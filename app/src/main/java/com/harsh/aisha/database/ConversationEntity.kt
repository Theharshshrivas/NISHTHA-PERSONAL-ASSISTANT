package com.harsh.aisha.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_messages")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val userTextEncrypted: String,
    val assistantTextEncrypted: String,
    val createdAtMillis: Long
)
