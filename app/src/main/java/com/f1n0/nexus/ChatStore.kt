package com.f1n0.nexus

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey val id: String,
    val title: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    @ColumnInfo(defaultValue = "'default'") val chatId: String = "default",
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY id ASC")
    fun observeChat(chatId: String): Flow<List<ChatMessage>>

    @Insert
    suspend fun insert(message: ChatMessage)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteForChat(chatId: String)

    @Query("DELETE FROM messages")
    suspend fun clear()
}

@Dao
interface ChatSessionDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ChatSession>>

    @Insert
    suspend fun insert(session: ChatSession)

    @Query("UPDATE chat_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chat_sessions SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [ChatMessage::class, ChatSession::class], version = 2, exportSchema = false)
abstract class NexusDatabase : RoomDatabase() {
    abstract fun messages(): ChatMessageDao
    abstract fun sessions(): ChatSessionDao

    companion object {
        @Volatile private var instance: NexusDatabase? = null
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN chatId TEXT NOT NULL DEFAULT 'default'")
                db.execSQL("CREATE TABLE IF NOT EXISTS chat_sessions (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("INSERT OR IGNORE INTO chat_sessions (id, title, updatedAt) SELECT 'default', 'Conversa anterior', COALESCE(MAX(createdAt), 0) FROM messages")
            }
        }

        fun get(context: Context): NexusDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, NexusDatabase::class.java, "nexus-chat.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
