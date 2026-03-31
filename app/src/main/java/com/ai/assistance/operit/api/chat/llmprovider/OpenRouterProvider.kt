package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.util.AppLogger
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import org.json.JSONObject

/**
 * OpenRouter provider.
 *
 * OpenRouter chat completions are largely OpenAI-compatible, but reasoning is controlled via
 * the unified `reasoning` object instead of the app's generic `enableThinking` toggle.
 *
 * This provider keeps the shared OpenAI request/response handling while applying OpenRouter's
 * request-body conventions and default headers.
 */
class OpenRouterProvider(
    apiEndpoint: String,
    apiKeyProvider: ApiKeyProvider,
    modelName: String,
    client: OkHttpClient,
    customHeaders: Map<String, String> = emptyMap(),
    providerType: ApiProviderType = ApiProviderType.OPENROUTER,
    supportsVision: Boolean = false,
    supportsAudio: Boolean = false,
    supportsVideo: Boolean = false,
    enableToolCall: Boolean = false
) : OpenAIProvider(
    apiEndpoint = apiEndpoint,
    apiKeyProvider = apiKeyProvider,
    modelName = modelName,
    client = client,
    customHeaders = mergeOpenRouterHeaders(customHeaders),
    providerType = providerType,
    supportsVision = supportsVision,
    supportsAudio = supportsAudio,
    supportsVideo = supportsVideo,
    enableToolCall = enableToolCall
) {

    override fun createRequestBody(
        context: Context,
        message: String,
        chatHistory: List<Pair<String, String>>,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean,
        stream: Boolean,
        availableTools: List<ToolPrompt>?,
        preserveThinkInHistory: Boolean
    ): RequestBody {
        val baseRequestBodyJson = super.createRequestBodyInternal(
            context,
            message,
            chatHistory,
            modelParameters,
            stream,
            availableTools,
            preserveThinkInHistory
        )
        val jsonObject = JSONObject(baseRequestBodyJson)

        applyOpenRouterReasoning(jsonObject, enableThinking)

        val logJson = JSONObject(jsonObject.toString())
        if (logJson.has("tools")) {
            val toolsArray = logJson.getJSONArray("tools")
            logJson.put("tools", "[${toolsArray.length()} tools omitted for brevity]")
        }
        val sanitizedLogJson = sanitizeImageDataForLogging(logJson)
        logLargeString(
            "OpenRouterProvider",
            sanitizedLogJson.toString(4),
            "Final OpenRouter request body: "
        )

        return createJsonRequestBody(jsonObject.toString())
    }

    private fun applyOpenRouterReasoning(
        requestJson: JSONObject,
        enableThinking: Boolean
    ) {
        if (!enableThinking) {
            return
        }

        val reasoningObject = requestJson.optJSONObject("reasoning")
        when {
            reasoningObject == null && !requestJson.has("reasoning") -> {
                requestJson.put("reasoning", JSONObject().put("enabled", true))
                AppLogger.d(
                    "OpenRouterProvider",
                    "OpenRouter thinking enabled via reasoning.enabled=true"
                )
            }

            reasoningObject == null && requestJson.isNull("reasoning") -> {
                requestJson.put("reasoning", JSONObject().put("enabled", true))
                AppLogger.d(
                    "OpenRouterProvider",
                    "OpenRouter thinking enabled via reasoning.enabled=true (replaced null reasoning)"
                )
            }

            reasoningObject != null -> {
                val hasExplicitReasoningControl =
                    reasoningObject.has("enabled") ||
                        reasoningObject.has("max_tokens") ||
                        reasoningObject.has("effort")

                if (!hasExplicitReasoningControl) {
                    reasoningObject.put("enabled", true)
                    requestJson.put("reasoning", reasoningObject)
                    AppLogger.d(
                        "OpenRouterProvider",
                        "OpenRouter reasoning object augmented with enabled=true"
                    )
                } else {
                    AppLogger.d(
                        "OpenRouterProvider",
                        "Preserving caller-supplied OpenRouter reasoning object"
                    )
                }
            }

            else -> {
                AppLogger.w(
                    "OpenRouterProvider",
                    "Skipping automatic thinking injection because reasoning is not an object"
                )
            }
        }
    }

    companion object {
        private const val DEFAULT_HTTP_REFERER = "ai.assistance.operit"
        private const val DEFAULT_X_TITLE = "Assistance App"

        private fun mergeOpenRouterHeaders(customHeaders: Map<String, String>): Map<String, String> {
            val merged = linkedMapOf<String, String>()

            if (customHeaders.keys.none { it.equals("HTTP-Referer", ignoreCase = true) }) {
                merged["HTTP-Referer"] = DEFAULT_HTTP_REFERER
            }
            if (customHeaders.keys.none { it.equals("X-Title", ignoreCase = true) }) {
                merged["X-Title"] = DEFAULT_X_TITLE
            }

            merged.putAll(customHeaders)
            return merged
        }
    }
}
