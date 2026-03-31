/* METADATA
{
    "name": "browser",
    "display_name": {
        "zh": "Browser 自动化操作",
        "en": "Browser Automation"
    },
    "description": {
        "zh": "严格对齐 Playwright MCP 默认 browser 工具面的浏览器自动化工具集。",
        "en": "Browser automation tools aligned to the default Playwright MCP browser surface."
    },
    "enabledByDefault": true,
    "category": "Automatic",
    "tools": [
        {
            "name": "click",
            "description": { "zh": "点击页面元素。", "en": "Click an element on the page." },
            "parameters": [
                { "name": "ref", "description": { "zh": "快照中的目标元素引用。", "en": "Target element ref from the snapshot." }, "type": "string", "required": true },
                { "name": "element", "description": { "zh": "可选，人类可读元素描述。", "en": "Optional human-readable element description." }, "type": "string", "required": false },
                { "name": "doubleClick", "description": { "zh": "可选，是否双击。", "en": "Optional double click." }, "type": "boolean", "required": false },
                { "name": "button", "description": { "zh": "可选，left/right/middle。", "en": "Optional mouse button: left/right/middle." }, "type": "string", "required": false },
                { "name": "modifiers", "description": { "zh": "可选，修饰键数组。", "en": "Optional modifier keys array." }, "type": "array", "required": false }
            ]
        },
        {
            "name": "close",
            "description": { "zh": "关闭当前 tab。", "en": "Close the current tab." },
            "parameters": []
        },
        {
            "name": "console_messages",
            "description": { "zh": "读取控制台消息。", "en": "Read console messages." },
            "parameters": [
                { "name": "level", "description": { "zh": "可选，日志级别：error/warning/info/debug，默认 info。", "en": "Optional log level: error/warning/info/debug. Defaults to info." }, "type": "string", "required": false },
                { "name": "filename", "description": { "zh": "可选，保存输出的文件名。", "en": "Optional output file name." }, "type": "string", "required": false }
            ]
        },
        {
            "name": "drag",
            "description": { "zh": "在两个元素之间拖拽。", "en": "Drag between two elements." },
            "parameters": [
                { "name": "startElement", "description": { "zh": "源元素的人类可读描述。", "en": "Human-readable source element description." }, "type": "string", "required": true },
                { "name": "startRef", "description": { "zh": "源元素 ref。", "en": "Source element ref." }, "type": "string", "required": true },
                { "name": "endElement", "description": { "zh": "目标元素的人类可读描述。", "en": "Human-readable target element description." }, "type": "string", "required": true },
                { "name": "endRef", "description": { "zh": "目标元素 ref。", "en": "Target element ref." }, "type": "string", "required": true }
            ]
        },
        {
            "name": "evaluate",
            "description": { "zh": "在页面或元素上执行 JavaScript 函数。", "en": "Evaluate a JavaScript function on the page or an element." },
            "parameters": [
                { "name": "function", "description": { "zh": "要执行的函数源码。", "en": "Function source to execute." }, "type": "string", "required": true },
                { "name": "element", "description": { "zh": "可选，人类可读元素描述。", "en": "Optional human-readable element description." }, "type": "string", "required": false },
                { "name": "ref", "description": { "zh": "可选，目标元素 ref。", "en": "Optional target element ref." }, "type": "string", "required": false }
            ]
        },
        {
            "name": "upload",
            "description": { "zh": "向当前文件选择器上传文件。", "en": "Upload files to the current file chooser." },
            "parameters": [
                { "name": "paths", "description": { "zh": "可选，绝对路径数组；不传则取消 file chooser。", "en": "Optional absolute file paths; omit to cancel the file chooser." }, "type": "array", "required": false }
            ]
        },
        {
            "name": "fill_form",
            "description": { "zh": "批量填写表单字段。", "en": "Fill multiple form fields." },
            "parameters": [
                { "name": "fields", "description": { "zh": "字段数组。", "en": "Array of form fields." }, "type": "array", "required": true }
            ]
        },
        {
            "name": "handle_dialog",
            "description": { "zh": "处理当前对话框。", "en": "Handle the current dialog." },
            "parameters": [
                { "name": "accept", "description": { "zh": "是否接受对话框。", "en": "Whether to accept the dialog." }, "type": "boolean", "required": true },
                { "name": "promptText", "description": { "zh": "可选，prompt 的输入文本。", "en": "Optional prompt text." }, "type": "string", "required": false }
            ]
        },
        {
            "name": "hover",
            "description": { "zh": "悬停到页面元素上。", "en": "Hover over an element." },
            "parameters": [
                { "name": "ref", "description": { "zh": "目标元素 ref。", "en": "Target element ref." }, "type": "string", "required": true },
                { "name": "element", "description": { "zh": "可选，人类可读元素描述。", "en": "Optional human-readable element description." }, "type": "string", "required": false }
            ]
        },
        {
            "name": "goto",
            "description": { "zh": "导航到指定 URL。", "en": "Navigate to a URL." },
            "parameters": [
                { "name": "url", "description": { "zh": "目标 URL。", "en": "Target URL." }, "type": "string", "required": true }
            ]
        },
        {
            "name": "back",
            "description": { "zh": "后退到上一页。", "en": "Go back to the previous page." },
            "parameters": []
        },
        {
            "name": "network_requests",
            "description": { "zh": "读取当前页面的网络请求。", "en": "Read network requests for the current page." },
            "parameters": [
                { "name": "includeStatic", "description": { "zh": "可选，是否包含静态资源请求，默认 false。", "en": "Optional include static resource requests. Defaults to false." }, "type": "boolean", "required": false },
                { "name": "filename", "description": { "zh": "可选，保存输出的文件名。", "en": "Optional output file name." }, "type": "string", "required": false }
            ]
        },
        {
            "name": "press_key",
            "description": { "zh": "按下键盘按键。", "en": "Press a keyboard key." },
            "parameters": [
                { "name": "key", "description": { "zh": "按键名。", "en": "Key name." }, "type": "string", "required": true }
            ]
        },
        {
            "name": "resize",
            "description": { "zh": "调整浏览器视口大小。", "en": "Resize the browser viewport." },
            "parameters": [
                { "name": "width", "description": { "zh": "宽度。", "en": "Width." }, "type": "number", "required": true },
                { "name": "height", "description": { "zh": "高度。", "en": "Height." }, "type": "number", "required": true }
            ]
        },
        {
            "name": "run_code",
            "description": { "zh": "运行 Playwright 风格代码片段。", "en": "Run a Playwright-style code snippet." },
            "parameters": [
                { "name": "code", "description": { "zh": "代码片段。", "en": "Code snippet." }, "type": "string", "required": true }
            ]
        },
        {
            "name": "select_option",
            "description": { "zh": "在下拉框中选择选项。", "en": "Select options in a dropdown." },
            "parameters": [
                { "name": "ref", "description": { "zh": "目标元素 ref。", "en": "Target element ref." }, "type": "string", "required": true },
                { "name": "values", "description": { "zh": "要选择的值数组。", "en": "Values to select." }, "type": "array", "required": true },
                { "name": "element", "description": { "zh": "可选，人类可读元素描述。", "en": "Optional human-readable element description." }, "type": "string", "required": false }
            ]
        },
        {
            "name": "snapshot",
            "description": { "zh": "获取当前页面结构化快照。", "en": "Get a structured page snapshot." },
            "parameters": [
                { "name": "filename", "description": { "zh": "可选，保存快照的文件名。", "en": "Optional snapshot output file name." }, "type": "string", "required": false }
            ]
        },
        {
            "name": "screenshot",
            "description": { "zh": "截取页面截图。", "en": "Take a screenshot." },
            "parameters": [
                { "name": "type", "description": { "zh": "可选，图片类型：png/jpeg，默认 png。", "en": "Optional image type: png/jpeg. Defaults to png." }, "type": "string", "required": false },
                { "name": "filename", "description": { "zh": "可选，保存截图的文件名。", "en": "Optional screenshot output file name." }, "type": "string", "required": false },
                { "name": "element", "description": { "zh": "可选，元素描述。", "en": "Optional element description." }, "type": "string", "required": false },
                { "name": "ref", "description": { "zh": "可选，元素 ref。", "en": "Optional element ref." }, "type": "string", "required": false },
                { "name": "fullPage", "description": { "zh": "可选，是否截取整页。", "en": "Optional full-page screenshot." }, "type": "boolean", "required": false }
            ]
        },
        {
            "name": "type",
            "description": { "zh": "向可编辑元素输入文本。", "en": "Type text into an editable element." },
            "parameters": [
                { "name": "ref", "description": { "zh": "目标元素 ref。", "en": "Target element ref." }, "type": "string", "required": true },
                { "name": "text", "description": { "zh": "输入文本。", "en": "Text to type." }, "type": "string", "required": true },
                { "name": "element", "description": { "zh": "可选，人类可读元素描述。", "en": "Optional human-readable element description." }, "type": "string", "required": false },
                { "name": "submit", "description": { "zh": "可选，输入后是否提交。", "en": "Optional submit after typing." }, "type": "boolean", "required": false },
                { "name": "slowly", "description": { "zh": "可选，是否逐字输入。", "en": "Optional type slowly." }, "type": "boolean", "required": false }
            ]
        },
        {
            "name": "wait_for",
            "description": { "zh": "等待文本出现、消失或等待指定时间。", "en": "Wait for text to appear, disappear, or for a duration." },
            "parameters": [
                { "name": "time", "description": { "zh": "可选，等待秒数。", "en": "Optional number of seconds to wait." }, "type": "number", "required": false },
                { "name": "text", "description": { "zh": "可选，等待出现的文本。", "en": "Optional text to wait for." }, "type": "string", "required": false },
                { "name": "textGone", "description": { "zh": "可选，等待消失的文本。", "en": "Optional text to wait to disappear." }, "type": "string", "required": false }
            ]
        },
        {
            "name": "tabs",
            "description": { "zh": "列出、创建、切换或关闭 tab。", "en": "List, create, select, or close tabs." },
            "parameters": [
                { "name": "action", "description": { "zh": "操作：list/create/select/close。", "en": "Action: list/create/select/close." }, "type": "string", "required": true },
                { "name": "index", "description": { "zh": "可选，0-based tab 索引。", "en": "Optional 0-based tab index." }, "type": "number", "required": false }
            ]
        },
    ]
}*/

const MAX_INLINE_BROWSER_TEXT_CHARS = 24000;
type JsonObject = Record<string, unknown>;
type BrowserTabAction = "list" | "create" | "select" | "close";
type BrowserMouseButton = "left" | "right" | "middle";
type ToolParamValue = string | number | boolean | object;

interface FilenamePayload {
    filename?: string;
}

interface ClickPayload {
    ref: string;
    element?: string;
    button?: BrowserMouseButton;
    modifiers?: string[];
    doubleClick?: boolean;
}

interface ConsoleMessagesPayload extends FilenamePayload {
    level: string;
}

interface EvaluatePayload {
    function: string;
    ref?: string;
    element?: string;
}

interface UploadPayload {
    paths?: string[];
}

interface FillFormFieldPayload {
    name: string;
    type: string;
    value: ToolParamValue;
    ref?: string;
    selector?: string;
}

interface FillFormPayload {
    fields: FillFormFieldPayload[];
}

interface HandleDialogPayload {
    accept: boolean;
    promptText?: string;
}

interface HoverPayload {
    ref: string;
    element?: string;
}

interface NetworkRequestsPayload extends FilenamePayload {
    includeStatic?: boolean;
}

interface SelectOptionPayload {
    ref: string;
    values: string[];
    element?: string;
}

interface ScreenshotPayload extends FilenamePayload {
    type: string;
    element?: string;
    ref?: string;
    fullPage?: boolean;
}

interface TypePayload {
    ref: string;
    text: string;
    element?: string;
    submit?: boolean;
    slowly?: boolean;
}

interface WaitForPayload {
    time?: number;
    text?: string;
    textGone?: string;
}

interface TabsPayload {
    action: BrowserTabAction;
    index?: number;
}

const TOOL_NAMES = [
    "click",
    "close",
    "console_messages",
    "drag",
    "evaluate",
    "upload",
    "fill_form",
    "handle_dialog",
    "hover",
    "goto",
    "back",
    "network_requests",
    "press_key",
    "resize",
    "run_code",
    "select_option",
    "snapshot",
    "screenshot",
    "type",
    "wait_for",
    "tabs"
];

function assertObject(params: unknown, toolName: string): JsonObject {
    if (params === undefined || params === null) {
        return {};
    }
    if (typeof params !== "object" || Array.isArray(params)) {
        throw new Error(toolName + " expects one parameter object");
    }
    return params as JsonObject;
}

function requireString(value: unknown, name: string): string {
    const normalized = typeof value === "string" ? value.trim() : String(value || "").trim();
    if (!normalized) {
        throw new Error(name + " is required");
    }
    return normalized;
}

function optionalString(value: unknown): string | undefined {
    if (value === undefined || value === null) {
        return undefined;
    }
    const normalized = String(value).trim();
    return normalized ? normalized : undefined;
}

function optionalBoolean(value: unknown, name: string): boolean | undefined {
    if (value === undefined) {
        return undefined;
    }
    if (typeof value !== "boolean") {
        throw new Error(name + " must be a boolean");
    }
    return value;
}

function requireNumber(value: unknown, name: string): number {
    const normalized = Number(value);
    if (!Number.isFinite(normalized)) {
        throw new Error(name + " must be a number");
    }
    return normalized;
}

function optionalArray(value: unknown, name: string): unknown[] | undefined {
    if (value === undefined) {
        return undefined;
    }
    if (!Array.isArray(value)) {
        throw new Error(name + " must be an array");
    }
    return value;
}

function requireStringArray(value: unknown, name: string): string[] {
    const array = optionalArray(value, name);
    if (!array || array.length === 0) {
        throw new Error(name + " must be a non-empty array");
    }
    return array.map((item) => requireString(item, name + "[]"));
}

function buildLargeOutputFilename(prefix: string, extension: string): string {
    const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
    const rand = Math.floor(Math.random() * 1000000);
    return OPERIT_CLEAN_ON_EXIT_DIR + "/browser_" + prefix + "_" + timestamp + "_" + rand + "." + extension;
}

async function maybePersistLargeText<T extends FilenamePayload>(
    nativeName: string,
    params: T,
    result: unknown,
    prefix: string,
    extension: string
) {
    if (typeof result !== "string" || result.length <= MAX_INLINE_BROWSER_TEXT_CHARS || params.filename) {
        return result;
    }
    await Tools.Files.mkdir(OPERIT_CLEAN_ON_EXIT_DIR, true);
    const filename = buildLargeOutputFilename(prefix, extension);
    return toolCall(nativeName, toToolParams({ ...params, filename: filename }));
}

function toToolParams<T extends object>(params: T): ToolParams {
    return params as unknown as ToolParams;
}

async function callBrowser(nativeName: string, params: object = {}) {
    return toolCall(nativeName, toToolParams(params));
}

async function click(params: unknown) {
    const normalized = assertObject(params, "click");
    const payload: ClickPayload = {
        ref: requireString(normalized.ref, "ref")
    };
    const element = optionalString(normalized.element);
    const button = optionalString(normalized.button);
    const modifiers = optionalArray(normalized.modifiers, "modifiers");
    const doubleClick = optionalBoolean(normalized.doubleClick, "doubleClick");
    if (element) {
        payload.element = element;
    }
    if (button) {
        if (!["left", "right", "middle"].includes(button)) {
            throw new Error("button must be left, right, or middle");
        }
        payload.button = button as BrowserMouseButton;
    }
    if (modifiers) {
        payload.modifiers = modifiers.map((item) => requireString(item, "modifiers[]"));
    }
    if (doubleClick !== undefined) {
        payload.doubleClick = doubleClick;
    }
    return callBrowser("browser_click", payload);
}

async function close() {
    return callBrowser("browser_close");
}

async function console_messages(params: unknown) {
    const normalized = assertObject(params, "console_messages");
    const payload: ConsoleMessagesPayload = {
        level: optionalString(normalized.level) || "info"
    };
    const filename = optionalString(normalized.filename);
    if (filename) {
        payload.filename = filename;
    }
    const result = await callBrowser("browser_console_messages", payload);
    return maybePersistLargeText("browser_console_messages", payload, result, "console_messages", "log");
}

async function drag(params: unknown) {
    const normalized = assertObject(params, "drag");
    return callBrowser("browser_drag", {
        startElement: requireString(normalized.startElement, "startElement"),
        startRef: requireString(normalized.startRef, "startRef"),
        endElement: requireString(normalized.endElement, "endElement"),
        endRef: requireString(normalized.endRef, "endRef")
    });
}

async function evaluate(params: unknown) {
    const normalized = assertObject(params, "evaluate");
    const payload: EvaluatePayload = {
        function: requireString(normalized.function, "function")
    };
    const ref = optionalString(normalized.ref);
    const element = optionalString(normalized.element);
    if (element && !ref) {
        throw new Error("ref is required when element is provided");
    }
    if (ref) {
        payload.ref = ref;
    }
    if (element) {
        payload.element = element;
    }
    return callBrowser("browser_evaluate", payload);
}

async function upload(params: unknown) {
    const normalized = assertObject(params, "upload");
    const payload: UploadPayload = {};
    if (normalized.paths !== undefined) {
        payload.paths = requireStringArray(normalized.paths, "paths");
    }
    return callBrowser("browser_file_upload", payload);
}

function normalizeFormFields(fields: unknown): FillFormFieldPayload[] {
    const array = optionalArray(fields, "fields");
    if (!array || array.length === 0) {
        throw new Error("fields must be a non-empty array");
    }
    return array.map((field, index) => {
        if (!field || typeof field !== "object" || Array.isArray(field)) {
            throw new Error("fields[" + index + "] must be an object");
        }
        const normalizedField = field as JsonObject;
        const normalized: FillFormFieldPayload = {
            name: requireString(normalizedField.name, "fields[" + index + "].name"),
            type: requireString(normalizedField.type, "fields[" + index + "].type"),
            value: normalizedField.value as ToolParamValue
        };
        const ref = optionalString(normalizedField.ref);
        const selector = optionalString(normalizedField.selector);
        if (!ref && !selector) {
            throw new Error("fields[" + index + "] requires ref or selector");
        }
        if (ref) {
            normalized.ref = ref;
        }
        if (selector) {
            normalized.selector = selector;
        }
        return normalized;
    });
}

async function fill_form(params: unknown) {
    const normalized = assertObject(params, "fill_form");
    const payload: FillFormPayload = {
        fields: normalizeFormFields(normalized.fields)
    };
    return callBrowser("browser_fill_form", payload);
}

async function handle_dialog(params: unknown) {
    const normalized = assertObject(params, "handle_dialog");
    if (typeof normalized.accept !== "boolean") {
        throw new Error("accept must be a boolean");
    }
    const payload: HandleDialogPayload = {
        accept: normalized.accept
    };
    const promptText = optionalString(normalized.promptText);
    if (promptText) {
        payload.promptText = promptText;
    }
    return callBrowser("browser_handle_dialog", payload);
}

async function hover(params: unknown) {
    const normalized = assertObject(params, "hover");
    const payload: HoverPayload = {
        ref: requireString(normalized.ref, "ref")
    };
    const element = optionalString(normalized.element);
    if (element) {
        payload.element = element;
    }
    return callBrowser("browser_hover", payload);
}

async function goto(params: unknown) {
    const normalized = assertObject(params, "goto");
    return callBrowser("browser_navigate", {
        url: requireString(normalized.url, "url")
    });
}

async function back() {
    return callBrowser("browser_navigate_back");
}

async function network_requests(params: unknown) {
    const normalized = assertObject(params, "network_requests");
    const payload: NetworkRequestsPayload = {};
    const includeStatic = optionalBoolean(normalized.includeStatic, "includeStatic");
    const filename = optionalString(normalized.filename);
    if (includeStatic !== undefined) {
        payload.includeStatic = includeStatic;
    }
    if (filename) {
        payload.filename = filename;
    }
    const result = await callBrowser("browser_network_requests", payload);
    return maybePersistLargeText("browser_network_requests", payload, result, "network_requests", "log");
}

async function press_key(params: unknown) {
    const normalized = assertObject(params, "press_key");
    return callBrowser("browser_press_key", {
        key: requireString(normalized.key, "key")
    });
}

async function resize(params: unknown) {
    const normalized = assertObject(params, "resize");
    return callBrowser("browser_resize", {
        width: requireNumber(normalized.width, "width"),
        height: requireNumber(normalized.height, "height")
    });
}

async function run_code(params: unknown) {
    const normalized = assertObject(params, "run_code");
    return callBrowser("browser_run_code", {
        code: requireString(normalized.code, "code")
    });
}

async function select_option(params: unknown) {
    const normalized = assertObject(params, "select_option");
    const payload: SelectOptionPayload = {
        ref: requireString(normalized.ref, "ref"),
        values: requireStringArray(normalized.values, "values")
    };
    const element = optionalString(normalized.element);
    if (element) {
        payload.element = element;
    }
    return callBrowser("browser_select_option", payload);
}

async function snapshot(params: unknown) {
    const normalized = assertObject(params, "snapshot");
    const payload: FilenamePayload = {};
    const filename = optionalString(normalized.filename);
    if (filename) {
        payload.filename = filename;
    }
    const result = await callBrowser("browser_snapshot", payload);
    return maybePersistLargeText("browser_snapshot", payload, result, "snapshot", "md");
}

async function screenshot(params: unknown) {
    const normalized = assertObject(params, "screenshot");
    const payload: ScreenshotPayload = {
        type: optionalString(normalized.type) || "png"
    };
    const filename = optionalString(normalized.filename);
    const element = optionalString(normalized.element);
    const ref = optionalString(normalized.ref);
    const fullPage = optionalBoolean(normalized.fullPage, "fullPage");
    if (!["png", "jpeg", "jpg"].includes(payload.type)) {
        throw new Error("type must be png or jpeg");
    }
    if (ref && !element) {
        throw new Error("element is required when ref is provided");
    }
    if (element && !ref) {
        throw new Error("ref is required when element is provided");
    }
    if (fullPage && ref) {
        throw new Error("fullPage cannot be used with element screenshots");
    }
    if (filename) {
        payload.filename = filename;
    }
    if (element) {
        payload.element = element;
    }
    if (ref) {
        payload.ref = ref;
    }
    if (fullPage !== undefined) {
        payload.fullPage = fullPage;
    }
    return callBrowser("browser_take_screenshot", payload);
}

async function type(params: unknown) {
    const normalized = assertObject(params, "type");
    const payload: TypePayload = {
        ref: requireString(normalized.ref, "ref"),
        text: requireString(normalized.text, "text")
    };
    const element = optionalString(normalized.element);
    const submit = optionalBoolean(normalized.submit, "submit");
    const slowly = optionalBoolean(normalized.slowly, "slowly");
    if (element) {
        payload.element = element;
    }
    if (submit !== undefined) {
        payload.submit = submit;
    }
    if (slowly !== undefined) {
        payload.slowly = slowly;
    }
    return callBrowser("browser_type", payload);
}

async function wait_for(params: unknown) {
    const normalized = assertObject(params, "wait_for");
    const payload: WaitForPayload = {};
    const time = normalized.time !== undefined ? requireNumber(normalized.time, "time") : undefined;
    const text = optionalString(normalized.text);
    const textGone = optionalString(normalized.textGone);
    if (time === undefined && !text && !textGone) {
        throw new Error("one of time, text, or textGone is required");
    }
    if (time !== undefined) {
        payload.time = time;
    }
    if (text) {
        payload.text = text;
    }
    if (textGone) {
        payload.textGone = textGone;
    }
    return callBrowser("browser_wait_for", payload);
}

async function tabs(params: unknown) {
    const normalized = assertObject(params, "tabs");
    const action = requireString(normalized.action, "action");
    if (!["list", "create", "select", "close"].includes(action)) {
        throw new Error("action must be list, create, select, or close");
    }
    const payload: TabsPayload = { action: action as BrowserTabAction };
    if (normalized.index !== undefined) {
        payload.index = requireNumber(normalized.index, "index");
    }
    return callBrowser("browser_tabs", payload);
}

async function browserMain() {
    return "Browser package ready: " + TOOL_NAMES.join(", ");
}

exports.click = click;
exports.close = close;
exports.console_messages = console_messages;
exports.drag = drag;
exports.evaluate = evaluate;
exports.upload = upload;
exports.fill_form = fill_form;
exports.handle_dialog = handle_dialog;
exports.hover = hover;
exports.goto = goto;
exports.back = back;
exports.network_requests = network_requests;
exports.press_key = press_key;
exports.resize = resize;
exports.run_code = run_code;
exports.select_option = select_option;
exports.snapshot = snapshot;
exports.screenshot = screenshot;
exports.type = type;
exports.wait_for = wait_for;
exports.tabs = tabs;
exports.main = browserMain;
