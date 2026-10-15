package com.f1n0.nexus

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM messages ORDER BY id ASC")
    fun observeAll(): Flow<List<ChatMessage>>

    @Insert
    suspend fun insert(message: ChatMessage)

    @Query("DELETE FROM messages")
    suspend fun clear()
}

@Database(entities = [ChatMessage::class], version = 1, exportSchema = false)
abstract class NexusDatabase : RoomDatabase() {
    abstract fun messages(): ChatMessageDao

    companion object {
        @Volatile private var instance: NexusDatabase? = null
        fun get(context: Context): NexusDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, NexusDatabase::class.java, "nexus-chat.db"
            ).build().also { instance = it }
        }
    }
}
