
package com.harsh.aisha.privacy

import java.util.UUID

data class OnlineRequestPreview(
    val id: String,
    val providerName: String,
    val exactContent: String,
    val createdAtMillis: Long
)

class OnlineApprovalToken internal constructor(
    internal val id: String
)

/**
 * In-memory, one-use approval gate.
 *
 * The caller must display OnlineRequestPreview to the user
 * before calling approve().
 */
class OnlineApprovalGate {

    private data class PendingRequest(
        val preview: OnlineRequestPreview
    )

    private data class ApprovedRequest(
        val content: String,
        val expiresAtMillis: Long
    )

    private val pending = mutableMapOf<String, PendingRequest>()
    private val approved = mutableMapOf<String, ApprovedRequest>()

    @Synchronized
    fun prepare(
        exactContent: String,
        providerName: String
    ): OnlineRequestPreview? {
        val content = exactContent.trim()
        val provider = providerName.trim()

        if (content.isBlank() || provider.isBlank()) return null

        val preview = OnlineRequestPreview(
            id = UUID.randomUUID().toString(),
            providerName = provider,
            exactContent = content,
            createdAtMillis = System.currentTimeMillis()
        )

        pending[preview.id] = PendingRequest(preview)
        removeExpired()
        return preview
    }

    /**
     * Call only after the user has reviewed the exact preview
     * and explicitly tapped an approval control.
     */
    @Synchronized
    fun approve(previewId: String): OnlineApprovalToken? {
        removeExpired()

        val request = pending.remove(previewId) ?: return null
        val tokenId = UUID.randomUUID().toString()

        approved[tokenId] = ApprovedRequest(
            content = request.preview.exactContent,
            expiresAtMillis = System.currentTimeMillis() + TOKEN_LIFETIME_MS
        )

        return OnlineApprovalToken(tokenId)
    }

    @Synchronized
    fun cancel(previewId: String) {
        pending.remove(previewId)
    }

    /**
     * Consumes the token. A token cannot be reused.
     */
    @Synchronized
    fun consume(token: OnlineApprovalToken): String? {
        removeExpired()
        return approved.remove(token.id)?.content
    }

    @Synchronized
    private fun removeExpired() {
        val now = System.currentTimeMillis()

        pending.entries.removeAll { (_, request) ->
            now - request.preview.createdAtMillis > TOKEN_LIFETIME_MS
        }

        approved.entries.removeAll { (_, request) ->
            request.expiresAtMillis < now
        }
    }

    companion object {
        private const val TOKEN_LIFETIME_MS = 2 * 60 * 1000L
    }
}
