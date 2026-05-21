package com.example

import kotlinx.coroutines.flow.Flow

class TaskRepository(private val taskDao: TaskDao) {
    val allTasks: Flow<List<Task>> = taskDao.getAllTasks()

    suspend fun insertTask(task: Task) {
        taskDao.insertTask(task)
    }

    suspend fun updateTaskCompletion(id: Int, isCompleted: Boolean) {
        taskDao.updateTaskCompletion(id, isCompleted)
    }

    suspend fun deleteTaskById(id: Int) {
        taskDao.deleteTaskById(id)
    }

    suspend fun clearAllTasks() {
        taskDao.clearAllTasks()
    }

    fun getAgentMessages(agentName: String): Flow<List<AgentMessage>> {
        return taskDao.getAgentMessages(agentName)
    }

    suspend fun insertAgentMessage(message: AgentMessage) {
        taskDao.insertAgentMessage(message)
    }

    suspend fun clearAgentMessages(agentName: String) {
        taskDao.clearAgentMessages(agentName)
    }
}
