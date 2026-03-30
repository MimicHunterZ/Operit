"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.resolveExtraInfoI18n = resolveExtraInfoI18n;
exports.getAppContext = getAppContext;
exports.loadSettings = loadSettings;
exports.saveSettings = saveSettings;
exports.getExtraInfoInjectionEnabled = getExtraInfoInjectionEnabled;
exports.setExtraInfoInjectionEnabled = setExtraInfoInjectionEnabled;
exports.normalizeHookPayload = normalizeHookPayload;
exports.containsExtraInfoAttachment = containsExtraInfoAttachment;
exports.buildExtraInfoAttachmentTags = buildExtraInfoAttachmentTags;
const SETTINGS_PREFS_NAME = "toolpkg_message_insert_extra_info";
const SETTINGS_KEY = "message_insert_extra_info_settings";
const COMBINED_ATTACHMENT_FILE_NAME_PREFIX = "Time:";
const COMBINED_ATTACHMENT_ID_PREFIX = "message_insert_extra_bundle_";
const LEGACY_ATTACHMENT_FILE_NAMES = [
    "extra_info_time.txt",
    "extra_info_battery.txt",
    "extra_info_weather.txt",
    "extra_info_location.txt",
    "extra_info_notifications.txt",
];
const LEGACY_ATTACHMENT_ID_PREFIXES = [
    "message_insert_extra_time_",
    "message_insert_extra_battery_",
    "message_insert_extra_weather_",
    "message_insert_extra_location_",
    "message_insert_extra_notifications_",
];
const NOTIFICATION_FETCH_LIMIT = 5;
const ZH_CN_I18N = {
    menuTitle: "额外信息注入",
    menuDescription: "发送消息时自动附加时间、电量、天气、位置、通知等额外信息，并与设置页开关同步",
    toolboxTitle: "额外信息注入",
    toolboxSubtitle: "把时间、电量、天气、位置、通知作为显性附件挂到用户消息上方，并随消息一起保存。",
    toolboxBanner: "这里的开关和输入菜单里的“额外信息注入”是同一个状态；启用的项目都会在每次发送时一起注入。",
    masterSectionTitle: "注入开关",
    masterToggleTitle: "额外信息注入",
    masterToggleDescription: "和输入菜单里的“额外信息注入”开关完全同步，切一个地方，另一个地方会一起变化。",
    itemsSectionTitle: "注入项目",
    timeToggleTitle: "注入时间",
    timeToggleDescription: "每次发送消息时都插入当前时间附件。",
    batteryToggleTitle: "注入电量",
    batteryToggleDescription: "每次发送消息时都插入当前电量与充电状态。",
    weatherToggleTitle: "注入天气",
    weatherToggleDescription: "每次发送消息时都插入当前天气信息。",
    locationToggleTitle: "注入位置",
    locationToggleDescription: "每次发送消息时都插入当前定位信息与地址。",
    notificationsToggleTitle: "注入通知",
    notificationsToggleDescription: "每次发送消息时都插入最近通知摘要。",
    summarySectionTitle: "当前规则",
    summaryMasterEnabled: "额外信息注入：已开启",
    summaryMasterDisabled: "额外信息注入：已关闭",
    summaryTimeEnabled: "时间：每次发送都注入",
    summaryTimeDisabled: "时间：已关闭",
    summaryBatteryEnabled: "电量：每次发送都注入",
    summaryBatteryDisabled: "电量：已关闭",
    summaryWeatherEnabled: "天气：每次发送都注入",
    summaryWeatherDisabled: "天气：已关闭",
    summaryLocationEnabled: "位置：每次发送都注入",
    summaryLocationDisabled: "位置：已关闭",
    summaryNotificationsEnabled: "通知：每次发送都注入",
    summaryNotificationsDisabled: "通知：已关闭",
    summaryRulesHint: "这些设置会直接影响用户消息中显性附件的生成规则。",
    saveErrorPrefix: "保存失败：",
    attachmentTimeTitle: "【当前时间】",
    attachmentBatteryTitle: "【当前电量】",
    attachmentWeatherTitle: "【当前天气】",
    attachmentLocationTitle: "【当前位置】",
    attachmentNotificationsTitle: "【最近通知】",
    timeZoneLabel: "时区",
    weekdayLabel: "星期",
    batteryLevelLabel: "电量",
    batteryStatusLabel: "状态",
    batteryCharging: "充电中",
    batteryNotCharging: "未充电",
    batteryFull: "已充满",
    weatherLocationLabel: "地点",
    weatherConditionLabel: "天气",
    weatherTemperatureLabel: "温度",
    weatherHumidityLabel: "湿度",
    weatherWindLabel: "风速",
    weatherSourceLabel: "来源",
    locationAddressLabel: "地址",
    locationCoordinatesLabel: "坐标",
    locationAccuracyLabel: "精度",
    locationProviderLabel: "定位源",
    locationTimestampLabel: "时间",
    notificationCountLabel: "通知数量",
    notificationAppLabel: "应用",
    notificationTextLabel: "内容",
    notificationTimeLabel: "时间",
    notificationsEmpty: "当前没有可注入的通知",
    errorLabel: "错误",
};
const EN_US_I18N = {
    menuTitle: "Extra Info Injection",
    menuDescription: "Automatically attach time, battery, weather, location, notifications, and other extra info when sending messages, synced with the settings switch",
    toolboxTitle: "Extra Info Injection",
    toolboxSubtitle: "Attach time, battery, weather, location, and notifications as visible attachments above the user message and save them with the message.",
    toolboxBanner: "This switch is the same state as the input-menu toggle. Every enabled item is injected on each send.",
    masterSectionTitle: "Injection Switch",
    masterToggleTitle: "Extra Info Injection",
    masterToggleDescription: "This is the exact same switch as the input-menu toggle. Changing either one keeps the other in sync.",
    itemsSectionTitle: "Injection Items",
    timeToggleTitle: "Inject Time",
    timeToggleDescription: "Insert the current time attachment on every send.",
    batteryToggleTitle: "Inject Battery",
    batteryToggleDescription: "Insert the current battery level and charging state on every send.",
    weatherToggleTitle: "Inject Weather",
    weatherToggleDescription: "Insert current weather information on every send.",
    locationToggleTitle: "Inject Location",
    locationToggleDescription: "Insert current location and address on every send.",
    notificationsToggleTitle: "Inject Notifications",
    notificationsToggleDescription: "Insert a summary of recent notifications on every send.",
    summarySectionTitle: "Current Rules",
    summaryMasterEnabled: "Extra info injection: enabled",
    summaryMasterDisabled: "Extra info injection: disabled",
    summaryTimeEnabled: "Time: inject on every send",
    summaryTimeDisabled: "Time: disabled",
    summaryBatteryEnabled: "Battery: inject on every send",
    summaryBatteryDisabled: "Battery: disabled",
    summaryWeatherEnabled: "Weather: inject on every send",
    summaryWeatherDisabled: "Weather: disabled",
    summaryLocationEnabled: "Location: inject on every send",
    summaryLocationDisabled: "Location: disabled",
    summaryNotificationsEnabled: "Notifications: inject on every send",
    summaryNotificationsDisabled: "Notifications: disabled",
    summaryRulesHint: "These settings directly control how visible attachments are generated for user messages.",
    saveErrorPrefix: "Save failed: ",
    attachmentTimeTitle: "[Current Time]",
    attachmentBatteryTitle: "[Current Battery]",
    attachmentWeatherTitle: "[Current Weather]",
    attachmentLocationTitle: "[Current Location]",
    attachmentNotificationsTitle: "[Recent Notifications]",
    timeZoneLabel: "Time zone",
    weekdayLabel: "Weekday",
    batteryLevelLabel: "Level",
    batteryStatusLabel: "Status",
    batteryCharging: "Charging",
    batteryNotCharging: "Not charging",
    batteryFull: "Fully charged",
    weatherLocationLabel: "Location",
    weatherConditionLabel: "Condition",
    weatherTemperatureLabel: "Temperature",
    weatherHumidityLabel: "Humidity",
    weatherWindLabel: "Wind",
    weatherSourceLabel: "Source",
    locationAddressLabel: "Address",
    locationCoordinatesLabel: "Coordinates",
    locationAccuracyLabel: "Accuracy",
    locationProviderLabel: "Provider",
    locationTimestampLabel: "Time",
    notificationCountLabel: "Notification count",
    notificationAppLabel: "App",
    notificationTextLabel: "Content",
    notificationTimeLabel: "Time",
    notificationsEmpty: "There are no notifications to inject right now",
    errorLabel: "Error",
};
const DEFAULT_SETTINGS = {
    masterEnabled: false,
    injectTime: true,
    injectBattery: false,
    injectWeather: false,
    injectLocation: false,
    injectNotifications: false,
};
function normalizeLocale(locale) {
    const value = String(locale || "").trim().toLowerCase();
    if (!value) {
        return "zh-CN";
    }
    if (value.startsWith("en")) {
        return "en-US";
    }
    if (value.startsWith("zh")) {
        return "zh-CN";
    }
    return "zh-CN";
}
function resolveExtraInfoI18n(locale) {
    const rawLocale = locale ?? (typeof getLang === "function" ? getLang() : "");
    return normalizeLocale(rawLocale) === "en-US" ? EN_US_I18N : ZH_CN_I18N;
}
function getAppContext() {
    if (typeof Java.getApplicationContext !== "function") {
        return null;
    }
    return Java.getApplicationContext();
}
function getPrefs() {
    const context = getAppContext();
    if (!context) {
        throw new Error("application context unavailable");
    }
    return context.getSharedPreferences(SETTINGS_PREFS_NAME, 0);
}
function sanitizeSettings(input) {
    return {
        masterEnabled: Boolean(input?.masterEnabled ?? DEFAULT_SETTINGS.masterEnabled),
        injectTime: Boolean(input?.injectTime ?? DEFAULT_SETTINGS.injectTime),
        injectBattery: Boolean(input?.injectBattery ?? DEFAULT_SETTINGS.injectBattery),
        injectWeather: Boolean(input?.injectWeather ?? DEFAULT_SETTINGS.injectWeather),
        injectLocation: Boolean(input?.injectLocation ?? DEFAULT_SETTINGS.injectLocation),
        injectNotifications: Boolean(input?.injectNotifications ?? DEFAULT_SETTINGS.injectNotifications),
    };
}
function loadSettings() {
    try {
        const raw = String(getPrefs().getString(SETTINGS_KEY, "") || "").trim();
        if (!raw) {
            return { ...DEFAULT_SETTINGS };
        }
        return sanitizeSettings(JSON.parse(raw));
    }
    catch {
        return { ...DEFAULT_SETTINGS };
    }
}
function saveSettings(patch) {
    const next = sanitizeSettings({ ...loadSettings(), ...patch });
    getPrefs().edit().putString(SETTINGS_KEY, JSON.stringify(next)).apply();
    return next;
}
function getExtraInfoInjectionEnabled() {
    return loadSettings().masterEnabled;
}
function setExtraInfoInjectionEnabled(enabled) {
    return saveSettings({ masterEnabled: !!enabled });
}
function normalizeHookPayload(input) {
    const record = input;
    if (record && record.eventPayload && typeof record.eventPayload === "object") {
        return record.eventPayload;
    }
    return record || {};
}
function escapeXml(value) {
    return value
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/\"/g, "&quot;")
        .replace(/'/g, "&apos;");
}
function containsExtraInfoAttachment(input) {
    const attachmentMarkers = [
        `filename="${COMBINED_ATTACHMENT_FILE_NAME_PREFIX}`,
        `id="${COMBINED_ATTACHMENT_ID_PREFIX}`,
        ...LEGACY_ATTACHMENT_FILE_NAMES.map(name => `filename="${name}"`),
        ...LEGACY_ATTACHMENT_ID_PREFIXES.map(prefix => `id="${prefix}`),
    ];
    return attachmentMarkers.some(marker => input.includes(marker));
}
function buildAttachmentTag(idPrefix, fileName, content) {
    const attachmentId = `${idPrefix}${Date.now()}`;
    const attributes = [
        `id="${escapeXml(attachmentId)}"`,
        `filename="${escapeXml(fileName)}"`,
        `type="text/plain"`,
        `size="${content.length}"`,
    ].join(" ");
    return `<attachment ${attributes}>${escapeXml(content)}</attachment>`;
}
function buildTimeContent() {
    const SimpleDateFormat = Java.type("java.text.SimpleDateFormat");
    const DateClass = Java.type("java.util.Date");
    const LocaleClass = Java.type("java.util.Locale");
    const TimeZoneClass = Java.type("java.util.TimeZone");
    const locale = LocaleClass.getDefault();
    const now = new DateClass();
    const text = resolveExtraInfoI18n();
    const localTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", locale).format(now);
    const weekday = new SimpleDateFormat("EEEE", locale).format(now);
    const timeZone = TimeZoneClass.getDefault().getID();
    return [
        text.attachmentTimeTitle,
        localTime,
        `${text.timeZoneLabel}: ${timeZone}`,
        `${text.weekdayLabel}: ${weekday}`,
    ].join("\n");
}
function formatLocalTimestamp(timestampMs) {
    const SimpleDateFormat = Java.type("java.text.SimpleDateFormat");
    const DateClass = Java.type("java.util.Date");
    const LocaleClass = Java.type("java.util.Locale");
    const locale = LocaleClass.getDefault();
    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", locale).format(new DateClass(timestampMs));
}
function buildCombinedAttachmentFileName(timestampMs) {
    const SimpleDateFormat = Java.type("java.text.SimpleDateFormat");
    const DateClass = Java.type("java.util.Date");
    const LocaleClass = Java.type("java.util.Locale");
    const locale = LocaleClass.getDefault();
    const displayTime = new SimpleDateFormat("HH小时mm分钟ss秒dd天", locale).format(new DateClass(timestampMs));
    return `${COMBINED_ATTACHMENT_FILE_NAME_PREFIX}${displayTime}`;
}
function readBatterySnapshot() {
    const IntentClass = Java.type("android.content.Intent");
    const IntentFilterClass = Java.type("android.content.IntentFilter");
    const BatteryManagerClass = Java.type("android.os.BatteryManager");
    const context = getAppContext();
    if (!context) {
        throw new Error("application context unavailable");
    }
    const batteryIntent = context.registerReceiver(null, new IntentFilterClass(IntentClass.ACTION_BATTERY_CHANGED));
    if (!batteryIntent) {
        throw new Error("battery intent unavailable");
    }
    const level = Number(batteryIntent.getIntExtra(BatteryManagerClass.EXTRA_LEVEL, -1));
    const scale = Number(batteryIntent.getIntExtra(BatteryManagerClass.EXTRA_SCALE, -1));
    const status = Number(batteryIntent.getIntExtra(BatteryManagerClass.EXTRA_STATUS, -1));
    const percentage = level >= 0 && scale > 0 ? Math.round((level * 100) / scale) : -1;
    return {
        percentage,
        status,
    };
}
function resolveBatteryStatus(status) {
    const BatteryManagerClass = Java.type("android.os.BatteryManager");
    const text = resolveExtraInfoI18n();
    if (status === Number(BatteryManagerClass.BATTERY_STATUS_FULL)) {
        return text.batteryFull;
    }
    if (status === Number(BatteryManagerClass.BATTERY_STATUS_CHARGING)) {
        return text.batteryCharging;
    }
    return text.batteryNotCharging;
}
function buildBatteryContent() {
    const text = resolveExtraInfoI18n();
    const snapshot = readBatterySnapshot();
    if (snapshot.percentage < 0) {
        throw new Error("battery percentage unavailable");
    }
    return [
        text.attachmentBatteryTitle,
        `${text.batteryLevelLabel}: ${snapshot.percentage}%`,
        `${text.batteryStatusLabel}: ${resolveBatteryStatus(snapshot.status)}`,
    ].join("\n");
}
function buildErrorContent(title, error) {
    const text = resolveExtraInfoI18n();
    const message = error instanceof Error ? error.message : String(error || "unknown");
    return [
        title,
        `${text.errorLabel}: ${message}`,
    ].join("\n");
}
async function fetchWeatherPayload() {
    const response = await Tools.Net.http({
        url: "https://wttr.in/?format=j1",
        method: "GET",
        headers: {
            Accept: "application/json",
        },
        connect_timeout: 8,
        read_timeout: 8,
        validateStatus: false,
    });
    if (Number(response.statusCode) < 200 || Number(response.statusCode) >= 300) {
        throw new Error(`weather request failed: HTTP ${response.statusCode}`);
    }
    const rawContent = String(response.content || "").trim();
    if (!rawContent) {
        throw new Error("weather response empty");
    }
    try {
        return JSON.parse(rawContent);
    }
    catch (error) {
        const message = error instanceof Error ? error.message : String(error || "unknown");
        throw new Error(`weather response parse failed: ${message}`);
    }
}
async function buildWeatherContent() {
    const text = resolveExtraInfoI18n();
    const payload = await fetchWeatherPayload();
    const current = Array.isArray(payload?.current_condition) ? payload.current_condition[0] : null;
    const nearestArea = Array.isArray(payload?.nearest_area) ? payload.nearest_area[0] : null;
    const locationParts = [
        nearestArea?.areaName?.[0]?.value,
        nearestArea?.region?.[0]?.value,
        nearestArea?.country?.[0]?.value,
    ].map((item) => String(item || "").trim()).filter(Boolean);
    const weatherDesc = String(current?.weatherDesc?.[0]?.value || "").trim();
    const tempC = String(current?.temp_C || "").trim();
    const feelsLikeC = String(current?.FeelsLikeC || "").trim();
    const humidity = String(current?.humidity || "").trim();
    const windKmph = String(current?.windspeedKmph || "").trim();
    return [
        text.attachmentWeatherTitle,
        `${text.weatherLocationLabel}: ${locationParts.join(" / ") || "-"}`,
        `${text.weatherConditionLabel}: ${weatherDesc || "-"}`,
        `${text.weatherTemperatureLabel}: ${tempC ? `${tempC}°C` : "-"}${feelsLikeC ? ` (feels like ${feelsLikeC}°C)` : ""}`,
        `${text.weatherHumidityLabel}: ${humidity ? `${humidity}%` : "-"}`,
        `${text.weatherWindLabel}: ${windKmph ? `${windKmph} km/h` : "-"}`,
        `${text.weatherSourceLabel}: wttr.in`,
    ].join("\n");
}
async function buildLocationContent() {
    const text = resolveExtraInfoI18n();
    const location = await Tools.System.getLocation(false, 8);
    const addressParts = [
        location.address,
        location.city,
        location.province,
        location.country,
    ].map((item) => String(item || "").trim()).filter(Boolean);
    const coordinates = Number.isFinite(Number(location.latitude)) && Number.isFinite(Number(location.longitude))
        ? `${Number(location.latitude).toFixed(6)}, ${Number(location.longitude).toFixed(6)}`
        : "-";
    const accuracy = Number.isFinite(Number(location.accuracy)) && Number(location.accuracy) > 0
        ? `${Math.round(Number(location.accuracy))} m`
        : "-";
    const timestamp = Number.isFinite(Number(location.timestamp)) && Number(location.timestamp) > 0
        ? formatLocalTimestamp(Number(location.timestamp))
        : "-";
    return [
        text.attachmentLocationTitle,
        `${text.locationAddressLabel}: ${addressParts.join(" / ") || "-"}`,
        `${text.locationCoordinatesLabel}: ${coordinates}`,
        `${text.locationAccuracyLabel}: ${accuracy}`,
        `${text.locationProviderLabel}: ${String(location.provider || "-").trim() || "-"}`,
        `${text.locationTimestampLabel}: ${timestamp}`,
    ].join("\n");
}
async function buildNotificationsContent() {
    const text = resolveExtraInfoI18n();
    const result = await Tools.System.getNotifications(NOTIFICATION_FETCH_LIMIT, false);
    const notifications = Array.isArray(result?.notifications) ? result.notifications : [];
    const lines = [
        text.attachmentNotificationsTitle,
        `${text.notificationCountLabel}: ${notifications.length}`,
    ];
    if (!notifications.length) {
        lines.push(text.notificationsEmpty);
        return lines.join("\n");
    }
    notifications.forEach((notification, index) => {
        const packageName = String(notification?.packageName || "").trim() || "-";
        const notificationText = String(notification?.text || "").trim() || "-";
        const timestamp = Number.isFinite(Number(notification?.timestamp)) && Number(notification.timestamp) > 0
            ? formatLocalTimestamp(Number(notification.timestamp))
            : "-";
        lines.push(`#${index + 1}`, `${text.notificationAppLabel}: ${packageName}`, `${text.notificationTextLabel}: ${notificationText}`, `${text.notificationTimeLabel}: ${timestamp}`);
    });
    return lines.join("\n");
}
async function buildExtraInfoAttachmentTags(messageText) {
    const settings = loadSettings();
    if (!settings.masterEnabled || containsExtraInfoAttachment(messageText)) {
        return [];
    }
    const attachmentTimestampMs = Date.now();
    const contentBlocks = [];
    if (settings.injectTime) {
        contentBlocks.push(buildTimeContent());
    }
    if (settings.injectBattery) {
        let content = "";
        try {
            content = buildBatteryContent();
        }
        catch (error) {
            content = buildErrorContent(resolveExtraInfoI18n().attachmentBatteryTitle, error);
        }
        contentBlocks.push(content);
    }
    if (settings.injectWeather) {
        let content = "";
        try {
            content = await buildWeatherContent();
        }
        catch (error) {
            content = buildErrorContent(resolveExtraInfoI18n().attachmentWeatherTitle, error);
        }
        contentBlocks.push(content);
    }
    if (settings.injectLocation) {
        let content = "";
        try {
            content = await buildLocationContent();
        }
        catch (error) {
            content = buildErrorContent(resolveExtraInfoI18n().attachmentLocationTitle, error);
        }
        contentBlocks.push(content);
    }
    if (settings.injectNotifications) {
        let content = "";
        try {
            content = await buildNotificationsContent();
        }
        catch (error) {
            content = buildErrorContent(resolveExtraInfoI18n().attachmentNotificationsTitle, error);
        }
        contentBlocks.push(content);
    }
    if (!contentBlocks.length) {
        return [];
    }
    return [
        buildAttachmentTag(COMBINED_ATTACHMENT_ID_PREFIX, buildCombinedAttachmentFileName(attachmentTimestampMs), contentBlocks.join("\n\n")),
    ];
}
