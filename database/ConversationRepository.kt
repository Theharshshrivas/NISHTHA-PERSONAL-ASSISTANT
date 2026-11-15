package com.harsh.aisha.database

import android.content.Context

data class ConversationMessage(
    val id: Long,
    val userText: String,
    val assistantText: String,
    val createdAtMillis: Long
)

class ConversationRepository(context: Context) {

    private val dao = NishthaDatabase
        .getInstance(context)
        .conversationDao()

    suspend fun save(
        userText: String,
        assistantText: String
    ): Long {

        require(userText.isNotBlank())
        require(assistantText.isNotBlank())

        val entity = ConversationEntity(
            userTextEncrypted = SecureTextCipher.encrypt(userText),
            assistantTextEncrypted = SecureTextCipher.encrypt(assistantText),
            createdAtMillis = System.currentTimeMillis()
        )

        return dao.insert(entity)
    }

    suspend fun getRecent(
        limit: Int = 50
    ): List<ConversationMessage> {

        require(limit in 1..500)

        return dao.getRecent(limit).map { entity ->

            ConversationMessage(
                id = entity.id,
                userText = SecureTextCipher.decrypt(
                    entity.userTextEncrypted
                ),
                assistantText = SecureTextCipher.decrypt(
                    entity.assistantTextEncrypted
                ),
                createdAtMillis = entity.createdAtMillis
            )
        }
    }

    suspend fun getCount(): Int {
        return dao.count()
    }

    suspend fun clearHistory() {
        dao.deleteAll()
    }
}
