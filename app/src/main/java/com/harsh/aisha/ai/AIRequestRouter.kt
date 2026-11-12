
package com.harsh.aisha.ai

import com.harsh.aisha.privacy.OnlineApprovalGate
import com.harsh.aisha.privacy.OnlineApprovalToken

interface LocalInferenceEngine {
    val isModelReady: Boolean

    suspend fun generate(prompt: String): String
}

interface OnlineInferenceProvider {
    val providerName: String

    suspend fun generate(approvedPrompt: String): String
}

enum class ResponseSource {
    LOCAL_COMMANDS,
    LOCAL_MODEL,
    ONLINE_MODEL,
    OFFLINE_FALLBACK
}

data class AssistantAnswer(
    val text: String,
    val source: ResponseSource
)

/**
 * Local-first AI routing.
 *
 * No automatic chat-history or memory upload.
 * Online requests require a one-use approval token.
 */
class AIRequestRouter(
    private val commandEngine: LocalCommandEngine,
    private val localModel: LocalInferenceEngine?,
    private val onlineProvider: OnlineInferenceProvider?,
    private val approvalGate: OnlineApprovalGate
) {

    suspend fun respond(
        userText: String,
        onlineApproval: OnlineApprovalToken? = null
    ): AssistantAnswer {

        val cleaned = userText.trim()

        if (cleaned.isBlank()) {
            return AssistantAnswer(
                "Please type or speak a message first.",
                ResponseSource.LOCAL_COMMANDS
            )
        }

        // Online is never selected automatically.
        if (onlineApproval != null) {
            val approvedText = approvalGate.consume(onlineApproval)

            if (approvedText == null || approvedText != cleaned) {
                return AssistantAnswer(
                    "Online approval invalid or expired. Nothing was sent.",
                    ResponseSource.OFFLINE_FALLBACK
                )
            }

            val provider = onlineProvider
                ?: return AssistantAnswer(
                    "Online AI provider is not configured. Nothing was sent.",
                    ResponseSource.OFFLINE_FALLBACK
                )

            return try {
                AssistantAnswer(
                    provider.generate(approvedText),
                    ResponseSource.ONLINE_MODEL
                )
            } catch (_: Exception) {
                AssistantAnswer(
                    "Online AI request failed. Your local data was not automatically shared.",
                    ResponseSource.OFFLINE_FALLBACK
                )
            }
        }

        val localResult = commandEngine.process(cleaned)

        if (localResult.handled) {
            return AssistantAnswer(
                localResult.text,
                ResponseSource.LOCAL_COMMANDS
            )
        }

        val model = localModel

        if (model != null && model.isModelReady) {
            return try {
                AssistantAnswer(
                    model.generate(cleaned),
                    ResponseSource.LOCAL_MODEL
                )
            } catch (_: Exception) {
                AssistantAnswer(
                    "Local model error. No online request was made.",
                    ResponseSource.OFFLINE_FALLBACK
                )
            }
        }

        return AssistantAnswer(
            localResult.text,
            ResponseSource.OFFLINE_FALLBACK
        )
    }
}
