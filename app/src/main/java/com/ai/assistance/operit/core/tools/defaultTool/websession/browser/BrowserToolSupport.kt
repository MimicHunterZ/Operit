package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebResourceRequest
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools
import java.util.Locale
import java.util.concurrent.CountDownLatch
import org.json.JSONArray
import org.json.JSONObject

internal data class BrowserPageRegistry(
    val orderedSessionIds: List<String>,
    val activeSessionId: String?,
    val overlayExpanded: Boolean,
    val snapshots: Map<String, BrowserSnapshot?>
)

internal data class BrowserSnapshot(
    val sessionId: String,
    val generation: Long,
    val title: String,
    val markdown: String,
    val nodesByRef: Map<String, BrowserSnapshotNode>,
    val createdAt: Long = System.currentTimeMillis()
)

internal data class BrowserSnapshotNode(
    val ref: String,
    val role: String,
    val name: String,
    val value: String?,
    val isActive: Boolean,
    val lineText: String
)

internal data class BrowserConsoleEntry(
    val level: String,
    val message: String,
    val sourceId: String? = null,
    val lineNumber: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)

internal data class BrowserNetworkRequestEntry(
    val method: String,
    val url: String,
    val isMainFrame: Boolean,
    val isStatic: Boolean,
    val headers: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

internal data class PendingDialog(
    val type: String,
    val message: String,
    val defaultValue: String? = null,
    val url: String? = null,
    val jsResult: JsResult? = null,
    val jsPromptResult: JsPromptResult? = null,
    val timestamp: Long = System.currentTimeMillis()
)

internal data class PendingAsyncJsCall(
    val latch: CountDownLatch = CountDownLatch(1),
    @Volatile var result: String? = null,
    @Volatile var error: String? = null
)

internal data class WebDownloadEvent(
    val status: String,
    val type: String,
    val fileName: String,
    val url: String? = null,
    val mimeType: String? = null,
    val savedPath: String? = null,
    val downloadId: Long? = null,
    val error: String? = null
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("status", status)
            .put("type", type)
            .put("file_name", fileName)
            .also { json ->
                if (!url.isNullOrBlank()) {
                    json.put("url", url)
                }
                if (!mimeType.isNullOrBlank()) {
                    json.put("mime_type", mimeType)
                }
                if (!savedPath.isNullOrBlank()) {
                    json.put("saved_path", savedPath)
                }
                if (downloadId != null) {
                    json.put("download_id", downloadId)
                }
                if (!error.isNullOrBlank()) {
                    json.put("error", error)
                }
            }
}

internal fun buildBrowserResponse(
    code: String? = null,
    openTabs: String? = null,
    pageState: String? = null,
    snapshot: String? = null,
    consoleMessages: String? = null,
    modalState: String? = null,
    downloads: String? = null,
    result: String? = null,
    error: String? = null
): String {
    val sections = mutableListOf<String>()
    if (!code.isNullOrBlank()) {
        sections += "### Ran Playwright code\n```js\n${code.trim()}\n```"
    }
    if (!openTabs.isNullOrBlank()) {
        sections += "### Open tabs\n${openTabs.trim()}"
    }
    if (!pageState.isNullOrBlank()) {
        sections += "### Page state\n${pageState.trim()}"
    }
    if (!snapshot.isNullOrBlank()) {
        sections += "### Snapshot\n${snapshot.trim()}"
    }
    if (!consoleMessages.isNullOrBlank()) {
        sections += "### New console messages\n${consoleMessages.trim()}"
    }
    if (!modalState.isNullOrBlank()) {
        sections += "### Modal state\n${modalState.trim()}"
    }
    if (!downloads.isNullOrBlank()) {
        sections += "### Downloads\n${downloads.trim()}"
    }
    if (!result.isNullOrBlank()) {
        sections += "### Result\n${result.trim()}"
    }
    if (!error.isNullOrBlank()) {
        sections += "### Error\n${error.trim()}"
    }
    return sections.joinToString("\n\n")
}

internal typealias BrowserToolSession = StandardBrowserSessionTools.WebSession
internal typealias BrowserToolActionPolicy = StandardBrowserSessionTools.BrowserActionSettlementPolicy
internal typealias BrowserToolActionMarkers = StandardBrowserSessionTools.BrowserActionMarkers
internal typealias BrowserToolActionSettlement = StandardBrowserSessionTools.BrowserActionSettlement
internal typealias BrowserSnapshotSession = BrowserToolSession

internal fun StandardBrowserSessionTools.latestConsoleTimestamp(session: BrowserToolSession): Long =
    synchronized(session.consoleEntries) {
        session.consoleEntries.lastOrNull()?.timestamp ?: 0L
    }

internal fun StandardBrowserSessionTools.appendConsoleEntry(
    session: BrowserToolSession,
    entry: BrowserConsoleEntry
) {
    synchronized(session.consoleEntries) {
        session.consoleEntries += entry
        if (session.consoleEntries.size > StandardBrowserSessionTools.MAX_EVENT_LOG_ENTRIES) {
            session.consoleEntries.removeAt(0)
        }
    }
}

internal fun StandardBrowserSessionTools.clearEventLogs(session: BrowserToolSession) {
    synchronized(session.consoleEntries) {
        session.consoleEntries.clear()
    }
    synchronized(session.networkEntries) {
        session.networkEntries.clear()
    }
}

internal fun StandardBrowserSessionTools.recordNetworkRequest(
    session: BrowserToolSession,
    request: WebResourceRequest
) {
    val url = request.url?.toString().orEmpty()
    if (url.isBlank()) {
        return
    }
    val headers = request.requestHeaders?.mapKeys { it.key ?: "" } ?: emptyMap()
    val acceptHeader = headers.entries.firstOrNull { it.key.equals("Accept", ignoreCase = true) }?.value
    val entry =
        com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserNetworkRequestEntry(
            method = request.method.orEmpty().ifBlank { "GET" },
            url = url,
            isMainFrame = request.isForMainFrame,
            isStatic = isStaticRequest(url, acceptHeader),
            headers = headers
        )
    synchronized(session.networkEntries) {
        session.networkEntries += entry
        if (session.networkEntries.size > StandardBrowserSessionTools.MAX_EVENT_LOG_ENTRIES) {
            session.networkEntries.removeAt(0)
        }
    }
}

internal fun StandardBrowserSessionTools.renderAllConsoleMessages(
    session: BrowserToolSession,
    level: String
): String {
    val threshold = consoleSeverity(level)
    val messages =
        synchronized(session.consoleEntries) {
            session.consoleEntries.toList()
        }.filter { consoleSeverity(it.level) <= threshold }
    if (messages.isEmpty()) {
        return "No console messages."
    }
    return messages.joinToString("\n") { entry ->
        val source = if (!entry.sourceId.isNullOrBlank()) " (${entry.sourceId}:${entry.lineNumber ?: 0})" else ""
        "- [${normalizeConsoleLevel(entry.level)}] ${entry.message}$source"
    }
}

internal fun StandardBrowserSessionTools.renderNewConsoleMessages(
    session: BrowserToolSession,
    marker: Long
): String? {
    val messages =
        synchronized(session.consoleEntries) {
            session.consoleEntries.filter { it.timestamp > marker }
        }
    if (messages.isEmpty()) {
        return null
    }
    return messages.joinToString("\n") { entry ->
        val source = if (!entry.sourceId.isNullOrBlank()) " (${entry.sourceId}:${entry.lineNumber ?: 0})" else ""
        "- [${normalizeConsoleLevel(entry.level)}] ${entry.message}$source"
    }
}

internal fun StandardBrowserSessionTools.renderNetworkRequestLog(
    session: BrowserToolSession,
    includeStatic: Boolean
): String {
    val entries =
        synchronized(session.networkEntries) {
            session.networkEntries.toList()
        }.filter { includeStatic || !it.isStatic }
    if (entries.isEmpty()) {
        return "No network requests recorded for the current page."
    }
    return entries.joinToString("\n") { entry ->
        val frameTag = if (entry.isMainFrame) " [main-frame]" else ""
        val staticTag = if (entry.isStatic) " [static]" else ""
        "- ${entry.method} ${entry.url}$frameTag$staticTag"
    }
}

internal fun StandardBrowserSessionTools.renderModalState(session: BrowserToolSession): String? {
    val dialog = session.pendingDialog ?: return null
    return buildString {
        appendLine("- Type: ${dialog.type}")
        appendLine("- Message: ${dialog.message.ifBlank { "(empty)" }}")
        if (!dialog.defaultValue.isNullOrBlank()) {
            appendLine("- Default value: ${dialog.defaultValue}")
        }
        append("- URL: ${dialog.url.orEmpty().ifBlank { session.currentUrl.ifBlank { "about:blank" } }}")
    }
}

internal fun StandardBrowserSessionTools.renderDownloads(
    session: BrowserToolSession,
    marker: Long
): String? = renderManagedDownloads(session, marker)

internal fun StandardBrowserSessionTools.requireSnapshotNode(
    session: BrowserToolSession,
    ref: String
): BrowserSnapshotNode? {
    val snapshot = session.lastSnapshot ?: latestSnapshot(session)
    return snapshot.nodesByRef[ref]
}

internal fun StandardBrowserSessionTools.captureActionMarkers(
    session: BrowserToolSession
): BrowserToolActionMarkers =
    BrowserToolActionMarkers(
        initialSessionId = session.id,
        initialUrl = readCurrentUrl(session.webView, session.currentUrl).ifBlank { session.currentUrl },
        consoleTimestamp = latestConsoleTimestamp(session),
        downloadTimestamp = latestBrowserDownloadEventAt(),
        snapshotGeneration = session.lastSnapshot?.generation ?: 0L,
        startedAt = System.currentTimeMillis()
    )

internal fun StandardBrowserSessionTools.isDocumentReady(session: BrowserToolSession): Boolean {
    if (session.pageLoaded && !session.isLoading) {
        return true
    }
    val ready =
        runCatching {
            decodeJsResult(
                evaluateJavascriptSync(
                    session.webView,
                    "(function(){ return String(document.readyState || ''); })();",
                    2_000L
                )
            )
        }.getOrNull()
    return ready == "complete" || ready == "interactive"
}

internal fun StandardBrowserSessionTools.waitForDocumentReady(
    session: BrowserToolSession,
    timeoutMs: Long
): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs.coerceAtLeast(250L)
    while (System.currentTimeMillis() < deadline) {
        if (isDocumentReady(session)) {
            return true
        }
        Thread.sleep(120)
    }
    return false
}

internal fun StandardBrowserSessionTools.matchesTextState(
    session: BrowserToolSession,
    text: String?,
    textGone: String?
): Boolean {
    val bodyText =
        runCatching {
            decodeJsResult(
                evaluateJavascriptSync(
                    session.webView,
                    "(function(){ return String((document.body && document.body.innerText) || ''); })();",
                    2_000L
                )
            )
        }.getOrElse { "" }
    val containsWanted = text == null || bodyText.contains(text)
    val goneSatisfied = textGone == null || !bodyText.contains(textGone)
    return containsWanted && goneSatisfied
}

internal fun StandardBrowserSessionTools.buildWaitForCode(
    timeSeconds: Double?,
    text: String?,
    textGone: String?
): String =
    when {
        text != null && textGone != null ->
            "await page.waitFor({ text: ${quoteJsCode(text)}, textGone: ${quoteJsCode(textGone)} });"
        text != null -> "await page.waitFor({ text: ${quoteJsCode(text)} });"
        textGone != null -> "await page.waitFor({ textGone: ${quoteJsCode(textGone)} });"
        else -> "await page.waitFor({ time: ${timeSeconds ?: 0.0} });"
    }

internal fun StandardBrowserSessionTools.buildClickCode(
    session: BrowserToolSession,
    ref: String,
    button: String,
    doubleClick: Boolean,
    modifiers: Set<String>
): String {
    val locator = locatorExpressionForRef(session, ref)
    val method = if (doubleClick) "dblclick" else "click"
    val options = mutableListOf<String>()
    if (button != "left") {
        options += "button: ${quoteJsCode(button)}"
    }
    if (modifiers.isNotEmpty()) {
        options += "modifiers: ${renderJsArrayCode(modifiers.toList())}"
    }
    return if (options.isEmpty()) {
        "await $locator.$method();"
    } else {
        "await $locator.$method({ ${options.joinToString(", ")} });"
    }
}

internal fun StandardBrowserSessionTools.settleBrowserAction(
    initialSession: BrowserToolSession,
    markers: BrowserToolActionMarkers,
    policy: BrowserToolActionPolicy = BrowserToolActionPolicy()
): BrowserToolActionSettlement {
    val deadline = markers.startedAt + policy.timeoutMs.coerceAtLeast(250L)
    var candidateSession = sessionById(markers.initialSessionId) ?: initialSession

    while (System.currentTimeMillis() < deadline) {
        val registry = buildPageRegistry()
        val activeSession =
            when {
                policy.allowActivePageSwitch -> registry.activeSessionId?.let(::sessionById)
                else -> sessionById(markers.initialSessionId)
            } ?: sessionById(markers.initialSessionId)
                ?: candidateSession

        candidateSession = activeSession
        if (actionSettled(activeSession, markers, policy)) {
            runOnMainSync<Unit> {
                ensureSessionAttachedOnMain(activeSession.id)
            }
            val snapshot = latestSnapshot(activeSession)
            val finalRegistry = buildPageRegistry()
            return BrowserToolActionSettlement(
                registry = finalRegistry,
                session = activeSession,
                snapshot = snapshot,
                consoleMarker = if (activeSession.id == markers.initialSessionId) markers.consoleTimestamp else 0L,
                downloadMarker = markers.downloadTimestamp,
                timedOut = false
            )
        }

        Thread.sleep(120)
    }

    val registry = buildPageRegistry()
    val activeSession =
        when {
            policy.allowActivePageSwitch -> registry.activeSessionId?.let(::sessionById)
            else -> sessionById(markers.initialSessionId)
        } ?: sessionById(markers.initialSessionId)
            ?: candidateSession
    runOnMainSync<Unit> {
        ensureSessionAttachedOnMain(activeSession.id)
    }
    val snapshot = latestSnapshot(activeSession)
    val finalRegistry = buildPageRegistry()
    return BrowserToolActionSettlement(
        registry = finalRegistry,
        session = activeSession,
        snapshot = snapshot,
        consoleMarker = if (activeSession.id == markers.initialSessionId) markers.consoleTimestamp else 0L,
        downloadMarker = markers.downloadTimestamp,
        timedOut = true
    )
}

internal fun StandardBrowserSessionTools.latestSnapshot(
    session: BrowserToolSession,
    forceRefresh: Boolean = true
): BrowserSnapshot {
    if (!forceRefresh) {
        session.lastSnapshot?.let { return it }
    }
    val snapshot = captureSnapshotModel(session)
    session.lastSnapshot = snapshot
    return snapshot
}

private fun StandardBrowserSessionTools.actionSettled(
    session: BrowserToolSession,
    markers: BrowserToolActionMarkers,
    policy: BrowserToolActionPolicy
): Boolean {
    val requiredElapsedMs = ((policy.waitForTimeSeconds ?: 0.0) * 1000.0).toLong().coerceAtLeast(0L)
    if (System.currentTimeMillis() - markers.startedAt < requiredElapsedMs) {
        return false
    }

    val dialogOpened = session.pendingDialog?.timestamp?.let { it >= markers.startedAt } == true
    val fileChooserOpened =
        session.pendingFileChooserCallback != null && session.lastFileChooserRequestAt >= markers.startedAt
    val downloadTriggered = latestBrowserDownloadEventAt() > markers.downloadTimestamp
    if (dialogOpened || fileChooserOpened || downloadTriggered) {
        return true
    }

    if (policy.waitForText != null || policy.waitForTextGone != null) {
        return matchesTextState(session, policy.waitForText, policy.waitForTextGone)
    }

    val activeSwitched = policy.allowActivePageSwitch && session.id != markers.initialSessionId
    val currentUrl = readCurrentUrl(session.webView, session.currentUrl).ifBlank { session.currentUrl }
    val urlChanged = currentUrl != markers.initialUrl

    if (policy.waitForNavigationChange && !activeSwitched && !urlChanged) {
        return false
    }

    val ready = isDocumentReady(session)
    if (policy.waitForDocumentReady || policy.waitForNavigationChange || activeSwitched || urlChanged) {
        return ready
    }

    if (session.isLoading && !ready) {
        return false
    }

    return ready && System.currentTimeMillis() - markers.startedAt >= 150L
}

private fun isStaticRequest(url: String, acceptHeader: String?): Boolean {
    val lowerUrl = url.lowercase(Locale.ROOT)
    val lowerAccept = acceptHeader?.lowercase(Locale.ROOT).orEmpty()
    return lowerAccept.contains("image/") ||
        lowerAccept.contains("font/") ||
        lowerAccept.contains("text/css") ||
        lowerAccept.contains("javascript") ||
        lowerUrl.endsWith(".png") ||
        lowerUrl.endsWith(".jpg") ||
        lowerUrl.endsWith(".jpeg") ||
        lowerUrl.endsWith(".gif") ||
        lowerUrl.endsWith(".svg") ||
        lowerUrl.endsWith(".css") ||
        lowerUrl.endsWith(".js") ||
        lowerUrl.endsWith(".woff") ||
        lowerUrl.endsWith(".woff2") ||
        lowerUrl.endsWith(".ttf")
}

private fun normalizeConsoleLevel(level: String): String =
    when (level.lowercase(Locale.ROOT)) {
        "warn" -> "warning"
        "tip", "log" -> "info"
        else -> level.lowercase(Locale.ROOT)
    }

private fun consoleSeverity(level: String): Int =
    when (normalizeConsoleLevel(level)) {
        "error" -> 0
        "warning" -> 1
        "info" -> 2
        else -> 3
    }

private fun formatDownloadEvent(event: WebDownloadEvent): String =
    buildString {
        appendLine("- Status: ${event.status}")
        appendLine("- Type: ${event.type}")
        appendLine("- File: ${event.fileName}")
        if (!event.url.isNullOrBlank()) {
            appendLine("- URL: ${event.url}")
        }
        if (!event.savedPath.isNullOrBlank()) {
            appendLine("- Saved path: ${event.savedPath}")
        }
        if (!event.error.isNullOrBlank()) {
            append("- Error: ${event.error}")
        }
    }

internal fun StandardBrowserSessionTools.captureSnapshotText(session: BrowserSnapshotSession): String =
    latestSnapshot(session).markdown

internal fun StandardBrowserSessionTools.snapshotNode(
    session: BrowserSnapshotSession,
    ref: String
): BrowserSnapshotNode? {
    val snapshot = session.lastSnapshot ?: latestSnapshot(session)
    return snapshot.nodesByRef[ref]
}

internal fun StandardBrowserSessionTools.captureSnapshotModel(
    session: BrowserSnapshotSession
): BrowserSnapshot {
    val script =
        """
        (function() {
            try {
                const normalize = (value) => String(value || "").replace(/\s+/g, " ").trim();
                const isVisible = (el) => {
                    if (!el || el.nodeType !== 1) return false;
                    const style = window.getComputedStyle(el);
                    if (!style || style.visibility === "hidden" || style.display === "none") return false;
                    const rect = el.getBoundingClientRect();
                    return rect.width > 0 || rect.height > 0;
                };
                const ensureRef = (el, nextRefState) => {
                    let ref = String(el.getAttribute("aria-ref") || "");
                    if (!ref) {
                        ref = "e" + nextRefState.value++;
                        try { el.setAttribute("aria-ref", ref); } catch (_) {}
                    }
                    return ref;
                };
                const roleFor = (el) => {
                    const explicit = normalize(el.getAttribute("role"));
                    if (explicit) return explicit;
                    const tag = String(el.tagName || "").toLowerCase();
                    if (tag === "a") return "link";
                    if (tag === "button") return "button";
                    if (tag === "select") return "combobox";
                    if (tag === "textarea") return "textbox";
                    if (tag === "img") return "img";
                    if (tag === "input") {
                        const type = normalize(el.getAttribute("type")).toLowerCase();
                        if (type === "checkbox") return "checkbox";
                        if (type === "radio") return "radio";
                        if (type === "submit" || type === "button" || type === "reset") return "button";
                        return "textbox";
                    }
                    return "generic";
                };
                const nameFor = (el) => normalize(
                    el.getAttribute("aria-label") ||
                    el.getAttribute("title") ||
                    el.getAttribute("placeholder") ||
                    el.getAttribute("alt") ||
                    el.getAttribute("value") ||
                    el.innerText ||
                    el.textContent
                );
                const valueFor = (el, role, name) => {
                    if (role !== "textbox") return null;
                    const value = normalize(el.value || "");
                    return value && value !== name ? value : null;
                };
                const body = document.body || document.documentElement;
                const title = normalize(document.title) || "untitled";
                const nextRefState = { value: 1 };
                const existingRefNumbers = Array.from(document.querySelectorAll("[aria-ref]"))
                    .map((el) => {
                        const match = /^e(\d+)$/.exec(String(el.getAttribute("aria-ref") || ""));
                        return match ? parseInt(match[1], 10) : 0;
                    })
                    .filter((value) => Number.isFinite(value) && value > 0);
                if (existingRefNumbers.length) {
                    nextRefState.value = Math.max.apply(null, existingRefNumbers) + 1;
                }

                const lines = [];
                const nodes = [];
                lines.push('- document "' + title + '"');

                if (body) {
                    const bodyText = normalize((body.innerText || "").slice(0, 400));
                    const bodyRef = ensureRef(body, nextRefState);
                    const bodyIsActive = !document.activeElement || document.activeElement === body || document.activeElement === document.documentElement;
                    const bodyActiveSuffix = bodyIsActive ? " [active]" : "";
                    const bodyLine = '  - generic' + bodyActiveSuffix + ' [ref=' + bodyRef + ']' + (bodyText ? ': ' + bodyText : "");
                    lines.push(bodyLine);
                    nodes.push({
                        ref: bodyRef,
                        role: "generic",
                        name: "",
                        value: null,
                        active: bodyIsActive,
                        lineText: bodyLine
                    });
                }

                const interactive = Array.from(document.querySelectorAll("a[href],button,input,select,textarea,summary,[role],[tabindex]"))
                    .filter((el) => isVisible(el))
                    .slice(0, 200);
                interactive.forEach((el) => {
                    const ref = ensureRef(el, nextRefState);
                    const role = roleFor(el);
                    const name = nameFor(el);
                    const value = valueFor(el, role, name);
                    const active = document.activeElement === el;
                    const activeSuffix = active ? " [active]" : "";
                    const namePart = name ? ' "' + name + '"' : "";
                    const valuePart = value ? ': ' + value : "";
                    const line = '  - ' + role + namePart + activeSuffix + ' [ref=' + ref + ']' + valuePart;
                    lines.push(line);
                    nodes.push({
                        ref,
                        role,
                        name,
                        value,
                        active,
                        lineText: line
                    });
                });

                return JSON.stringify({
                    ok: true,
                    title,
                    markdown: lines.join("\n"),
                    nodes
                });
            } catch (e) {
                return JSON.stringify({
                    ok: false,
                    title: "untitled",
                    markdown: '- generic [active] [ref=e1]: Snapshot error: ' + String(e),
                    nodes: [
                        {
                            ref: "e1",
                            role: "generic",
                            name: "Snapshot error",
                            value: String(e),
                            active: true,
                            lineText: '- generic [active] [ref=e1]: Snapshot error: ' + String(e)
                        }
                    ],
                    error: String(e)
                });
            }
        })();
        """.trimIndent()
    val json = runJsonScript(session.webView, script, "snapshot_capture_error")
    val markdown = json?.optString("markdown").orEmpty().ifBlank { "- generic [active] [ref=e1]: Snapshot capture error" }
    val title = json?.optString("title").orEmpty().ifBlank { session.pageTitle.ifBlank { "untitled" } }
    val nodes = mutableMapOf<String, BrowserSnapshotNode>()
    val array = json?.optJSONArray("nodes") ?: JSONArray()
    for (index in 0 until array.length()) {
        val node = array.optJSONObject(index) ?: continue
        val ref = node.optString("ref").trim()
        if (ref.isBlank()) {
            continue
        }
        nodes[ref] =
            BrowserSnapshotNode(
                ref = ref,
                role = node.optString("role", "generic"),
                name = node.optString("name"),
                value = node.optString("value").takeIf { it.isNotBlank() },
                isActive = node.optBoolean("active", false),
                lineText = node.optString("lineText").ifBlank { ref }
            )
    }
    return BrowserSnapshot(
        sessionId = session.id,
        generation = nextSnapshotGeneration(),
        title = title,
        markdown = markdown.trim(),
        nodesByRef = nodes
    )
}

internal fun StandardBrowserSessionTools.locatorExpressionForRef(
    session: BrowserSnapshotSession,
    ref: String
): String {
    val node = snapshotNode(session, ref)
    if (node == null) {
        return "page.locator('[aria-ref=${ref}]')"
    }
    val role = node.role.trim()
    val name = node.name.trim()
    return when {
        role.isBlank() || role == "generic" -> "page.locator('[aria-ref=${ref}]')"
        name.isNotBlank() -> "page.getByRole(${quoteJsCode(role)}, { name: ${quoteJsCode(name)} })"
        else -> "page.getByRole(${quoteJsCode(role)})"
    }
}
