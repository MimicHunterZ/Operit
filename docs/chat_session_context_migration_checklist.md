# `ChatSessionContext` 迁移实施清单

本文是 `docs/chat_session_host_refactor_plan.md` 的落地版清单。

已确认前提：

- 当前版本**未发布**。
- 直接把 `ChatServiceCore` 重命名并迁移为 `ChatSessionContext`。
- 不保留兼容类、不保留双实现、不保留回退分支。

---

## 一、目标结构

最终建议结构：

- `app/src/main/java/com/ai/assistance/operit/services/session/CallState.kt`
- `app/src/main/java/com/ai/assistance/operit/services/session/ChatSelectionMode.kt`
- `app/src/main/java/com/ai/assistance/operit/services/session/SessionUiBridge.kt`
- `app/src/main/java/com/ai/assistance/operit/services/session/ChatSessionContext.kt`
- `app/src/main/java/com/ai/assistance/operit/services/session/ChatSessionHost.kt`

保留但瘦身：

- `app/src/main/java/com/ai/assistance/operit/ui/features/chat/viewmodel/ChatViewModel.kt`
- `app/src/main/java/com/ai/assistance/operit/services/FloatingChatService.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/features/chat/viewmodel/FloatingWindowDelegate.kt`

最终删除或退出：

- `app/src/main/java/com/ai/assistance/operit/services/ChatServiceCore.kt`
- `FloatingWindowDelegate` 中基于 `chatHistoryFlow` 的消息推送同步逻辑

---

## 二、文件级改造清单

## 1. 直接迁移 `ChatServiceCore`

源文件：

- `app/src/main/java/com/ai/assistance/operit/services/ChatServiceCore.kt`

目标：

- 新路径：`app/src/main/java/com/ai/assistance/operit/services/session/ChatSessionContext.kt`
- 新类名：`ChatSessionContext`

迁移时要一起改的内容：

- package 从 `com.ai.assistance.operit.services` 改到 `com.ai.assistance.operit.services.session`
- 类名 `ChatServiceCore` 改成 `ChatSessionContext`
- 构造参数新增：
  - `callState: CallState`
  - `selectionMode: ChatSelectionMode`
  - `uiBridge: SessionUiBridge`
- 把现在构造里写死的空回调替换成 `uiBridge`

建议构造签名：

```kotlin
class ChatSessionContext(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    val callState: CallState,
    private val selectionMode: ChatSelectionMode,
    private val uiBridge: SessionUiBridge,
)
```

## 2. 新增 `CallState`

新文件：

- `app/src/main/java/com/ai/assistance/operit/services/session/CallState.kt`

建议内容：

```kotlin
enum class CallState {
    MAIN,
    FLOATING
}
```

用途：

- 标识状态域
- 标识宿主取哪个 session
- 不能替代 `chatId`
- 不能复用 `PromptFunctionType`

## 3. 新增 `ChatSelectionMode`

新文件：

- `app/src/main/java/com/ai/assistance/operit/services/session/ChatSelectionMode.kt`

建议内容：

```kotlin
enum class ChatSelectionMode {
    FOLLOW_GLOBAL,
    LOCAL_ONLY
}
```

用途：

- `MAIN` 跟随 `ChatHistoryManager.currentChatIdFlow`
- `FLOATING` 保持本地会话选择，不自动跟全局同步

## 4. 新增 `SessionUiBridge`

新文件：

- `app/src/main/java/com/ai/assistance/operit/services/session/SessionUiBridge.kt`

建议接口：

```kotlin
interface SessionUiBridge {
    fun updateWebServerForCurrentChat(chatId: String?)
    fun resetAttachmentPanelState()
    fun clearReplyToMessage()
    fun getReplyToMessage(): ChatMessage?
}
```

建议再补一个空实现：

```kotlin
object EmptySessionUiBridge : SessionUiBridge {
    override fun updateWebServerForCurrentChat(chatId: String?) = Unit
    override fun resetAttachmentPanelState() = Unit
    override fun clearReplyToMessage() = Unit
    override fun getReplyToMessage(): ChatMessage? = null
}
```

注意：

- 这里的空实现只是接口默认实现，不是回退方案。
- 未发布前提下，最终应该让 `MAIN` / `FLOATING` 都接上各自桥接，不要长期依赖空实现。

## 5. 新增 `ChatSessionHost`

新文件：

- `app/src/main/java/com/ai/assistance/operit/services/session/ChatSessionHost.kt`

职责：

- 统一构建 `ChatSessionContext`
- 维护 `Map<CallState, ChatSessionContext>`
- 管理跨 session 聚合状态
- 提供 `session(callState)` 查询入口

建议 API：

```kotlin
class ChatSessionHost private constructor(
    private val appContext: Context
) {
    fun getOrCreateSession(
        callState: CallState,
        coroutineScope: CoroutineScope,
        selectionMode: ChatSelectionMode,
        uiBridge: SessionUiBridge,
    ): ChatSessionContext

    fun getSession(callState: CallState): ChatSessionContext?

    fun removeSession(callState: CallState)
}
```

建议先做 application 单例：

```kotlin
companion object {
    fun getInstance(context: Context): ChatSessionHost
}
```

## 6. 修改 `ChatHistoryDelegate`

源文件：

- `app/src/main/java/com/ai/assistance/operit/services/core/ChatHistoryDelegate.kt`

需要新增：

- 构造参数 `selectionMode: ChatSelectionMode`

需要调整：

- 当前订阅 `currentChatIdFlow` 的逻辑只在 `FOLLOW_GLOBAL` 下启用
- `LOCAL_ONLY` 下不自动跟随全局 currentChatId
- `switchChat(syncToGlobal = false)` 继续保留，但它要成为 `LOCAL_ONLY` 的标准行为，而不是临时分支行为

建议新增方法：

```kotlin
fun syncCurrentChatIdToGlobal()
```

要求：

- 该方法只做显式同步
- 不要让 `LOCAL_ONLY` 平时偷偷跟随全局

## 7. 修改 `MessageProcessingDelegate`

源文件：

- `app/src/main/java/com/ai/assistance/operit/services/core/MessageProcessingDelegate.kt`

要改的重点：

- 移除 companion object 内的：
  - `sharedIsLoading`
  - `sharedActiveStreamingChatIds`
  - `loadingByInstance`
  - `activeChatIdsByInstance`
- 保留实例内：
  - `chatRuntimes`
  - `inputProcessingStateByChatId`
  - `turnCompleteCounterByChatId`

调整后：

- `isLoading` 只反映当前 `ChatSessionContext` 内部状态
- `activeStreamingChatIds` 只反映当前 `ChatSessionContext` 内部状态
- 需要跨 session 聚合时，由 `ChatSessionHost` 计算

## 8. 修改 `MessageCoordinationDelegate`

源文件：

- `app/src/main/java/com/ai/assistance/operit/services/core/MessageCoordinationDelegate.kt`

重点不是重写逻辑，而是改注入来源：

- `updateWebServerForCurrentChat`
- `resetAttachmentPanelState`
- `clearReplyToMessage`
- `getReplyToMessage`

这些不再由 `ChatViewModel` / `ChatServiceCore` 直接塞 lambda，统一改成来自 `SessionUiBridge`。

同时需要注意：

- 不要把 `chatIdOverride` 升级成 `CallState`
- `chatIdOverride` 仍然保持当前语义

## 9. 修改 `ChatViewModel`

源文件：

- `app/src/main/java/com/ai/assistance/operit/ui/features/chat/viewmodel/ChatViewModel.kt`

目标：

- 不再自己 new 一整套 delegate
- 改成从 `ChatSessionHost` 取 `CallState.MAIN`

建议步骤：

1. 新增 `host = ChatSessionHost.getInstance(context)`
2. 构造 `MainSessionUiBridge`
3. 调用 `host.getOrCreateSession(...)`
4. 把现有对 delegate 的直接引用替换成 `mainSession.xxx`

建议新字段：

```kotlin
private val sessionHost = ChatSessionHost.getInstance(context)
private lateinit var mainSession: ChatSessionContext
```

需要从 `ChatViewModel` 移除的职责：

- delegate 的初始化拼装
- 与 session 无关的重复转发壳

需要保留的职责：

- 主界面专属 UI 状态
- 权限请求
- 主界面导航
- 主界面 workspace / webview / picker 等强 UI 行为

## 10. 修改 `FloatingChatService`

源文件：

- `app/src/main/java/com/ai/assistance/operit/services/FloatingChatService.kt`

目标：

- 不再 new `ChatServiceCore`
- 改为从 `ChatSessionHost` 取 `CallState.FLOATING`

建议步骤：

1. 新增 `host = ChatSessionHost.getInstance(this)`
2. 构造 `FloatingSessionUiBridge`
3. 获取 `floatingSession`
4. 把 `chatCore` 字段整体替换为 `floatingSession`

建议最终字段：

```kotlin
private lateinit var floatingSession: ChatSessionContext
```

同时：

- `getChatCore()` 改名为 `getSessionContext()`
- 所有 UI 层调用同步改名
- 不要留下 `getChatCore()` 兼容方法

## 11. 修改悬浮窗 UI 调用点

重点文件：

- `app/src/main/java/com/ai/assistance/operit/ui/floating/ui/window/screen/FloatingChatWindowScreen.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/floating/ui/fullscreen/screen/FloatingFullscreenScreen.kt`
- 其他通过 `floatContext.chatService?.getChatCore()` 取会话的文件

统一替换为：

- `floatContext.chatService?.getSessionContext()`

需要同步替换的方法名：

- `switchChatLocal(...)`
- `syncCurrentChatIdToGlobal()`
- `createNewChat(...)`
- `isLoading`
- `chatHistory`

## 12. 修改 `FloatingWindowDelegate`

源文件：

- `app/src/main/java/com/ai/assistance/operit/ui/features/chat/viewmodel/FloatingWindowDelegate.kt`

需要删除的逻辑：

- `chatHistoryFlow` -> `floatingService.updateChatMessages(...)`
- `notifyFloatingServiceReload()` 这种主界面推动悬浮窗重载的机制
- binder `setChatSyncCallback` / `setReloadCallback` 如果后续只用于消息同步，也应清理掉

保留的逻辑：

- 启动服务
- 绑定服务
- 关闭服务
- 模式切换

这样 `FloatingWindowDelegate` 会退化为真正的“窗口生命周期协调器”，而不是消息同步器。

---

## 三、建议的落地顺序

### Patch 1：定骨架

- 新增 `CallState`
- 新增 `ChatSelectionMode`
- 新增 `SessionUiBridge`
- 新增 `ChatSessionHost`
- 迁移 `ChatServiceCore` -> `ChatSessionContext`

目标：

- 新骨架可编译
- 引用尚未完全替换也没关系

### Patch 2：接主聊天

- `ChatViewModel` 改接 `MAIN` session
- 主界面发送、取消、消息列表、token 统计改读 session
- 清理 `ChatViewModel` 里的 delegate 初始化代码

目标：

- 主界面完全不再自己装配 delegate

### Patch 3：接悬浮窗

- `FloatingChatService` 改接 `FLOATING` session
- 所有 `getChatCore()` 改成 `getSessionContext()`
- 悬浮窗 UI 改读 `FLOATING` session

目标：

- 悬浮窗完全不再持有独立装配逻辑

### Patch 4：拆同步与共享态

- `ChatHistoryDelegate` 接入 `ChatSelectionMode`
- `MessageProcessingDelegate` 去掉 companion 共享态
- `FloatingWindowDelegate` 删消息同步逻辑

目标：

- `MAIN` / `FLOATING` 真正互不干扰

### Patch 5：清理尾巴

- 删除旧 `ChatServiceCore` 文件与引用
- 删除兼容 import
- 删除不再使用的 binder 消息同步回调
- 删除 `updateChatMessages(...)` 之类的历史桥接逻辑

目标：

- 代码树里不再出现旧方案残留

---

## 四、验收标准

完成后需要满足：

- 主界面和悬浮窗各自拥有独立 `CallState`
- `MAIN` 跟随全局 chat，`FLOATING` 默认本地 chat
- 悬浮窗切换会话不会影响主界面，除非显式同步
- 主界面与悬浮窗不再互相推送消息列表
- `MessageProcessingDelegate` 不再通过 companion 聚合多实例 loading
- 工程内不再存在 `ChatServiceCore` 引用

---

## 五、建议实现时的命名规则

建议统一命名：

- `mainSession`
- `floatingSession`
- `sessionHost`
- `SessionUiBridge`
- `MainSessionUiBridge`
- `FloatingSessionUiBridge`

不建议继续使用：

- `chatCore`
- `core`
- `serviceCore`

这些旧名字会把“会话上下文”和“服务宿主”混在一起。
