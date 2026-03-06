# 主聊天 / 悬浮窗会话宿主重构方案

## 已确认决策

本方案已锁定以下前提，不再保留分支讨论：

- 当前版本**未发布**。
- 不做向前兼容，不保留旧结构与新结构长期并存。
- `app/src/main/java/com/ai/assistance/operit/services/ChatServiceCore.kt` 不再作为长期保留层。
- 采用**直接重命名并迁移为 `ChatSessionContext`** 的方案。

因此，本文后续所有改造步骤都以“**直接迁移**”为前提，而不是“适配器过渡 + 长期共存”。

## 目标

当前项目里，普通聊天和悬浮窗聊天已经部分共用底层发送链路，但状态持有、生命周期、UI 同步方式仍然分散在 `ChatViewModel`、`FloatingChatService`、`FloatingWindowDelegate` 中。

本方案的目标是：

- 让聊天逻辑从 `ViewModel` / `Service` 中进一步脱离。
- 保持“主聊天”和“悬浮窗聊天”仍然是两种独立的 `CallState`，互不串线。
- 允许后续需要时再统一到一个运行时宿主，但不把当前 `FloatingChatService` 直接变成万能宿主。
- 不引入兜底代码、回退代码；优先把状态边界理顺。
- 直接把 `ChatServiceCore` 重命名并迁移为 `ChatSessionContext`，不保留双实现。

## 先说结论

### 可行，但不要直接共用一个 `ChatServiceCore`

如果只是把主聊天和悬浮窗都塞进同一个 `ChatServiceCore` 实例里，然后继续靠 `chatIdOverride` 或一些分支判断区分两边，这个方案不稳。

更合适的方向是：

- 抽出一个 **会话宿主**（`ChatSessionHost`）。
- 宿主内部按 `CallState` 持有多份 **会话上下文**（`ChatSessionContext`）。
- `MAIN` 和 `FLOATING` 各自拥有自己的：
  - 当前会话选择状态
  - 输入框状态
  - 附件状态
  - 回复目标状态
  - 总结任务状态
  - token 统计状态
  - UI 侧桥接回调

统一的是“业务装配方式”，不是“把两边状态塞到同一个对象里”。

---

## 现状梳理

### 1. 底层发送链路其实已经接近统一

主聊天发送链路：

- `app/src/main/java/com/ai/assistance/operit/ui/features/chat/screens/AIChatScreen.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/features/chat/viewmodel/ChatViewModel.kt`
- `app/src/main/java/com/ai/assistance/operit/services/core/MessageCoordinationDelegate.kt`
- `app/src/main/java/com/ai/assistance/operit/services/core/MessageProcessingDelegate.kt`

悬浮窗发送链路：

- `app/src/main/java/com/ai/assistance/operit/services/floating/FloatingWindowManager.kt`
- `app/src/main/java/com/ai/assistance/operit/services/FloatingChatService.kt`
- `app/src/main/java/com/ai/assistance/operit/services/ChatServiceCore.kt`
- `app/src/main/java/com/ai/assistance/operit/services/core/MessageCoordinationDelegate.kt`
- `app/src/main/java/com/ai/assistance/operit/services/core/MessageProcessingDelegate.kt`

结论：**真正的发送逻辑已经集中在 delegate 层**，现在更大的问题不是“如何统一发送算法”，而是“如何统一状态宿主”。

### 2. 当前主要耦合点

#### 2.1 `ChatViewModel` 和 `ChatServiceCore` 仍然是两套装配入口

- `ChatViewModel` 自己初始化 `ChatHistoryDelegate`、`MessageProcessingDelegate`、`MessageCoordinationDelegate`、`FloatingWindowDelegate`。
- `ChatServiceCore` 又初始化了一套自己的 delegate。

这导致：

- 同一套逻辑有两套装配代码。
- 主聊天和悬浮窗虽然“底层像”，但状态 owner 不一致。
- UI 侧专属逻辑（附件面板、reply、web server 更新等）在两边注入方式不同。

#### 2.2 `ChatHistoryDelegate` 的 `currentChatId` 仍然被全局流牵引

关键文件：

- `app/src/main/java/com/ai/assistance/operit/services/core/ChatHistoryDelegate.kt`
- `app/src/main/java/com/ai/assistance/operit/data/repository/ChatHistoryManager.kt`

当前 `ChatHistoryDelegate` 会持续订阅 `ChatHistoryManager.currentChatIdFlow`。

这意味着：

- 即使悬浮窗调用了 `switchChat(chatId, syncToGlobal = false)`，它也只是**本地暂时改了 `_currentChatId`**。
- 只要全局 `currentChatIdFlow` 后面再变化，悬浮窗这边仍然可能被覆盖。

所以现在的“悬浮窗本地 chatId 不写回全局”并不是完全隔离，而是**半隔离**。

#### 2.3 `FloatingWindowDelegate` 仍在做“主界面 -> 悬浮窗”的消息推送同步

关键文件：

- `app/src/main/java/com/ai/assistance/operit/ui/features/chat/viewmodel/FloatingWindowDelegate.kt`
- `app/src/main/java/com/ai/assistance/operit/services/FloatingChatService.kt`

当前流程里：

- 主界面的 `chatHistoryFlow` 会被 `FloatingWindowDelegate` 收集。
- 收到消息后再调用 `FloatingChatService.updateChatMessages(...)`。
- 与此同时，`FloatingChatService` 自己又在收集 `chatCore.chatHistory`。

这说明悬浮窗 UI 现在同时存在两种消息来源：

- 悬浮窗内部 `chatCore`
- 主界面外部推送

这就是典型的“双写源”，后续如果继续合并而不先拆清楚，会越来越难维护。

#### 2.4 `MessageProcessingDelegate` 有 companion 级共享态

关键文件：

- `app/src/main/java/com/ai/assistance/operit/services/core/MessageProcessingDelegate.kt`

当前有下面这些 companion 级共享状态：

- `sharedIsLoading`
- `sharedActiveStreamingChatIds`
- `loadingByInstance`
- `activeChatIdsByInstance`

这会造成两个问题：

- 不同 owner 的 `MessageProcessingDelegate` 会互相聚合 loading 状态。
- 它更像“全局统计器”，而不是“单个会话上下文”的内部状态。

如果后续引入 `CallState`，这些共享态必须上移到宿主层，不能继续埋在 delegate 的 companion object 里。

#### 2.5 `chatIdOverride` 不能当 `CallState` 替代品

关键文件：

- `app/src/main/java/com/ai/assistance/operit/services/core/MessageCoordinationDelegate.kt`

现在代码里只要 `chatIdOverride` 不为空，就会被视为 background send 语义的一部分。这个判断会影响：

- `replyToMessage` 是否生效
- summary 是否启用
- 发送后是否清空附件/UI 状态

所以：

- `chatIdOverride` 的语义是“目标 chatId 覆盖 / 背景发送辅助参数”
- 它不是“主聊天 / 悬浮窗”这种状态域标识

不能把它偷换成 `CallState`。

#### 2.6 底层 AI 实例已经天然支持 chat 维度隔离

关键文件：

- `app/src/main/java/com/ai/assistance/operit/api/chat/EnhancedAIService.kt`

`EnhancedAIService.getChatInstance(context, chatId)` 已经按 `chatId` 管理实例。

这说明：

- 聊天生成链路的底层实例并不是本次重构障碍。
- 这次重构的核心是“上层状态宿主”和“会话选择策略”，不是 AI provider 层。

---

## 为什么不建议直接把 `FloatingChatService` 变成统一宿主

`FloatingChatService` 当前同时承担了：

- 悬浮窗窗口管理
- 前台通知
- wake lock
- 输入焦点切换
- 悬浮窗 UI 生命周期
- 聊天逻辑宿主

如果把主聊天也直接绑到它上面，会出现几个问题：

- 主界面聊天被迫依赖悬浮窗服务生命周期。
- 即使用户没开悬浮窗，也要承受 service/binder 复杂度。
- 窗口层职责和聊天状态层职责继续混在一起。

因此更推荐两步走：

1. **先抽出纯聊天会话宿主**。
2. 如果后续确认必须统一到 Android `Service`，再让这个宿主搬进一个新的运行时 service。

---

## 推荐目标结构

## 一、核心概念

### 1. `CallState`

建议新增：

```kotlin
enum class CallState {
    MAIN,
    FLOATING
}
```

它表示“状态域”，而不是 chatId、不是消息类型、也不是 promptFunctionType。

### 2. `ChatSessionContext`

每个 `CallState` 持有一份自己的 `ChatSessionContext`。

建议包含：

- `ChatHistoryDelegate`
- `MessageProcessingDelegate`
- `MessageCoordinationDelegate`
- `AttachmentDelegate`
- `TokenStatisticsDelegate`
- `UiStateDelegate`
- `SessionUiBridge`
- `replyToMessage`
- `attachmentPanelState`
- `currentChatId` 选择策略

### 3. `SessionUiBridge`

把当前散落在 `ChatViewModel` / `ChatServiceCore` 里的 UI 相关回调抽成接口，例如：

```kotlin
interface SessionUiBridge {
    fun updateWebServerForCurrentChat(chatId: String?)
    fun resetAttachmentPanelState()
    fun clearReplyToMessage()
    fun getReplyToMessage(): ChatMessage?
}
```

不同 `CallState` 用不同实现：

- `MAIN`：接到主界面状态
- `FLOATING`：接到悬浮窗状态

### 4. `ChatSessionHost`

宿主职责：

- 维护 `Map<CallState, ChatSessionContext>`
- 提供 `session(callState)`
- 维护跨 session 的聚合统计（如果还需要）
- 统一初始化逻辑，避免 `ChatViewModel` / `ChatServiceCore` 重复装配

---

## 二、会话选择策略必须显式化

这是这次改造最关键的一点。

当前 chat 选择逻辑混在 `ChatHistoryDelegate` 里，而且部分依赖全局 `currentChatIdFlow`。重构后建议显式拆成：

### `ChatSelectionMode`

```kotlin
enum class ChatSelectionMode {
    FOLLOW_GLOBAL,
    LOCAL_ONLY
}
```

建议约定：

- `MAIN` 使用 `FOLLOW_GLOBAL`
- `FLOATING` 使用 `LOCAL_ONLY`

行为定义：

- `FOLLOW_GLOBAL`：持续订阅 `ChatHistoryManager.currentChatIdFlow`
- `LOCAL_ONLY`：不自动跟随全局，只维护本地 currentChatId
- 需要时提供 `syncCurrentChatIdToGlobal()` 进行显式同步

这一步做完之后，悬浮窗的“本地 chatId”才算真正独立。

---

## 三、状态归属建议

### `MAIN` 独占

- 主页面输入框内容
- 主页面 reply 目标
- 主页面附件面板开关
- 主页面 web server / workspace UI 联动

### `FLOATING` 独占

- 悬浮窗输入框内容
- 悬浮窗 reply 目标
- 悬浮窗附件面板开关
- 悬浮窗当前 chatId（默认本地）
- 悬浮窗输入焦点/窗口模式相关 UI 状态

### 可按 chatId 复用

- `EnhancedAIService.getChatInstance(context, chatId)`
- token 统计缓存
- 聊天历史数据库

### 不要继续放在 delegate companion 里

- 聚合的 `isLoading`
- 聚合的 `activeStreamingChatIds`

这些应该移动到 `ChatSessionHost`，由 host 决定是：

- 只看某个 `CallState`
- 还是做全局聚合展示

---

## 四、建议的代码结构

建议新增目录：

- `app/src/main/java/com/ai/assistance/operit/services/session/`

建议新增文件：

- `ChatSessionHost.kt`
- `ChatSessionContext.kt`
- `CallState.kt`
- `ChatSelectionMode.kt`
- `SessionUiBridge.kt`
- `SessionMetricsAggregator.kt`

建议保留但瘦身的现有文件：

- `app/src/main/java/com/ai/assistance/operit/ui/features/chat/viewmodel/ChatViewModel.kt`
- `app/src/main/java/com/ai/assistance/operit/services/FloatingChatService.kt`

建议直接重命名迁移的历史文件：

- `app/src/main/java/com/ai/assistance/operit/services/ChatServiceCore.kt`

它当前已经非常接近“session context”的职责边界，因此本次改造不再走 adapter 过渡，而是直接：

- 将文件迁移到新的 session 目录
- 将类名改为 `ChatSessionContext`
- 让 `ChatViewModel` 与 `FloatingChatService` 统一通过 host 获取该上下文
- 在迁移完成后删除旧引用，不保留 `ChatServiceCore` 名称

---

## 五、推荐改造顺序

### Phase 0：决策冻结

当前已确认：

- 版本**未发布**。
- 本次改造**不保留兼容层**。
- `ChatServiceCore` 直接迁移为 `ChatSessionContext`。

因此后续步骤默认允许：

- 直接改类名
- 直接改目录
- 直接改引用
- 直接删除旧结构中不再需要的中间层

### Phase 1：抽宿主，不改业务行为

目标：先把装配统一起来。

步骤：

1. 新增 `CallState`、`ChatSelectionMode`、`SessionUiBridge`。
2. 新增 `ChatSessionHost`。
3. 将 `ChatServiceCore` 重命名并迁移为 `ChatSessionContext`。
4. 把原先分散在 `ChatViewModel` / `ChatServiceCore` 的 delegate 装配逻辑统一收口到 host。
5. `ChatViewModel` 改为拿 `host.session(MAIN)`。
6. `FloatingChatService` 改为拿 `host.session(FLOATING)`。
7. 删除旧的 `ChatServiceCore` 引用，不保留同名兼容壳。

这一阶段的重点不是最小改动，而是**把唯一正确的依赖注入入口立起来**。

### Phase 2：拆开 chat 选择策略

目标：让 `MAIN` / `FLOATING` 真正互不干扰。

步骤：

1. 让 `ChatHistoryDelegate` 支持选择模式配置。
2. `MAIN` 订阅全局 `currentChatIdFlow`。
3. `FLOATING` 不订阅全局，只保留本地选择。
4. 保留显式 `syncCurrentChatIdToGlobal()`。

### Phase 3：去掉双写消息同步

目标：悬浮窗消息只来自自己的 session，不再接受主界面“推送覆盖”。

步骤：

1. 删除 `FloatingWindowDelegate` 中基于 `chatHistoryFlow` 的 `updateChatMessages(...)` 推送。
2. 悬浮窗 UI 直接观察 `session(FLOATING)` 的 `chatHistory`。
3. 主界面 UI 直接观察 `session(MAIN)` 的 `chatHistory`。

这样消息来源会变成单一数据源。

### Phase 4：上移共享聚合状态

目标：移除 `MessageProcessingDelegate` 的 companion 共享态。

步骤：

1. 把 `sharedIsLoading` / `sharedActiveStreamingChatIds` 上移到 host。
2. `MessageProcessingDelegate` 只维护实例内状态。
3. 需要聚合时由 host 根据所有 `ChatSessionContext` 计算。

### Phase 5：完成清理收口

当 `ChatSessionContext` 已接管全部职责后：

- 删除旧的 `ChatServiceCore` 文件路径与类名
- 删除所有对 `ChatServiceCore` 的 import / 引用
- 保持最终结构只剩：
  - `ChatViewModel`
  - `FloatingChatService`
  - `ChatSessionHost`
  - `ChatSessionContext`

不要留下：

- 同名转发类
- adapter 壳
- 兼容 alias
- 双套初始化代码

---

## 六、如果以后一定要“统一到一个 Android Service”

推荐方式不是继续扩展 `FloatingChatService`，而是：

### 新建 `ChatRuntimeService`

职责只做：

- 持有 `ChatSessionHost`
- 暴露 `MAIN` / `FLOATING` session binder 接口
- 不负责悬浮窗窗口绘制
- 不负责悬浮窗 UI 生命周期

然后：

- `ChatViewModel` 绑定 `ChatRuntimeService`，拿 `MAIN` session
- `FloatingChatService` 绑定 `ChatRuntimeService`，拿 `FLOATING` session
- `FloatingChatService` 继续只负责悬浮窗 UI / window manager / wake lock

这样才是“一个运行时 service + 两个状态域”的干净结构。

如果把窗口管理和聊天运行时彻底揉成一个 service，后面一定还会再拆一次。

---

## 七、明确不建议的做法

- 不要把 `chatIdOverride` 当成 `CallState`
- 不要让 `MAIN` 和 `FLOATING` 共用同一个 `ChatHistoryDelegate`
- 不要继续保留“主界面推送消息到悬浮窗 + 悬浮窗自己也收消息”的双数据源
- 不要再往 `FloatingChatService` 里堆业务逻辑
- 不要为了过渡而加长期回退分支

---

## 八、最小落地版本

如果希望先做一版最小改造，建议目标定为：

1. 新增 `ChatSessionHost`
2. 新增 `CallState.MAIN` / `CallState.FLOATING`
3. `ChatViewModel` 改为读取 `MAIN` session
4. `FloatingChatService` 改为读取 `FLOATING` session
5. `ChatHistoryDelegate` 新增 `ChatSelectionMode`
6. 删除 `FloatingWindowDelegate` 里的消息双向推送逻辑

做到这一步后：

- 逻辑已经基本脱离 `ViewModel` 和 `Service`
- 两边仍是两种 `CallState`
- 互不干扰会比现在明显更可靠
- 后续要不要再收敛到 Android Service，就变成可选项，而不是结构前提

---

## 九、实施验收点

改造完成后，至少应满足：

- 主界面切 chat，不会强制把悬浮窗切过去
- 悬浮窗切 chat，不会写回主界面当前 chat，除非显式同步
- 主界面和悬浮窗可同时各自发送消息，loading 状态不串线
- 悬浮窗关闭后，不影响主界面 session 状态
- reply / attachment / summary 状态都按 `CallState` 隔离
- 不再依赖主界面向悬浮窗推送消息列表

---

## 十、建议的下一步

如果要继续推进实现，建议先做下面两件事：

1. 先出一版 **类图/文件级别拆分清单**。
2. 再按 Phase 1 ~ Phase 3 分三个小 patch 实施，而不是一次性大改。

这样更容易确认每一步都没有把主聊天和悬浮窗重新耦合回去。
