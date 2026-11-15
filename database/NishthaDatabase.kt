package com.harsh.aisha.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ConversationEntity::class],
    version = 1,
    exportSchema = false
)
abstract class NishthaDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao

    companion object {

        @Volatile
        private var INSTANCE: NishthaDatabase? = null

        fun getInstance(context: Context): NishthaDatabase {

            return INSTANCE ?: synchronized(this) {

                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NishthaDatabase::class.java,
                    "nishtha_local.db"
                )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
