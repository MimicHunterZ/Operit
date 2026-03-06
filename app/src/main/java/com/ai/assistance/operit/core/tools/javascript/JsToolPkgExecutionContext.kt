package com.ai.assistance.operit.core.tools.javascript

internal class JsToolPkgExecutionContext {
    data class LogSnapshot(
        val pluginIdForLogs: String,
        val functionForLogs: String,
        val scriptContextForLogs: String
    )

    private val tempTextResourceResolverLock = Any()
    @Volatile
    private var tempTextResourceResolver: ((String, String) -> String?)? = null

    fun capture(script: String, functionName: String, params: Map<String, Any?>): LogSnapshot {
        return LogSnapshot(
            pluginIdForLogs = resolvePluginIdForLogs(params, functionName),
            functionForLogs = functionName.trim(),
            scriptContextForLogs = buildScriptContextSnippet(script, functionName)
        )
    }

    fun hasActivePluginIdForLogs(snapshot: LogSnapshot?): Boolean {
        return !snapshot?.pluginIdForLogs.isNullOrBlank()
    }

    fun withPluginTag(snapshot: LogSnapshot?, message: String): String {
        val pluginId = compactPluginId(snapshot?.pluginIdForLogs.orEmpty())
        val normalized =
            message.replace(Regex("""\[plugin=([^\]]+)]""")) { matchResult ->
                "[${compactPluginId(matchResult.groupValues[1])}]"
            }
        if (normalized.startsWith("[$pluginId] ")) {
            return normalized
        }
        return "[$pluginId] $normalized"
    }

    fun withCodeContext(snapshot: LogSnapshot?, message: String): String {
        val functionName = snapshot?.functionForLogs?.trim().orEmpty()
        val scriptContext = snapshot?.scriptContextForLogs?.trim().orEmpty()
        if (functionName.isBlank() && scriptContext.isBlank()) {
            return message
        }

        val builder = StringBuilder(message)
        if (functionName.isNotBlank()) {
            builder.append("\nExecution Function: ").append(functionName)
        }
        if (scriptContext.isNotBlank()) {
            builder.append("\nCode Context:\n").append(scriptContext)
        }
        return builder.toString()
    }

    fun <T> withTemporaryTextResourceResolver(
        resolver: (String, String) -> String?,
        block: () -> T
    ): T {
        synchronized(tempTextResourceResolverLock) {
            val previous = tempTextResourceResolver
            tempTextResourceResolver = resolver
            return try {
                block()
            } finally {
                tempTextResourceResolver = previous
            }
        }
    }

    fun resolveTemporaryTextResource(
        packageNameOrSubpackageId: String,
        resourcePath: String,
        onResolverFailure: (Exception) -> Unit
    ): String? {
        val resolver = tempTextResourceResolver ?: return null
        return try {
            resolver(packageNameOrSubpackageId, resourcePath)
        } catch (e: Exception) {
            onResolverFailure(e)
            null
        }
    }

    fun hasTemporaryTextResourceResolver(): Boolean {
        return tempTextResourceResolver != null
    }

    private fun resolvePluginIdForLogs(
        params: Map<String, Any?>,
        functionName: String
    ): String {
        val explicitPluginId =
            sequenceOf(
                params["__operit_plugin_id"],
                params["pluginId"],
                params["hookId"]
            )
                .mapNotNull { it?.toString()?.trim() }
                .firstOrNull { it.isNotBlank() }
        if (!explicitPluginId.isNullOrBlank()) {
            return explicitPluginId
        }

        val toolPkgId =
            sequenceOf(
                params["toolPkgId"],
                params["__operit_ui_package_name"],
                params["__operit_ui_toolpkg_id"],
                params["packageName"]
            )
                .mapNotNull { it?.toString()?.trim() }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()

        val normalizedFunction = functionName.trim().ifBlank { "runtime" }
        return if (toolPkgId.isNotBlank()) {
            "$normalizedFunction:$toolPkgId"
        } else {
            normalizedFunction
        }
    }

    private fun compactPluginId(rawPluginId: String): String {
        val normalized = rawPluginId.trim()
        if (normalized.isBlank()) {
            return "runtime"
        }
        val splitIndex = normalized.indexOf(':')
        if (splitIndex <= 0 || splitIndex >= normalized.lastIndex) {
            return compactPluginSegment(normalized)
        }
        val head = compactPluginSegment(normalized.substring(0, splitIndex))
        val tail = compactPluginSegment(normalized.substring(splitIndex + 1))
        return if (head.isBlank()) tail else "$head:$tail"
    }

    private fun compactPluginSegment(rawSegment: String): String {
        val fallback = rawSegment.trim()
        if (fallback.isBlank()) {
            return "runtime"
        }

        var segment = fallback
        if (segment.contains('/')) {
            segment = segment.substringAfterLast('/')
        }
        if (segment.contains('.')) {
            segment = segment.substringAfterLast('.')
        }

        segment =
            segment
                .removeSuffix("_bundle")
                .removeSuffix(".toolpkg")
                .trim()

        return if (segment.isNotBlank()) segment else fallback
    }

    private fun buildScriptContextSnippet(script: String, functionName: String): String {
        val normalizedScript = script.replace("\r\n", "\n")
        if (normalizedScript.isBlank()) {
            return ""
        }

        val lines = normalizedScript.split('\n')
        if (lines.isEmpty()) {
            return ""
        }

        val normalizedFunction = functionName.trim()
        val escapedFunction = Regex.escape(normalizedFunction)
        val anchorPatterns =
            if (normalizedFunction.isBlank()) {
                emptyList()
            } else {
                listOf(
                    Regex("""\bfunction\s+$escapedFunction\s*\("""),
                    Regex("""\b$escapedFunction\s*:\s*function\b"""),
                    Regex("""\b$escapedFunction\s*=\s*(?:async\s*)?\("""),
                    Regex("""\b$escapedFunction\s*=\s*(?:async\s+)?function\b"""),
                    Regex("""\bexports\.$escapedFunction\b"""),
                    Regex("""\bmodule\.exports\.$escapedFunction\b"""),
                    Regex("""\b$escapedFunction\s*\(""")
                )
            }

        var anchorLineIndex = -1
        if (anchorPatterns.isNotEmpty()) {
            for ((index, lineText) in lines.withIndex()) {
                if (anchorPatterns.any { it.containsMatchIn(lineText) }) {
                    anchorLineIndex = index
                    break
                }
            }
        }

        val around = 8
        val startIndex: Int
        val endIndex: Int
        if (anchorLineIndex >= 0) {
            startIndex = (anchorLineIndex - around).coerceAtLeast(0)
            endIndex = (anchorLineIndex + around).coerceAtMost(lines.lastIndex)
        } else {
            startIndex = 0
            endIndex = (lines.size - 1).coerceAtMost(23)
        }

        val builder = StringBuilder()
        if (anchorLineIndex >= 0) {
            builder
                .append("anchorLine=")
                .append(anchorLineIndex + 1)
                .append('\n')
        } else {
            builder.append("anchorLine=unknown\n")
        }

        for (i in startIndex..endIndex) {
            val lineNumber = (i + 1).toString().padStart(4, ' ')
            builder.append(lineNumber).append(" | ").append(lines[i]).append('\n')
        }

        val snippet = builder.toString().trimEnd()
        val maxChars = 2200
        return if (snippet.length <= maxChars) {
            snippet
        } else {
            snippet.take(maxChars) + "\n...[truncated]"
        }
    }
}
