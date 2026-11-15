package com.harsh.aisha.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ConversationDao {

    @Insert
    suspend fun insert(message: ConversationEntity): Long

    @Query(
        """
        SELECT * FROM conversation_messages
        ORDER BY createdAtMillis DESC
        LIMIT :limit
        """
    )
    suspend fun getRecent(limit: Int): List<ConversationEntity>

    @Query("SELECT COUNT(*) FROM conversation_messages")
    suspend fun count(): Int

    @Query("DELETE FROM conversation_messages")
    suspend fun deleteAll()
}
