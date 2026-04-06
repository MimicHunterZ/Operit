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
    "type",
    "wait_for",
    "tabs"
];
function normalizeOptionalString(value) {
    if (value === undefined) {
        return undefined;
    }
    const normalized = value.trim();
    return normalized ? normalized : undefined;
}
function buildLargeOutputFilename(prefix, extension) {
    const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
    const rand = Math.floor(Math.random() * 1000000);
    return OPERIT_CLEAN_ON_EXIT_DIR + "/browser_" + prefix + "_" + timestamp + "_" + rand + "." + extension;
}
async function maybePersistLargeBrowserResponse(result, prefix, extension = "md") {
    if (typeof result !== "string" || result.length <= MAX_INLINE_BROWSER_TEXT_CHARS) {
        return result;
    }
    await Tools.Files.mkdir(OPERIT_CLEAN_ON_EXIT_DIR, true);
    const filename = buildLargeOutputFilename(prefix, extension);
    await Tools.Files.write(filename, result, false);
    const normalizedPath = filename.replace(/\\/g, "/");
    return "Large browser response saved to:\n- [Browser Output](" + normalizedPath + ")";
}
function toToolParams(params) {
    return params;
}
async function callBrowser(nativeName, params = {}) {
    return toolCall(nativeName, toToolParams(params));
}
async function click(params) {
    const payload = {
        ref: params.ref
    };
    const element = normalizeOptionalString(params.element);
    const button = params.button;
    if (element) {
        payload.element = element;
    }
    if (button !== undefined) {
        if (!["left", "right", "middle"].includes(button)) {
            throw new Error("button must be left, right, or middle");
        }
        payload.button = button;
    }
    if (params.modifiers !== undefined) {
        payload.modifiers = params.modifiers;
    }
    if (params.doubleClick !== undefined) {
        payload.doubleClick = params.doubleClick;
    }
    const result = await callBrowser("browser_click", payload);
    return maybePersistLargeBrowserResponse(result, "click");
}
async function close() {
    const result = await callBrowser("browser_close");
    return maybePersistLargeBrowserResponse(result, "close");
}
async function console_messages(params = {}) {
    const payload = {
        level: normalizeOptionalString(params.level) || "info"
    };
    const filename = normalizeOptionalString(params.filename);
    if (filename) {
        payload.filename = filename;
    }
    const result = await callBrowser("browser_console_messages", payload);
    return maybePersistLargeBrowserResponse(result, "console_messages");
}
async function drag(params) {
    const result = await callBrowser("browser_drag", params);
    return maybePersistLargeBrowserResponse(result, "drag");
}
async function evaluate(params) {
    const payload = {
        function: params.function
    };
    const ref = normalizeOptionalString(params.ref);
    const element = normalizeOptionalString(params.element);
    if (element && !ref) {
        throw new Error("ref is required when element is provided");
    }
    if (ref) {
        payload.ref = ref;
    }
    if (element) {
        payload.element = element;
    }
    const result = await callBrowser("browser_evaluate", payload);
    return maybePersistLargeBrowserResponse(result, "evaluate");
}
async function upload(params = {}) {
    const payload = {};
    if (params.paths !== undefined) {
        payload.paths = params.paths;
    }
    const result = await callBrowser("browser_file_upload", payload);
    return maybePersistLargeBrowserResponse(result, "upload");
}
function normalizeFormFields(fields) {
    if (fields.length === 0) {
        throw new Error("fields must be a non-empty array");
    }
    return fields.map((field, index) => {
        const normalized = {
            name: field.name.trim(),
            type: field.type.trim(),
            value: field.value
        };
        if (!normalized.name) {
            throw new Error("fields[" + index + "].name is required");
        }
        if (!normalized.type) {
            throw new Error("fields[" + index + "].type is required");
        }
        const ref = normalizeOptionalString(field.ref);
        const selector = normalizeOptionalString(field.selector);
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
async function fill_form(params) {
    const payload = {
        fields: normalizeFormFields(params.fields)
    };
    const result = await callBrowser("browser_fill_form", payload);
    return maybePersistLargeBrowserResponse(result, "fill_form");
}
async function handle_dialog(params) {
    const payload = {
        accept: params.accept
    };
    const promptText = normalizeOptionalString(params.promptText);
    if (promptText) {
        payload.promptText = promptText;
    }
    const result = await callBrowser("browser_handle_dialog", payload);
    return maybePersistLargeBrowserResponse(result, "handle_dialog");
}
async function hover(params) {
    const payload = {
        ref: params.ref
    };
    const element = normalizeOptionalString(params.element);
    if (element) {
        payload.element = element;
    }
    const result = await callBrowser("browser_hover", payload);
    return maybePersistLargeBrowserResponse(result, "hover");
}
async function goto(params) {
    const result = await callBrowser("browser_navigate", params);
    return maybePersistLargeBrowserResponse(result, "goto");
}
async function back() {
    const result = await callBrowser("browser_navigate_back");
    return maybePersistLargeBrowserResponse(result, "back");
}
async function network_requests(params = {}) {
    const payload = {};
    if (params.includeStatic !== undefined) {
        payload.includeStatic = params.includeStatic;
    }
    const filename = normalizeOptionalString(params.filename);
    if (filename) {
        payload.filename = filename;
    }
    const result = await callBrowser("browser_network_requests", payload);
    return maybePersistLargeBrowserResponse(result, "network_requests");
}
async function press_key(params) {
    const result = await callBrowser("browser_press_key", params);
    return maybePersistLargeBrowserResponse(result, "press_key");
}
async function resize(params) {
    const result = await callBrowser("browser_resize", params);
    return maybePersistLargeBrowserResponse(result, "resize");
}
async function run_code(params) {
    const result = await callBrowser("browser_run_code", params);
    return maybePersistLargeBrowserResponse(result, "run_code");
}
async function select_option(params) {
    const payload = {
        ref: params.ref,
        values: params.values
    };
    const element = normalizeOptionalString(params.element);
    if (element) {
        payload.element = element;
    }
    const result = await callBrowser("browser_select_option", payload);
    return maybePersistLargeBrowserResponse(result, "select_option");
}
async function snapshot(params = {}) {
    const payload = {};
    const filename = normalizeOptionalString(params.filename);
    if (filename) {
        payload.filename = filename;
    }
    const result = await callBrowser("browser_snapshot", payload);
    return maybePersistLargeBrowserResponse(result, "snapshot");
}
async function type(params) {
    const payload = {
        ref: params.ref,
        text: params.text
    };
    const element = normalizeOptionalString(params.element);
    if (element) {
        payload.element = element;
    }
    if (params.submit !== undefined) {
        payload.submit = params.submit;
    }
    if (params.slowly !== undefined) {
        payload.slowly = params.slowly;
    }
    const result = await callBrowser("browser_type", payload);
    return maybePersistLargeBrowserResponse(result, "type");
}
async function wait_for(params = {}) {
    const payload = {};
    const time = params.time;
    const text = normalizeOptionalString(params.text);
    const textGone = normalizeOptionalString(params.textGone);
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
    const result = await callBrowser("browser_wait_for", payload);
    return maybePersistLargeBrowserResponse(result, "wait_for");
}
async function tabs(params) {
    const action = params.action;
    if (!["list", "create", "select", "close"].includes(action)) {
        throw new Error("action must be list, create, select, or close");
    }
    const payload = { action };
    if (params.index !== undefined) {
        payload.index = params.index;
    }
    const result = await callBrowser("browser_tabs", payload);
    return maybePersistLargeBrowserResponse(result, "tabs");
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
exports.type = type;
exports.wait_for = wait_for;
exports.tabs = tabs;
exports.main = browserMain;
