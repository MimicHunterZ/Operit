package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.view.View
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Toast
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashSet
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

private const val EXECUTION_SUPPORT_TAG = "BrowserSessionTools"

internal class BrowserWebDownloadBridge(
    private val tools: StandardBrowserSessionTools,
    private val session: BrowserToolSession
) {
    @JavascriptInterface
    fun downloadBase64(base64Data: String?, fileName: String?, mimeType: String?) {
        tools.handleInlineDownload(
            session = session,
            base64Data = base64Data.orEmpty(),
            fileName = fileName.orEmpty(),
            mimeType = mimeType.orEmpty(),
            type = "inline"
        )
    }

    @JavascriptInterface
    fun log(message: String?) {
        AppLogger.d(EXECUTION_SUPPORT_TAG, "web download bridge: ${message.orEmpty()}")
    }
}

internal class BrowserAsyncBridge {
    @JavascriptInterface
    fun resolve(callId: String?, payload: String?) {
        if (callId.isNullOrBlank()) {
            return
        }
        StandardBrowserSessionTools.pendingAsyncJsCalls.remove(callId)?.let { pending ->
            pending.result = payload
            pending.latch.countDown()
        }
    }

    @JavascriptInterface
    fun reject(callId: String?, error: String?) {
        if (callId.isNullOrBlank()) {
            return
        }
        StandardBrowserSessionTools.pendingAsyncJsCalls.remove(callId)?.let { pending ->
            pending.error = error ?: "Unknown JavaScript error"
            pending.latch.countDown()
        }
    }
}

internal fun StandardBrowserSessionTools.createDownloadListener(
    session: BrowserToolSession
): DownloadListener {
    return DownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
        try {
            when {
                url.startsWith("blob:") -> {
                    injectBlobDownloaderScript(session, url)
                }

                url.startsWith("data:") -> {
                    handleInlineDownload(
                        session = session,
                        base64Data = url,
                        fileName = "",
                        mimeType = guessMimeTypeFromDataUrl(url).ifBlank { mimetype.orEmpty() },
                        type = "data",
                        sourceUrl = url
                    )
                }

                else -> {
                    handleRegularDownload(
                        session = session,
                        url = url,
                        userAgent = userAgent,
                        contentDisposition = contentDisposition,
                        mimeType = mimetype
                    )
                }
            }
        } catch (e: Exception) {
            recordDownloadEvent(
                session,
                WebDownloadEvent(
                    status = "failed",
                    type =
                        when {
                            url.startsWith("blob:") -> "blob"
                            url.startsWith("data:") -> "data"
                            else -> "http"
                        },
                    fileName =
                        if (url.startsWith("data:") || url.startsWith("blob:")) {
                            resolveInlineDownloadFileName("", mimetype.orEmpty().ifBlank { guessMimeTypeFromDataUrl(url) })
                        } else {
                            sanitizeFileName(URLUtil.guessFileName(url, contentDisposition, mimetype))
                        },
                    url = url,
                    mimeType = mimetype,
                    error = e.message ?: "download_failed"
                )
            )
            AppLogger.e(EXECUTION_SUPPORT_TAG, "Download failed for session=${session.id}: ${e.message}", e)
            StandardBrowserSessionTools.mainHandler.post {
                Toast.makeText(
                    context,
                    context.getString(R.string.download_failed, e.message ?: url),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

internal fun StandardBrowserSessionTools.handleRegularDownload(
    session: BrowserToolSession,
    url: String,
    userAgent: String,
    contentDisposition: String?,
    mimeType: String?
) {
    startBrowserManagedDownload(
        session = session,
        url = url,
        userAgent = userAgent,
        contentDisposition = contentDisposition,
        mimeType = mimeType
    )
}

internal fun StandardBrowserSessionTools.injectDownloadHelper(webView: WebView) {
    val script =
        """
        (function() {
            if (window.__operitDownloadHelperInjected) {
                return;
            }
            window.__operitDownloadHelperInjected = true;

            function guessName(anchor) {
                if (!anchor) return "";
                const raw = String(anchor.getAttribute("download") || "").trim();
                if (raw) return raw;
                try {
                    const url = String(anchor.href || "");
                    if (!url) return "";
                    const pathname = new URL(url, location.href).pathname || "";
                    const last = pathname.split("/").filter(Boolean).pop() || "";
                    return decodeURIComponent(last || "");
                } catch (_) {
                    return "";
                }
            }

            function downloadBlob(blobUrl, fileName) {
                fetch(blobUrl)
                    .then((response) => response.blob())
                    .then((blob) => new Promise((resolve, reject) => {
                        const reader = new FileReader();
                        reader.onload = function() {
                            resolve({ data: String(reader.result || ""), type: String(blob.type || "") });
                        };
                        reader.onerror = function() {
                            reject(reader.error || new Error("blob_reader_failed"));
                        };
                        reader.readAsDataURL(blob);
                    }))
                    .then((payload) => {
                        window.OperitWebDownloadBridge.downloadBase64(payload.data, fileName || "", payload.type || "application/octet-stream");
                    })
                    .catch((error) => {
                        window.OperitWebDownloadBridge.log("blob download failed: " + String(error || "unknown"));
                    });
            }

            document.addEventListener("click", function(event) {
                const anchor = event.target && event.target.closest ? event.target.closest("a[href]") : null;
                if (!anchor) {
                    return;
                }
                const href = String(anchor.href || "");
                if (!href) {
                    return;
                }
                const fileName = guessName(anchor);
                if (href.startsWith("blob:")) {
                    event.preventDefault();
                    downloadBlob(href, fileName);
                } else if (href.startsWith("data:")) {
                    event.preventDefault();
                    const mimeType = href.slice(5).split(";")[0] || "application/octet-stream";
                    window.OperitWebDownloadBridge.downloadBase64(href, fileName || "", mimeType);
                }
            }, true);
        })();
        """.trimIndent()

    runCatching { webView.evaluateJavascript(script, null) }
        .onFailure { AppLogger.w(EXECUTION_SUPPORT_TAG, "Failed to inject download helper: ${it.message}") }
}

internal fun StandardBrowserSessionTools.injectBlobDownloaderScript(
    session: BrowserToolSession,
    blobUrl: String
) {
    val script =
        """
        (function() {
            fetch(${quoteJs(blobUrl)})
                .then((response) => response.blob())
                .then((blob) => new Promise((resolve, reject) => {
                    const reader = new FileReader();
                    reader.onload = function() {
                        resolve({ data: String(reader.result || ""), type: String(blob.type || "") });
                    };
                    reader.onerror = function() {
                        reject(reader.error || new Error("blob_reader_failed"));
                    };
                    reader.readAsDataURL(blob);
                }))
                .then((payload) => {
                    window.OperitWebDownloadBridge.downloadBase64(
                        payload.data,
                        "download_${System.currentTimeMillis()}",
                        payload.type || "application/octet-stream"
                    );
                })
                .catch((error) => {
                    window.OperitWebDownloadBridge.log("blob downloader script failed: " + String(error || "unknown"));
                });
        })();
        """.trimIndent()

    runCatching {
        evaluateJavascriptSync(
            session.webView,
            script,
            StandardBrowserSessionTools.DEFAULT_TIMEOUT_MS.coerceIn(2_000L, 8_000L)
        )
    }.onFailure {
        recordDownloadEvent(
            session,
            WebDownloadEvent(
                status = "failed",
                type = "blob",
                fileName = "download_${System.currentTimeMillis()}",
                url = blobUrl,
                error = it.message ?: "blob_download_script_failed"
            )
        )
    }
}

internal fun StandardBrowserSessionTools.handleInlineDownload(
    session: BrowserToolSession,
    base64Data: String,
    fileName: String,
    mimeType: String,
    type: String,
    sourceUrl: String? = null
) {
    runCatching {
        startInlineManagedDownload(
            session = session,
            type = type,
            base64Data = base64Data,
            fileName = fileName,
            mimeType = mimeType,
            sourceUrl = sourceUrl
        )
    }.onFailure { error ->
        val resolvedFileName = resolveInlineDownloadFileName(fileName, mimeType)
        recordDownloadEvent(
            session,
            WebDownloadEvent(
                status = "failed",
                type = type,
                fileName = resolvedFileName,
                mimeType = mimeType,
                error = error.message ?: "inline_download_failed"
            )
        )
        StandardBrowserSessionTools.mainHandler.post {
            Toast.makeText(
                context,
                context.getString(R.string.download_failed, error.message ?: resolvedFileName),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

internal fun StandardBrowserSessionTools.recordDownloadEvent(
    session: BrowserToolSession,
    event: WebDownloadEvent
) {
    session.lastDownloadEvent = event
    session.lastDownloadEventAt = System.currentTimeMillis()
    AppLogger.d(
        EXECUTION_SUPPORT_TAG,
        "web download event session=${session.id}, status=${event.status}, type=${event.type}, file=${event.fileName}, url=${event.url.orEmpty()}"
    )
}

internal fun StandardBrowserSessionTools.guessMimeTypeFromDataUrl(dataUrl: String): String {
    if (!dataUrl.startsWith("data:")) {
        return "application/octet-stream"
    }
    return dataUrl.substringAfter("data:", "")
        .substringBefore(';', "application/octet-stream")
        .ifBlank { "application/octet-stream" }
}

internal fun StandardBrowserSessionTools.resolveInlineDownloadFileName(
    fileName: String,
    mimeType: String
): String {
    val trimmed = fileName.trim()
    if (trimmed.isNotBlank()) {
        return sanitizeFileName(trimmed)
    }
    return sanitizeFileName(
        "download_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}${determineExtensionFromMimeType(mimeType)}"
    )
}

private fun determineExtensionFromMimeType(mimeType: String): String {
    val lowerMimeType = mimeType.lowercase(Locale.ROOT)
    return when {
        lowerMimeType.startsWith("image/") -> ".${lowerMimeType.substringAfter('/')}"
        lowerMimeType.startsWith("audio/") -> ".${lowerMimeType.substringAfter('/')}"
        lowerMimeType.startsWith("video/") -> ".${lowerMimeType.substringAfter('/')}"
        lowerMimeType.contains("pdf") -> ".pdf"
        lowerMimeType.contains("json") -> ".json"
        lowerMimeType.contains("xml") -> ".xml"
        lowerMimeType.contains("csv") -> ".csv"
        lowerMimeType.contains("zip") -> ".zip"
        lowerMimeType.contains("html") -> ".html"
        lowerMimeType.contains("javascript") -> ".js"
        lowerMimeType.contains("plain") -> ".txt"
        else -> ".bin"
    }
}

internal fun StandardBrowserSessionTools.sanitizeFileName(fileName: String): String {
    val sanitized = fileName.replace("[\\\\/:*?\"<>|]".toRegex(), "_").trim()
    return if (sanitized.isBlank()) {
        "download_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"
    } else {
        sanitized
    }
}

internal fun StandardBrowserSessionTools.getSession(sessionId: String?): BrowserToolSession? {
    if (!sessionId.isNullOrBlank()) {
        return sessionById(sessionId)
    }

    return buildPageRegistry().activeSessionId?.let(::sessionById)
}

internal fun StandardBrowserSessionTools.sessionById(sessionId: String): BrowserToolSession? =
    StandardBrowserSessionTools.sessions[sessionId]

internal fun StandardBrowserSessionTools.resolvePreferredSessionId(): String? {
    val registry = buildPageRegistry()
    StandardBrowserSessionTools.activeSessionId = registry.activeSessionId
    return registry.activeSessionId
}

internal fun StandardBrowserSessionTools.addSessionOrder(sessionId: String) {
    synchronized(StandardBrowserSessionTools.sessionOrderLock) {
        if (!StandardBrowserSessionTools.sessionOrder.contains(sessionId)) {
            StandardBrowserSessionTools.sessionOrder.add(sessionId)
        }
    }
}

internal fun StandardBrowserSessionTools.removeSessionOrder(sessionId: String) {
    synchronized(StandardBrowserSessionTools.sessionOrderLock) {
        StandardBrowserSessionTools.sessionOrder.remove(sessionId)
    }
}

internal fun StandardBrowserSessionTools.listSessionIdsInOrder(): List<String> {
    synchronized(StandardBrowserSessionTools.sessionOrderLock) {
        return StandardBrowserSessionTools.sessionOrder.toList()
    }
}

internal fun StandardBrowserSessionTools.evaluateJavascriptSync(
    webView: WebView,
    script: String,
    timeoutMs: Long
): String {
    val latch = CountDownLatch(1)
    var result: String? = null

    StandardBrowserSessionTools.mainHandler.post {
        try {
            if (!webView.isAttachedToWindow) {
                result = JSONObject.quote("WebView is not attached")
                latch.countDown()
                return@post
            }
            webView.evaluateJavascript(script) { value ->
                result = value
                latch.countDown()
            }
        } catch (e: Exception) {
            result = JSONObject.quote("JavaScript evaluation error: ${e.message}")
            latch.countDown()
        }
    }

    if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
        throw RuntimeException("JavaScript execution timeout (${timeoutMs}ms)")
    }

    return result ?: "null"
}

internal fun StandardBrowserSessionTools.decodeJsResult(raw: String?): String {
    if (raw.isNullOrBlank() || raw == "null") {
        return ""
    }

    if (raw.startsWith("\"") && raw.endsWith("\"")) {
        return try {
            JSONObject("{\"v\":$raw}").getString("v")
        } catch (_: Exception) {
            raw.substring(1, raw.length - 1)
        }
    }

    return raw
}

private fun quoteJs(value: String): String = JSONObject.quote(value)

internal fun StandardBrowserSessionTools.quoteJsCode(value: String): String {
    val escaped =
        buildString(value.length + 8) {
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '\'' -> append("\\'")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }
    return "'$escaped'"
}

internal fun StandardBrowserSessionTools.renderJsArrayCode(values: Collection<String>): String =
    values.joinToString(prefix = "[", postfix = "]", separator = ", ") { quoteJsCode(it) }

internal fun StandardBrowserSessionTools.ensureOverlayPermission(toolName: String): ToolResult? {
    val appContext = context.applicationContext
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(appContext)) {
        error(toolName, "Overlay permission is required for browser tools.")
    } else {
        null
    }
}

internal fun StandardBrowserSessionTools.buildPageRegistry(): BrowserPageRegistry {
    val orderedIds = orderedSessionIdsFromState()
    val activeId =
        StandardBrowserSessionTools.activeSessionId?.takeIf { sessionById(it) != null }
            ?: orderedIds.firstOrNull()
    return BrowserPageRegistry(
        orderedSessionIds = orderedIds,
        activeSessionId = activeId,
        overlayExpanded = StandardBrowserSessionTools.browserHost?.isExpanded() == true,
        snapshots = orderedIds.associateWith { id -> sessionById(id)?.lastSnapshot }
    )
}

private fun StandardBrowserSessionTools.orderedSessionIdsFromState(): List<String> {
    val orderedIds = LinkedHashSet<String>()
    listSessionIdsInOrder()
        .filter { sessionById(it) != null }
        .forEach(orderedIds::add)
    StandardBrowserSessionTools.sessions.values
        .sortedWith(compareBy<BrowserToolSession> { it.createdAt }.thenBy { it.id })
        .mapTo(orderedIds) { it.id }
    return orderedIds.toList()
}

internal fun StandardBrowserSessionTools.orderedSessionIds(): List<String> =
    buildPageRegistry().orderedSessionIds

internal fun StandardBrowserSessionTools.currentTabIndex(sessionId: String): Int =
    buildPageRegistry().orderedSessionIds.indexOf(sessionId)

internal fun StandardBrowserSessionTools.sessionIdAtIndex(index: Int): String? =
    buildPageRegistry().orderedSessionIds.getOrNull(index)

internal fun StandardBrowserSessionTools.pageError(
    toolName: String,
    session: BrowserToolSession?,
    message: String
): ToolResult {
    val rendered =
        if (session != null) {
            buildBrowserResponse(
                openTabs = renderOpenTabs(),
                pageState = renderPageState(session),
                snapshot = captureSnapshotText(session),
                modalState = renderModalState(session),
                error = message
            )
        } else {
            buildBrowserResponse(error = message)
        }
    return ToolResult(
        toolName = toolName,
        success = false,
        result = StringResultData(rendered),
        error = rendered
    )
}

internal fun StandardBrowserSessionTools.buildSettledBrowserResponse(
    settlement: StandardBrowserSessionTools.BrowserActionSettlement,
    code: String? = null,
    snapshot: String? = settlement.snapshot.markdown,
    consoleMessages: String? = renderNewConsoleMessages(settlement.session, settlement.consoleMarker),
    modalState: String? = renderModalState(settlement.session),
    downloads: String? = renderDownloads(settlement.session, settlement.downloadMarker),
    result: String? = null,
    error: String? = null
): String =
    buildBrowserResponse(
        code = code,
        openTabs = renderOpenTabs(settlement.registry),
        pageState = renderPageState(settlement.session),
        snapshot = snapshot,
        consoleMessages = consoleMessages,
        modalState = modalState,
        downloads = downloads,
        result = result,
        error = error
    )

internal fun StandardBrowserSessionTools.renderOpenTabs(
    registry: BrowserPageRegistry = buildPageRegistry()
): String {
    val activeId = registry.activeSessionId
    val ordered = registry.orderedSessionIds.mapNotNull(::sessionById)
    if (ordered.isEmpty()) {
        return "No open tabs."
    }
    return ordered.mapIndexed { index, session ->
        val active = if (session.id == activeId) " [active]" else ""
        val title = sessionDisplayTitle(session).ifBlank { "about:blank" }
        "- [$index] $title$active\n  ${session.currentUrl.ifBlank { "about:blank" }}"
    }.joinToString("\n")
}

internal fun StandardBrowserSessionTools.renderPageState(session: BrowserToolSession): String {
    val viewport =
        StandardBrowserSessionTools.browserHost?.currentViewportSize()
            ?: Pair(
                (session.viewportWidthPx ?: session.webView.width).coerceAtLeast(0),
                (session.viewportHeightPx ?: session.webView.height).coerceAtLeast(0)
            )
    return buildString {
        appendLine("- Title: ${session.pageTitle.ifBlank { "about:blank" }}")
        appendLine("- URL: ${session.currentUrl.ifBlank { "about:blank" }}")
        appendLine("- Loading: ${if (session.isLoading) "yes" else "no"}")
        appendLine("- Can go back: ${if (session.canGoBack) "yes" else "no"}")
        appendLine("- Can go forward: ${if (session.canGoForward) "yes" else "no"}")
        append("- Viewport: ${viewport.first}x${viewport.second}")
    }
}

internal fun StandardBrowserSessionTools.locatorExpressionForElement(
    session: BrowserToolSession,
    ref: String,
    selectorSuffix: String
): String = locatorExpressionForRef(session, ref) + selectorSuffix

internal fun StandardBrowserSessionTools.evaluateJavascriptAsync(
    webView: WebView,
    expression: String,
    timeoutMs: Long
): String {
    val callId = UUID.randomUUID().toString()
    val pending = PendingAsyncJsCall()
    StandardBrowserSessionTools.pendingAsyncJsCalls[callId] = pending
    StandardBrowserSessionTools.mainHandler.post {
        try {
            if (!webView.isAttachedToWindow) {
                StandardBrowserSessionTools.pendingAsyncJsCalls.remove(callId)
                pending.error = "WebView is not attached"
                pending.latch.countDown()
                return@post
            }
            val wrapped =
                """
                (function() {
                    try {
                        const operitExpression = ${quoteJs(expression)};
                        const operitValue = (0, eval)(operitExpression);
                        Promise.resolve(operitValue)
                            .then(function(value) {
                                let payload;
                                try {
                                    payload = JSON.stringify({ ok: true, value: value });
                                } catch (_) {
                                    payload = JSON.stringify({ ok: true, value: String(value) });
                                }
                                window.OperitAsyncBridge.resolve(${quoteJs(callId)}, payload);
                            })
                            .catch(function(error) {
                                window.OperitAsyncBridge.reject(
                                    ${quoteJs(callId)},
                                    String(error && (error.stack || error.message || error) || "Async execution failed")
                                );
                            });
                    } catch (error) {
                        window.OperitAsyncBridge.reject(
                            ${quoteJs(callId)},
                            String(error && (error.stack || error.message || error) || "Async execution failed")
                        );
                    }
                    return true;
                })();
                """.trimIndent()
            webView.evaluateJavascript(wrapped, null)
        } catch (e: Exception) {
            StandardBrowserSessionTools.pendingAsyncJsCalls.remove(callId)
            pending.error = e.message ?: "Async JavaScript wrapper error"
            pending.latch.countDown()
        }
    }
    if (!pending.latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
        StandardBrowserSessionTools.pendingAsyncJsCalls.remove(callId)
        throw RuntimeException("JavaScript execution timeout (${timeoutMs}ms)")
    }
    pending.error?.let { throw RuntimeException(it) }
    return pending.result ?: "{\"ok\":true,\"value\":null}"
}

private fun extractAsyncJsValue(payload: String): String {
    val json = JSONObject(payload)
    val value = json.opt("value")
    return when (value) {
        null, JSONObject.NULL -> ""
        is String -> value
        else -> value.toString()
    }
}

internal fun StandardBrowserSessionTools.evaluatePageFunction(
    webView: WebView,
    functionSource: String,
    ref: String?,
    timeoutMs: Long
): String {
    val expression =
        """
        (async function() {
            const refValue = ${quoteJs(ref.orEmpty())};
            const target =
                refValue
                    ? Array.from(document.querySelectorAll('[aria-ref]')).find((el) => String(el.getAttribute('aria-ref') || '') === refValue)
                    : undefined;
            if (refValue && !target) {
                throw new Error("ref_not_found");
            }
            const fn = (${functionSource});
            if (typeof fn !== "function") {
                throw new Error("function must evaluate to a callable value");
            }
            return refValue ? await fn(target) : await fn();
        })()
        """.trimIndent()
    return extractAsyncJsValue(evaluateJavascriptAsync(webView, expression, timeoutMs))
}

internal fun StandardBrowserSessionTools.parseFormFields(rawFields: String): List<JSONObject>? {
    return try {
        val array = JSONArray(rawFields)
        buildList(array.length()) {
            for (index in 0 until array.length()) {
                add(array.getJSONObject(index))
            }
        }
    } catch (_: Exception) {
        null
    }
}

internal fun StandardBrowserSessionTools.fillFormFields(
    webView: WebView,
    rawFields: String
): String {
    val script =
        """
        (function() {
            const fields = $rawFields;
            const results = [];
            const findByRef = (refValue) => Array.from(document.querySelectorAll('[aria-ref]')).find((el) => String(el.getAttribute('aria-ref') || '') === String(refValue || ''));
            fields.forEach((field, index) => {
                const target =
                    field.ref ? findByRef(field.ref) :
                    field.selector ? document.querySelector(String(field.selector)) :
                    null;
                if (!target) {
                    throw new Error("field_not_found:" + (field.name || index));
                }
                const type = String(field.type || target.type || target.tagName || "").toLowerCase();
                const value = field.value;
                try { target.focus(); } catch (_) {}
                if (type === "checkbox") {
                    target.checked = String(value).toLowerCase() === "true";
                    target.dispatchEvent(new Event("input", { bubbles: true }));
                    target.dispatchEvent(new Event("change", { bubbles: true }));
                } else if (type === "combobox" || target.tagName === "SELECT") {
                    const options = Array.from(target.options || []);
                    const match = options.find((option) => String(option.text) === String(value) || String(option.value) === String(value));
                    if (!match) {
                        throw new Error("option_not_found:" + String(value));
                    }
                    target.value = match.value;
                    target.dispatchEvent(new Event("input", { bubbles: true }));
                    target.dispatchEvent(new Event("change", { bubbles: true }));
                } else {
                    target.value = String(value ?? "");
                    target.dispatchEvent(new Event("input", { bubbles: true }));
                    target.dispatchEvent(new Event("change", { bubbles: true }));
                }
                results.push(String(field.name || ("field_" + (index + 1))) + " => " + type);
            });
            return results.join("\n");
        })();
        """.trimIndent()
    return decodeJsResult(evaluateJavascriptSync(webView, script, StandardBrowserSessionTools.DEFAULT_TIMEOUT_MS))
}

internal fun StandardBrowserSessionTools.pressKeyOnPage(
    webView: WebView,
    key: String
): String {
    val script =
        """
        (function() {
            const keyValue = ${quoteJs(key)};
            const target = document.activeElement || document.body || document.documentElement;
            if (!target) {
                return "No focus target available.";
            }
            const dispatch = (type) => target.dispatchEvent(new KeyboardEvent(type, { key: keyValue, bubbles: true, cancelable: true }));
            dispatch("keydown");
            if (keyValue.length === 1 && target && ("value" in target)) {
                const start = typeof target.selectionStart === "number" ? target.selectionStart : String(target.value || "").length;
                const end = typeof target.selectionEnd === "number" ? target.selectionEnd : start;
                const current = String(target.value || "");
                target.value = current.slice(0, start) + keyValue + current.slice(end);
                target.selectionStart = target.selectionEnd = start + keyValue.length;
                target.dispatchEvent(new Event("input", { bubbles: true }));
            } else if (keyValue === "Backspace" && target && ("value" in target)) {
                const current = String(target.value || "");
                target.value = current.slice(0, Math.max(0, current.length - 1));
                target.dispatchEvent(new Event("input", { bubbles: true }));
            } else if (keyValue === "Enter") {
                try {
                    const form = target.form || target.closest && target.closest("form");
                    if (form && typeof form.requestSubmit === "function") {
                        form.requestSubmit();
                    }
                } catch (_) {}
                target.dispatchEvent(new Event("change", { bubbles: true }));
            }
            dispatch("keyup");
            return "Pressed " + keyValue;
        })();
        """.trimIndent()
    return decodeJsResult(evaluateJavascriptSync(webView, script, StandardBrowserSessionTools.DEFAULT_TIMEOUT_MS))
}

internal fun StandardBrowserSessionTools.selectOptionsByRef(
    webView: WebView,
    ref: String,
    values: List<String>
): String {
    val script =
        """
        (function() {
            const refValue = ${quoteJs(ref)};
            const target = Array.from(document.querySelectorAll('[aria-ref]')).find((el) => String(el.getAttribute('aria-ref') || '') === refValue);
            if (!target) {
                throw new Error("ref_not_found");
            }
            const values = ${JSONArray(values).toString()};
            if (!target.options) {
                throw new Error("target_is_not_select");
            }
            const matched = [];
            Array.from(target.options).forEach((option) => {
                const shouldSelect = values.includes(String(option.value)) || values.includes(String(option.text));
                option.selected = shouldSelect;
                if (shouldSelect) {
                    matched.push(String(option.text || option.value || ""));
                }
            });
            target.dispatchEvent(new Event("input", { bubbles: true }));
            target.dispatchEvent(new Event("change", { bubbles: true }));
            return "Selected " + matched.join(", ");
        })();
        """.trimIndent()
    return decodeJsResult(evaluateJavascriptSync(webView, script, StandardBrowserSessionTools.DEFAULT_TIMEOUT_MS))
}

internal fun StandardBrowserSessionTools.typeIntoElementByRef(
    webView: WebView,
    ref: String,
    text: String,
    submit: Boolean,
    slowly: Boolean
): String {
    val expression =
        """
        (async function() {
            const refValue = ${quoteJs(ref)};
            const target = Array.from(document.querySelectorAll('[aria-ref]')).find((el) => String(el.getAttribute('aria-ref') || '') === refValue);
            if (!target) {
                throw new Error("ref_not_found");
            }
            const textValue = ${quoteJs(text)};
            const submitValue = ${if (submit) "true" else "false"};
            const slowValue = ${if (slowly) "true" else "false"};
            try { target.focus(); } catch (_) {}
            const applyValue = (value) => {
                if ("value" in target) {
                    target.value = value;
                } else if (target.isContentEditable) {
                    target.textContent = value;
                }
                target.dispatchEvent(new Event("input", { bubbles: true }));
            };
            if (slowValue) {
                let current = "";
                for (const ch of textValue) {
                    current += ch;
                    applyValue(current);
                    await new Promise((resolve) => setTimeout(resolve, 35));
                }
            } else {
                applyValue(String(textValue));
            }
            target.dispatchEvent(new Event("change", { bubbles: true }));
            if (submitValue) {
                target.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true, cancelable: true }));
                target.dispatchEvent(new KeyboardEvent("keyup", { key: "Enter", bubbles: true, cancelable: true }));
                try {
                    const form = target.form || target.closest && target.closest("form");
                    if (form && typeof form.requestSubmit === "function") {
                        form.requestSubmit();
                    }
                } catch (_) {}
            }
            return "Typed " + textValue.length + " character(s).";
        })()
        """.trimIndent()
    return extractAsyncJsValue(
        evaluateJavascriptAsync(
            webView,
            expression,
            StandardBrowserSessionTools.DEFAULT_TIMEOUT_MS.coerceAtLeast(12_000L)
        )
    )
}

internal fun StandardBrowserSessionTools.writeBrowserTextOutput(
    filename: String,
    content: String,
    defaultPrefix: String,
    extension: String
): String {
    val resolved = resolveBrowserOutputFile(filename, defaultPrefix, extension)
    resolved.parentFile?.mkdirs()
    resolved.writeText(content)
    return resolved.absolutePath
}

internal fun StandardBrowserSessionTools.resolveBrowserOutputFile(
    filename: String?,
    defaultPrefix: String,
    extension: String
): File {
    val baseDir = File(context.cacheDir, "browser-output").apply { mkdirs() }
    if (!filename.isNullOrBlank()) {
        val candidate = File(filename)
        return if (candidate.isAbsolute) {
            candidate
        } else {
            File(baseDir, filename)
        }
    }
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    return File(baseDir, "${defaultPrefix}_$timestamp.$extension")
}

internal fun StandardBrowserSessionTools.takeScreenshot(
    session: BrowserToolSession,
    filename: String?,
    type: String,
    fullPage: Boolean,
    ref: String?
): String {
    return runOnMainSync {
        ensureSessionAttachedOnMain(session.id)
        val resolvedType = if (type == "jpg") "jpeg" else type
        val output = resolveBrowserOutputFile(filename, "page", if (resolvedType == "png") "png" else "jpg")
        output.parentFile?.mkdirs()
        val bitmap =
            when {
                fullPage -> captureFullPageBitmap(session.webView)
                ref != null -> captureElementBitmap(session.webView, ref)
                else -> captureViewportBitmap(session.webView)
            }
        FileOutputStream(output).use { stream ->
            bitmap.compress(
                if (resolvedType == "png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                92,
                stream
            )
        }
        bitmap.recycle()
        output.absolutePath
    }
}

internal fun StandardBrowserSessionTools.captureViewportBitmap(webView: WebView): Bitmap {
    val width = webView.width.coerceAtLeast(1)
    val height = webView.height.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    webView.draw(canvas)
    return bitmap
}

internal fun StandardBrowserSessionTools.captureFullPageBitmap(webView: WebView): Bitmap {
    val (width, height) = resolveFullPageBitmapSize(webView)
    val originalWidth = webView.width.coerceAtLeast(1)
    val originalHeight = webView.height.coerceAtLeast(1)
    val originalScrollX = webView.scrollX
    val originalScrollY = webView.scrollY
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    webView.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
    )
    webView.layout(0, 0, width, height)
    webView.scrollTo(0, 0)
    webView.draw(canvas)
    webView.measure(
        View.MeasureSpec.makeMeasureSpec(originalWidth, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(originalHeight, View.MeasureSpec.EXACTLY)
    )
    webView.layout(0, 0, originalWidth, originalHeight)
    webView.scrollTo(originalScrollX, originalScrollY)
    return bitmap
}

internal fun StandardBrowserSessionTools.resolveFullPageBitmapSize(
    webView: WebView
): Pair<Int, Int> {
    val scale = webView.scale.takeIf { it > 0f } ?: 1f
    val pageSize =
        runJsonScript(
            webView,
            """
            (function() {
                const doc = document.documentElement;
                const body = document.body;
                const width = Math.max(
                    window.innerWidth || 0,
                    doc ? (doc.scrollWidth || 0) : 0,
                    doc ? (doc.offsetWidth || 0) : 0,
                    body ? (body.scrollWidth || 0) : 0,
                    body ? (body.offsetWidth || 0) : 0
                );
                const height = Math.max(
                    window.innerHeight || 0,
                    doc ? (doc.scrollHeight || 0) : 0,
                    doc ? (doc.offsetHeight || 0) : 0,
                    body ? (body.scrollHeight || 0) : 0,
                    body ? (body.offsetHeight || 0) : 0
                );
                return JSON.stringify({ ok: true, width, height });
            })();
            """.trimIndent(),
            "page_size_error"
        )

    val width =
        (((pageSize?.optDouble("width", 0.0) ?: 0.0) * scale).toInt())
            .coerceAtLeast(webView.width.coerceAtLeast(1))
    val height =
        (((pageSize?.optDouble("height", 0.0) ?: 0.0) * scale).toInt())
            .coerceAtLeast(webView.height.coerceAtLeast(1))
    return width to height
}

internal fun StandardBrowserSessionTools.captureElementBitmap(
    webView: WebView,
    ref: String
): Bitmap {
    val rect = resolveElementRect(webView, ref) ?: throw RuntimeException("ref_not_found")
    val bitmap = captureViewportBitmap(webView)
    val left = rect.left.coerceIn(0, bitmap.width - 1)
    val top = rect.top.coerceIn(0, bitmap.height - 1)
    val width = rect.width().coerceAtLeast(1).coerceAtMost(bitmap.width - left)
    val height = rect.height().coerceAtLeast(1).coerceAtMost(bitmap.height - top)
    return Bitmap.createBitmap(bitmap, left, top, width, height)
}

internal fun StandardBrowserSessionTools.resolveElementRect(
    webView: WebView,
    ref: String
): Rect? {
    val script =
        """
        (function() {
            const refValue = ${quoteJs(ref)};
            const target = Array.from(document.querySelectorAll('[aria-ref]')).find((el) => String(el.getAttribute('aria-ref') || '') === refValue);
            if (!target) {
                return JSON.stringify({ ok: false, error: "ref_not_found" });
            }
            try { target.scrollIntoView({ block: "center", inline: "center" }); } catch (_) {}
            const rect = target.getBoundingClientRect();
            return JSON.stringify({
                ok: true,
                left: Math.max(0, Math.floor(rect.left)),
                top: Math.max(0, Math.floor(rect.top)),
                right: Math.max(0, Math.ceil(rect.right)),
                bottom: Math.max(0, Math.ceil(rect.bottom))
            });
        })();
        """.trimIndent()
    val json = runJsonScript(webView, script, "element_rect_error") ?: return null
    if (!json.optBoolean("ok", false)) {
        return null
    }
    return Rect(
        json.optInt("left"),
        json.optInt("top"),
        json.optInt("right"),
        json.optInt("bottom")
    )
}

internal fun StandardBrowserSessionTools.runPlaywrightLikeCode(
    session: BrowserToolSession,
    code: String
): String {
    val expression =
        """
        (async function() {
            const isVisible = (el) => {
                if (!el || el.nodeType !== 1) return false;
                const style = window.getComputedStyle(el);
                if (!style || style.visibility === "hidden" || style.display === "none") return false;
                const rect = el.getBoundingClientRect();
                return rect.width > 0 || rect.height > 0;
            };
            const roleFor = (el) => {
                const explicit = String(el.getAttribute("role") || "").trim();
                if (explicit) return explicit;
                const tag = String(el.tagName || "").toLowerCase();
                if (tag === "a") return "link";
                if (tag === "button") return "button";
                if (tag === "select") return "combobox";
                if (tag === "textarea") return "textbox";
                if (tag === "input") {
                    const type = String(el.getAttribute("type") || "").toLowerCase();
                    if (type === "checkbox") return "checkbox";
                    if (type === "radio") return "radio";
                    if (type === "submit" || type === "button" || type === "reset") return "button";
                    return "textbox";
                }
                return "generic";
            };
            const nameFor = (el) => String(
                el.getAttribute("aria-label") ||
                el.getAttribute("title") ||
                el.getAttribute("placeholder") ||
                el.getAttribute("alt") ||
                el.getAttribute("value") ||
                el.innerText ||
                el.textContent ||
                ""
            ).replace(/\s+/g, " ").trim();
            const findByRole = (role, name) => Array.from(document.querySelectorAll("*"))
                .filter((el) => isVisible(el) && roleFor(el) === String(role))
                .find((el) => name == null || nameFor(el) === String(name));
            const makeLocator = (resolver, description) => ({
                async click() {
                    const el = resolver();
                    if (!el) throw new Error(description + " not found");
                    try { el.scrollIntoView({ block: "center", inline: "center" }); } catch (_) {}
                    try { el.focus({ preventScroll: true }); } catch (_) {}
                    setTimeout(() => { try { el.click(); } catch (_) {} }, 0);
                    await new Promise((resolve) => setTimeout(resolve, 60));
                    return null;
                },
                async hover() {
                    const el = resolver();
                    if (!el) throw new Error(description + " not found");
                    const rect = el.getBoundingClientRect();
                    ["pointerover", "mouseover", "mouseenter", "mousemove"].forEach((type) => {
                        el.dispatchEvent(new MouseEvent(type, {
                            bubbles: true,
                            cancelable: true,
                            clientX: rect.left + rect.width / 2,
                            clientY: rect.top + rect.height / 2
                        }));
                    });
                    return null;
                },
                async fill(value) {
                    const el = resolver();
                    if (!el) throw new Error(description + " not found");
                    el.value = String(value ?? "");
                    el.dispatchEvent(new Event("input", { bubbles: true }));
                    el.dispatchEvent(new Event("change", { bubbles: true }));
                    return null;
                },
                async selectOption(values) {
                    const el = resolver();
                    if (!el || !el.options) throw new Error(description + " is not a select element");
                    const wanted = Array.isArray(values) ? values.map((item) => String(item)) : [String(values)];
                    Array.from(el.options).forEach((option) => {
                        option.selected = wanted.includes(String(option.value)) || wanted.includes(String(option.text));
                    });
                    el.dispatchEvent(new Event("input", { bubbles: true }));
                    el.dispatchEvent(new Event("change", { bubbles: true }));
                    return null;
                },
                async textContent() {
                    const el = resolver();
                    if (!el) throw new Error(description + " not found");
                    return String(el.textContent || "");
                }
            });
            const page = {
                async title() { return String(document.title || ""); },
                async url() { return String(location.href || ""); },
                async evaluate(fn) {
                    if (typeof fn !== "function") throw new Error("page.evaluate expects a function");
                    return await fn();
                },
                async waitForTimeout(ms) {
                    await new Promise((resolve) => setTimeout(resolve, Number(ms) || 0));
                    return null;
                },
                locator(selector) {
                    return makeLocator(() => document.querySelector(String(selector)), "locator(" + selector + ")");
                },
                getByRole(role, options) {
                    const name = options && Object.prototype.hasOwnProperty.call(options, "name") ? options.name : null;
                    return makeLocator(() => findByRole(role, name), "getByRole(" + role + ")");
                },
                keyboard: {
                    async press(key) {
                        const target = document.activeElement || document.body || document.documentElement;
                        if (!target) throw new Error("No active element");
                        target.dispatchEvent(new KeyboardEvent("keydown", { key: String(key), bubbles: true, cancelable: true }));
                        target.dispatchEvent(new KeyboardEvent("keyup", { key: String(key), bubbles: true, cancelable: true }));
                        return null;
                    }
                },
                async goto() {
                    throw new Error("page.goto is not supported in Android WebView browser_run_code. Use browser.goto instead.");
                },
                async goBack() {
                    throw new Error("page.goBack is not supported in Android WebView browser_run_code. Use browser.back instead.");
                },
                async setViewportSize() {
                    throw new Error("page.setViewportSize is not supported in browser_run_code. Use browser.resize instead.");
                }
            };
            const codeSource = ${quoteJs(code)};
            const AsyncFunction = Object.getPrototypeOf(async function(){}).constructor;

            let fn = null;
            try {
                const maybeFn = (0, eval)("(" + codeSource + ")");
                if (typeof maybeFn === "function") {
                    fn = maybeFn;
                }
            } catch (_) {}

            let value;
            if (fn) {
                value = await fn(page);
            } else {
                const runner = new AsyncFunction("page", "console", codeSource);
                value = await runner(page, console);
            }
            return value == null ? "" : value;
        })()
        """.trimIndent()
    return extractAsyncJsValue(
        evaluateJavascriptAsync(
            session.webView,
            expression,
            StandardBrowserSessionTools.DEFAULT_TIMEOUT_MS.coerceAtLeast(15_000L)
        )
    )
}

internal fun StandardBrowserSessionTools.parseHeaders(raw: String?): Map<String, String> {
    if (raw.isNullOrBlank()) {
        return emptyMap()
    }

    return try {
        val json = JSONObject(raw)
        val keys = json.keys()
        val out = mutableMapOf<String, String>()
        while (keys.hasNext()) {
            val key = keys.next()
            out[key] = json.optString(key, "")
        }
        out
    } catch (e: Exception) {
        AppLogger.w(EXECUTION_SUPPORT_TAG, "Invalid headers JSON: ${e.message}")
        emptyMap()
    }
}

internal fun StandardBrowserSessionTools.parseStringArrayParam(raw: String?): List<String>? {
    if (raw.isNullOrBlank()) {
        return emptyList()
    }

    return try {
        val array = JSONArray(raw)
        val out = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val value = array.opt(i)
            if (value == null || value == JSONObject.NULL) {
                continue
            }
            out.add(value.toString())
        }
        out
    } catch (e: Exception) {
        AppLogger.w(EXECUTION_SUPPORT_TAG, "Invalid array JSON: ${e.message}")
        null
    }
}

internal fun StandardBrowserSessionTools.param(tool: AITool, name: String): String? =
    tool.parameters.find { it.name == name }?.value

internal fun StandardBrowserSessionTools.boolParam(
    tool: AITool,
    name: String,
    default: Boolean
): Boolean {
    return when (param(tool, name)?.trim()?.lowercase()) {
        "true", "1", "yes", "on" -> true
        "false", "0", "no", "off" -> false
        else -> default
    }
}

internal fun StandardBrowserSessionTools.intParam(tool: AITool, name: String, default: Int): Int {
    return param(tool, name)?.trim()?.toIntOrNull() ?: default
}

internal fun StandardBrowserSessionTools.longParam(tool: AITool, name: String, default: Long): Long {
    return param(tool, name)?.trim()?.toLongOrNull() ?: default
}

internal fun StandardBrowserSessionTools.ok(toolName: String, payload: JSONObject): ToolResult {
    return ok(toolName, payload.toString())
}

internal fun StandardBrowserSessionTools.ok(toolName: String, payload: String): ToolResult {
    return ToolResult(
        toolName = toolName,
        success = true,
        result = StringResultData(payload)
    )
}

internal fun StandardBrowserSessionTools.error(toolName: String, message: String): ToolResult {
    val rendered = buildBrowserResponse(error = message)
    return ToolResult(
        toolName = toolName,
        success = false,
        result = StringResultData(rendered),
        error = rendered
    )
}
