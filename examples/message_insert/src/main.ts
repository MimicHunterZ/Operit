import toolboxUI from "./ui/index.ui.js";
import {
  buildExtraInfoAttachmentTags,
  getExtraInfoInjectionEnabled,
  normalizeHookPayload,
  resolveExtraInfoI18n,
  setExtraInfoInjectionEnabled,
} from "./shared";

export function registerToolPkg(): boolean {
  ToolPkg.registerToolboxUiModule({
    id: "message_insert_settings",
    runtime: "compose_dsl",
    screen: toolboxUI,
    params: {},
    title: {
      zh: "额外信息注入",
      en: "Extra Info Injection",
    },
  });

  ToolPkg.registerPromptInputHook({
    id: "message_insert_prompt_input",
    function: onPromptInput,
  });

  ToolPkg.registerInputMenuTogglePlugin({
    id: "message_insert_input_menu_toggle",
    function: onInputMenuToggle,
  });

  return true;
}

export async function onPromptInput(
  input: ToolPkg.PromptInputHookEvent
) {
  const payload = normalizeHookPayload(input);
  const stage = String(payload.stage ?? input.eventName ?? "");
  if (stage !== "before_process") {
    return null;
  }

  const processedInput = String(payload.processedInput ?? payload.rawInput ?? "");
  if (!processedInput.trim()) {
    return null;
  }

  const tags = await buildExtraInfoAttachmentTags(processedInput);
  if (!tags.length) {
    return null;
  }

  return `${processedInput.replace(/\s+$/, "")} ${tags.join(" ")}`;
}

export function onInputMenuToggle(
  input: ToolPkg.InputMenuToggleHookEvent | unknown
): ToolPkg.InputMenuToggleDefinitionResult[] {
  const payload = normalizeHookPayload(input);
  const action = String(payload.action ?? "").toLowerCase();

  if (action === "toggle") {
    setExtraInfoInjectionEnabled(!getExtraInfoInjectionEnabled());
    return [];
  }

  if (action !== "create") {
    return [];
  }

  const text = resolveExtraInfoI18n();
  return [
    {
      id: "message_extra_info_injection",
      title: text.menuTitle,
      description: text.menuDescription,
      isChecked: getExtraInfoInjectionEnabled(),
    },
  ];
}
