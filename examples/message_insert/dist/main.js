"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.registerToolPkg = registerToolPkg;
exports.onPromptInput = onPromptInput;
exports.onInputMenuToggle = onInputMenuToggle;
const index_ui_js_1 = __importDefault(require("./ui/index.ui.js"));
const shared_1 = require("./shared");
function registerToolPkg() {
    ToolPkg.registerToolboxUiModule({
        id: "message_insert_settings",
        runtime: "compose_dsl",
        screen: index_ui_js_1.default,
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
async function onPromptInput(input) {
    const stage = String(input.eventPayload.stage ?? input.eventName ?? "");
    if (stage !== "before_process") {
        return null;
    }
    const processedInput = String(input.eventPayload.processedInput ?? input.eventPayload.rawInput ?? "");
    if (!processedInput.trim()) {
        return null;
    }
    const chatId = String(input.eventPayload.chatId ?? getChatId() ?? "").trim();
    const tags = await (0, shared_1.buildExtraInfoAttachmentTags)(processedInput, chatId || undefined);
    if (!tags.length) {
        return null;
    }
    return `${processedInput.replace(/\s+$/, "")} ${tags.join(" ")}`;
}
function onInputMenuToggle(input) {
    const action = String(input.eventPayload.action ?? "").toLowerCase();
    if (action === "toggle") {
        (0, shared_1.setExtraInfoInjectionEnabled)(!(0, shared_1.getExtraInfoInjectionEnabled)());
        return [];
    }
    if (action !== "create") {
        return [];
    }
    const text = (0, shared_1.resolveExtraInfoI18n)();
    return [
        {
            id: "message_extra_info_injection",
            title: text.menuTitle,
            description: text.menuDescription,
            isChecked: (0, shared_1.getExtraInfoInjectionEnabled)(),
        },
    ];
}
