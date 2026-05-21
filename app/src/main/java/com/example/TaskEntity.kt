package com.example

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String = "",
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val priority: String = "Medium", // High, Medium, Low
    val estimatedMinutes: Int = 15,
    val category: String = "General"
)

@Entity(tableName = "agent_messages")
data class AgentMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val agentName: String, // Brainstormer, Prioritizer, Encourager
    val messageText: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, priority DESC, createdAt DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task)

    @Query("UPDATE tasks SET isCompleted = :isCompleted WHERE id = :id")
    suspend fun updateTaskCompletion(id: Int, isCompleted: Boolean)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Int)

    @Query("DELETE FROM tasks")
    suspend fun clearAllTasks()

    @Query("SELECT * FROM agent_messages WHERE agentName = :agentName ORDER BY timestamp ASC")
    fun getAgentMessages(agentName: String): Flow<List<AgentMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgentMessage(message: AgentMessage)

    @Query("DELETE FROM agent_messages WHERE agentName = :agentName")
    suspend fun clearAgentMessages(agentName: String)
}

@Database(entities = [Task::class, AgentMessage::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
}
