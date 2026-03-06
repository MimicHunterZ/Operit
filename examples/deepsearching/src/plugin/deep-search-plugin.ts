
import { PlanModeManager } from "../planning/plan-mode-manager";
import { renderPlanXml } from "./plan-xml-render-plugin";
import { resolveDeepSearchI18n } from "../i18n";

const ApiPreferences = Java.com.ai.assistance.operit.data.preferences.ApiPreferences;
const EnhancedAIService = Java.com.ai.assistance.operit.api.chat.EnhancedAIService;
const MessageProcessingController = Java.com.ai.assistance.operit.core.chat.plugins.MessageProcessingController;
const ToolPkgMessageProcessingCancellationRegistry =
  Java.com.ai.assistance.operit.plugins.toolpkg.ToolPkgMessageProcessingCancellationRegistry;

const FEATURE_KEY = "ai_planning";

function getAppContext() {
  if (typeof Java.getApplicationContext !== "function") {
    return null;
  }
  return Java.getApplicationContext();
}

function isDeepSearchEnabled(context: unknown): boolean {
  return Boolean(ApiPreferences.getFeatureToggleBlocking(context, FEATURE_KEY, false));
}

function setDeepSearchEnabled(context: unknown, enabled: boolean) {
  ApiPreferences.setFeatureToggleBlocking(context, FEATURE_KEY, !!enabled);
}

function normalizePayload(input: unknown): Record<string, unknown> {
  const record = input as { eventPayload?: Record<string, unknown> } | null;
  if (record && record.eventPayload && typeof record.eventPayload === "object") {
    return record.eventPayload as Record<string, unknown>;
  }
  return (record as Record<string, unknown>) || {};
}

function getI18n() {
  const locale = getLang();
  return resolveDeepSearchI18n(locale);
}

export function registerToolPkg(): boolean {
  console.log("deepsearching registerToolPkg start");

  console.log("deepsearching skip: registerToolboxUiModule");

  ToolPkg.registerAppLifecycleHook({
    id: "deepsearching_app_create",
    event: "application_on_create",
    function: onApplicationCreate,
  });
  console.log("deepsearching registered: registerAppLifecycleHook");

  ToolPkg.registerMessageProcessingPlugin({
    id: "deepsearching_message_plugin",
    function: onMessageProcessing,
  });
  console.log("deepsearching registered: registerMessageProcessingPlugin");

  ToolPkg.registerXmlRenderPlugin({
    id: "deepsearching_xml_plan",
    tag: "plan",
    function: onXmlRender,
  });
  console.log("deepsearching registered: registerXmlRenderPlugin");

  ToolPkg.registerInputMenuTogglePlugin({
    id: "deepsearching_input_menu_toggle",
    function: onInputMenuToggle,
  });
  console.log("deepsearching registered: registerInputMenuTogglePlugin");

  console.log("deepsearching registerToolPkg done");
  return true;
}

export function onApplicationCreate(input: ToolPkg.AppLifecycleHookEvent | unknown): void {
  console.log("deepsearching onApplicationCreate", JSON.stringify(input ?? null));
}

export async function onMessageProcessing(
  input: ToolPkg.MessageProcessingHookEvent
): Promise<ToolPkg.MessageProcessingHookReturnValue> {
  const payload = normalizePayload(input);
  const probeOnly = Boolean(payload.probeOnly ?? false);
  const executionId = String((payload.executionId as string | undefined) ?? "").trim();
  const message = String((payload.messageContent as string | undefined) ?? "").trim();
  if (!message) {
    return { matched: false };
  }

  let context: unknown = null;
  let enhancedAIService: unknown = null;
  let manager: PlanModeManager | null = null;
  let cancellationHandle: unknown = null;
  try {
    context = getAppContext();
    if (!context) return { matched: false };

    const enabled = isDeepSearchEnabled(context);
    if (!enabled) {
      return { matched: false };
    }

    enhancedAIService = EnhancedAIService.getInstance(context);
    manager = new PlanModeManager(context, enhancedAIService);
    const shouldUse = manager.shouldUseDeepSearchMode(message);
    if (probeOnly) {
      return { matched: shouldUse };
    }
    if (!shouldUse) {
      return { matched: false };
    }
    if (!executionId) {
      throw new Error("deepsearching missing executionId");
    }

    cancellationHandle = Java.proxy(MessageProcessingController, {
      cancel() {
        manager?.cancel();
      },
    });
    ToolPkgMessageProcessingCancellationRegistry.register(executionId, cancellationHandle as any);

    const history = (payload.chatHistory as Array<[string, string]>) || [];
    const workspacePath = (payload.workspacePath as string | undefined) ?? null;
    const maxTokens = Number(payload.maxTokens ?? 0);
    const tokenUsageThreshold = Number(payload.tokenUsageThreshold ?? 0);

    if (!maxTokens || !tokenUsageThreshold) {
      console.log("deepsearching missing maxTokens/tokenUsageThreshold");
      return { matched: false };
    }

    const emitIntermediateChunk = (chunk: string) => {
      if (!chunk) return;
      if (typeof sendIntermediateResult === "function") {
        sendIntermediateResult({ chunk });
      }
    };

    const text = await manager.executeDeepSearchMode(
      message,
      history,
      workspacePath,
      maxTokens,
      tokenUsageThreshold,
      emitIntermediateChunk
    );

    if (!text) {
      return { matched: false };
    }
    return { matched: true, text };
  } catch (error) {
    console.log("deepsearching onMessageProcessing error", String(error));
    return { matched: false };
  } finally {
    try {
      if (executionId) {
        ToolPkgMessageProcessingCancellationRegistry.unregister(executionId);
      }
    } catch (_e) { }
    try {
      if (cancellationHandle) Java.releaseJs(cancellationHandle as any);
    } catch (_e) { }
    try {
      if (context) Java.release(context as any);
    } catch (_e) { }
    try {
      if (enhancedAIService) Java.release(enhancedAIService as any);
    } catch (_e) { }
  }
}


export function onXmlRender(
  event: ToolPkg.XmlRenderHookEvent
): ToolPkg.XmlRenderHookReturn {
  const payload = normalizePayload(event);
  const xmlContent = String((payload.xmlContent as string | undefined) ?? "");
  const tagName = String((payload.tagName as string | undefined) ?? "");
  console.log(
    "deepsearching onXmlRender input",
    JSON.stringify({
      tagName,
      xmlLength: xmlContent.length,
      preview: xmlContent.slice(0, 120)
    })
  );
  if (!xmlContent) {
    console.log("deepsearching onXmlRender skip: empty xmlContent");
    return { handled: false };
  }
  const result = renderPlanXml(xmlContent, tagName);
  console.log(
    "deepsearching onXmlRender result",
    JSON.stringify({
      tagName,
      handled: Boolean(result?.handled),
      hasComposeDsl: Boolean(result?.composeDsl),
      composeDslStateKeys: result?.composeDsl?.state ? Object.keys(result.composeDsl.state) : []
    })
  );
  return result;
}

export function onInputMenuToggle(input: ToolPkg.InputMenuToggleHookEvent | unknown): ToolPkg.InputMenuToggleDefinitionResult[] {
  const payload = normalizePayload(input);
  const action = String((payload.action as string | undefined) ?? "").toLowerCase();

  let context: unknown = null;
  try {
    context = getAppContext();
    if (!context) return [];

    if (action === "toggle") {
      const current = isDeepSearchEnabled(context);
      setDeepSearchEnabled(context, !current);
      return [];
    }

    if (action !== "create") {
      return [];
    }

    const enabled = isDeepSearchEnabled(context);
    const i18n = getI18n();
    return [
      {
        id: FEATURE_KEY,
        title: i18n.menuTitle,
        description: i18n.menuDescription,
        isChecked: enabled,
      },
    ];
  } catch (error) {
    console.log("deepsearching onInputMenuToggle error", String(error));
    return [];
  } finally {
    try {
      if (context) Java.release(context as any);
    } catch (_e) { }
  }
}
