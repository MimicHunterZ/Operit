package com.ai.assistance.operit.integrations.externalchat

import android.content.Context
import com.ai.assistance.operit.core.tools.ChatListResultData
import com.ai.assistance.operit.core.tools.MessageSendResultData
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardChatManagerTool
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.util.AppLogger

class ExternalChatRequestExecutor(context: Context) {

    private val appContext = context.applicationContext

    suspend fun execute(request: ExternalChatRequest): ExternalChatResult {
        val requestId = request.requestId?.trim()?.takeIf { it.isNotBlank() }
        val message = request.message?.trim()
        if (message.isNullOrBlank()) {
            return ExternalChatResult(
                requestId = requestId,
                success = false,
                error = "Missing extra: message"
            )
        }

        return try {
            val chatTool = StandardChatManagerTool(appContext)

            if (request.showFloating) {
                val params = mutableListOf<ToolParameter>()
                if (request.autoExitAfterMs > 0) {
                    params += ToolParameter(name = "timeout_ms", value = request.autoExitAfterMs.toString())
                }
                chatTool.startChatService(
                    AITool(
                        name = "start_chat_service",
                        parameters = params
                    )
                )
            }

            if (!request.createNewChat && request.chatId.isNullOrBlank() && !request.createIfNone) {
                val listResult = chatTool.listChats(AITool(name = "list_chats"))
                val currentChatId = (listResult.result as? ChatListResultData)?.currentChatId
                if (currentChatId.isNullOrBlank()) {
                    return ExternalChatResult(
                        requestId = requestId,
                        success = false,
                        error = "No current chat and create_if_none=false"
                    )
                }
            }

            if (request.createNewChat) {
                val params = mutableListOf<ToolParameter>()
                request.group?.trim()?.takeIf { it.isNotBlank() }?.let {
                    params += ToolParameter(name = "group", value = it)
                }
                chatTool.createNewChat(
                    AITool(
                        name = "create_new_chat",
                        parameters = params
                    )
                )
            }

            val sendParams = mutableListOf(
                ToolParameter(name = "message", value = message)
            )
            if (!request.createNewChat) {
                request.chatId?.trim()?.takeIf { it.isNotBlank() }?.let {
                    sendParams += ToolParameter(name = "chat_id", value = it)
                }
            }

            val sendResult = chatTool.sendMessageToAI(
                AITool(
                    name = "send_message_to_ai",
                    parameters = sendParams
                )
            )
            val resultData = sendResult.result as? MessageSendResultData

            if (request.stopAfter) {
                chatTool.stopChatService(AITool(name = "stop_chat_service"))
            }

            ExternalChatResult(
                requestId = requestId,
                success = sendResult.success,
                chatId = resultData?.chatId?.takeIf { it.isNotBlank() },
                aiResponse = resultData?.aiResponse?.takeIf { it.isNotBlank() },
                error = sendResult.error?.takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to execute external chat request", e)
            ExternalChatResult(
                requestId = requestId,
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    companion object {
        private const val TAG = "ExternalChatExecutor"
    }
}
