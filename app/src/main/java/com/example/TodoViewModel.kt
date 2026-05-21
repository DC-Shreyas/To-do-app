package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.Moshi
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@JsonClass(generateAdapter = true)
data class TaskSuggestion(
    val title: String,
    val description: String = "",
    val priority: String = "Medium",
    val category: String = "General"
)

class TodoViewModel(private val repository: TaskRepository) : ViewModel() {

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val _selectedAgent = MutableStateFlow("Brainstormer") // Brainstormer, Prioritizer, Encourager
    val selectedAgent: StateFlow<String> = _selectedAgent.asStateFlow()

    private val _agentMessages = MutableStateFlow<List<AgentMessage>>(emptyList())
    val agentMessages: StateFlow<List<AgentMessage>> = _agentMessages.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val moshi = Moshi.Builder().build()
    private val suggestionAdapter = moshi.adapter(TaskSuggestion::class.java)

    init {
        // Collect tasks in database
        viewModelScope.launch {
            repository.allTasks.collectLatest { taskList ->
                _tasks.value = taskList
            }
        }

        // Collect messages when selected agent changes
        viewModelScope.launch {
            _selectedAgent.collectLatest { agent ->
                repository.getAgentMessages(agent).collectLatest { messages ->
                    _agentMessages.value = messages
                }
            }
        }
    }

    fun selectAgent(agentName: String) {
        _selectedAgent.value = agentName
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun addTask(title: String, description: String, priority: String, estimatedMinutes: Int, category: String) {
        viewModelScope.launch {
            repository.insertTask(
                Task(
                    title = title,
                    description = description,
                    priority = priority,
                    estimatedMinutes = estimatedMinutes,
                    category = category
                )
            )
        }
    }

    fun toggleTask(id: Int, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.updateTaskCompletion(id, isCompleted)
        }
    }

    fun deleteTask(id: Int) {
        viewModelScope.launch {
            repository.deleteTaskById(id)
        }
    }

    fun clearAllTasks() {
        viewModelScope.launch {
            repository.clearAllTasks()
        }
    }

    fun clearAgentHistory() {
        val currentAgent = _selectedAgent.value
        viewModelScope.launch {
            repository.clearAgentMessages(currentAgent)
            // Add a warm introduction message after clearing
            addWelcomeMessageForAgent(currentAgent)
        }
    }

    suspend fun addWelcomeMessageForAgent(agent: String) {
        val welcomeText = when (agent) {
            "Brainstormer" -> "Hi! I am the **Task Expander Agent**. Tell me what you want to achieve or give me a broad goal (like 'learn to paint' or 'plan weekend getaway'), and I will help you break it down and automatically insert detailed sub-tasks into your list!"
            "Prioritizer" -> "Welcome! I am the **Strategic Organizer Agent**. I can analyze your current tasks and suggest an optimal, stress-free order, estimated task times, or help you execute 'Eat That Frog' strategy!"
            "Encourager" -> "Hello companion! I am your **Mindset & Focus Coach**. Struggling to start or feeling overwhelmed? Let me know which task is blocking you, and I will outline a laughably simple micro-step to establish momentum."
            else -> "Hello! How can I assist you with your tasks today?"
        }
        repository.insertAgentMessage(
            AgentMessage(
                agentName = agent,
                messageText = welcomeText,
                isUser = false
            )
        )
    }

    fun sendMessageToAgent(prompt: String) {
        if (prompt.isBlank()) return

        val currentAgent = _selectedAgent.value
        viewModelScope.launch {
            // Save User message
            repository.insertAgentMessage(
                AgentMessage(
                    agentName = currentAgent,
                    messageText = prompt,
                    isUser = true
                )
            )

            _isAiLoading.value = true

            try {
                // Compile conversation history
                val conversationHistory = repository.getAgentMessages(currentAgent).first()
                val currentTasksList = _tasks.value

                // Format Gemini prompt
                val formattedPrompt = buildString {
                    append("User Message: ")
                    append(prompt)
                    append("\n\n")
                    append("--- CONTEXTUAL SYSTEM STATE ---\n")
                    append("Here is the user's real-time task list in the app database:\n")
                    if (currentTasksList.isEmpty()) {
                        append("- User has no tasks currently.\n")
                    } else {
                        currentTasksList.forEachIndexed { index, task ->
                            append("${index + 1}. [${if (task.isCompleted) "COMPLETED" else "ACTIVE"}] '${task.title}' - Priority: ${task.priority}, Est Time: ${task.estimatedMinutes}m, Category: ${task.category}. Description: ${task.description}\n")
                        }
                    }
                    append("\n--- CONVERSATION RECENT MESSAGES ---\n")
                    conversationHistory.takeLast(10).forEach { msg ->
                        append("${if (msg.isUser) "User" else "AI"}: ${msg.messageText}\n")
                    }
                }

                // Call Gemini REST API
                val responseText = callGeminiApi(currentAgent, formattedPrompt)

                // Save Agent response to local room database
                repository.insertAgentMessage(
                    AgentMessage(
                        agentName = currentAgent,
                        messageText = responseText,
                        isUser = false
                    )
                )

                // Process special agent actions (like auto-adding parsed suggested tasks)
                processAgentActions(responseText)

            } catch (e: Exception) {
                repository.insertAgentMessage(
                    AgentMessage(
                        agentName = currentAgent,
                        messageText = "My connection seems to be interrupted. Please make sure your Gemini API key is configured properly in the AI Studio Secrets panel. Error: ${e.message}",
                        isUser = false
                    )
                )
            } finally {
                _isAiLoading.value = false
            }
        }
    }

    private suspend fun callGeminiApi(agent: String, prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "API key is not configured. Please add your GEMINI_API_KEY to the secrets panel in the AI Studio sidebar."
        }

        val systemPrompt = when (agent) {
            "Brainstormer" -> """
                You are the Todo Task Expander Agent, an advanced AI coordinator. Your main capability is to help the user brainstorm detailed, concrete, actionable steps for plans or milestones.
                Engage in highly motivating, constructive chat. Identify goals, brainstorm detailed procedures, and suggest individual tasks.
                
                IMPORTANT: When you suggest specific tasks/sub-tasks that the user can add to their list, format them inside exact __ADD_TASK_START__ and __ADD_TASK_END__ markers as valid JSON objects. The JSON schema must strictly match:
                {
                   "title": "Task title (max 40 chars)",
                   "description": "Short explanation of the step",
                   "priority": "High" or "Medium" or "Low",
                   "category": "Brainstorm" or "Work" or "Personal" or "Health"
                }
                
                Example inside your reply:
                To get started, we should prepare your itinerary. Let me add this to your board:
                __ADD_TASK_START__
                {
                  "title": "Draft 3-Day Itinerary",
                  "description": "Outline morning, afternoon, and night plans.",
                  "priority": "High",
                  "category": "Brainstorm"
                }
                __ADD_TASK_END__
                
                You can insert multiple task suggestions in a single message! They will be automatically parsed from your string and saved directly to the user's database. Be friendly and collaborative!
            """.trimIndent()

            "Prioritizer" -> """
                You are the Strategic Organizer Agent. Your job is to help users prioritize their work, find dependencies, categorize thoughts, and deal with cognitive overload.
                Analyze the user's current task list carefully.
                Help them choose the "One Thing" or the "Eat That Frog" task. Suggest clear restructurings or times.
                
                If you suggest a brand-new task to optimize their work, you can also use:
                __ADD_TASK_START__
                {
                  "title": "Suggested Task Title",
                  "description": "Explanation",
                  "priority": "High",
                  "category": "Priority"
                }
                __ADD_TASK_END__
                
                Keep your messages focused, visual (use bullet points), and highly practical.
            """.trimIndent()

            "Encourager" -> """
                You are the Mindset & Focus Coach Agent. Help users build momentum, tackle resistance, celebrate milestones, and beat procrastination.
                Never make them feel guilty about incomplete tasks. Normalize procrastination.
                Offer tiny, laughably small micro-steps for their tasks to overcome resistance. E.g., 'If your task is "Clean the kitchen", your micro-step is "Pick up one spoon"'.
                Suggest short, direct actions they can do in 5 minutes.
                Use warm, friendly, wise, and empathetic wording. Keep responses relatively brief and highly soothing.
            """.trimIndent()

            else -> "You are a helpful general task coordinator agent."
        }

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
                )
            ),
            generationConfig = GeminiGenerationConfig(temperature = 0.7),
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = systemPrompt))
            )
        )

        val response = RetrofitClient.service.generateContent(apiKey, request)
        response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: "The AI agent returned an empty response."
    }

    private suspend fun processAgentActions(text: String) {
        val regex = Regex("__ADD_TASK_START__(.*?)__ADD_TASK_END__", RegexOption.DOT_MATCHES_ALL)
        val matches = regex.findAll(text)
        var addedCount = 0

        for (match in matches) {
            val jsonString = match.groupValues[1].trim()
            try {
                val suggestion = withContext(Dispatchers.Default) {
                    suggestionAdapter.fromJson(jsonString)
                }
                if (suggestion != null) {
                    repository.insertTask(
                        Task(
                            title = suggestion.title,
                            description = suggestion.description,
                            priority = suggestion.priority,
                            estimatedMinutes = when (suggestion.priority) {
                                "High" -> 45
                                "Medium" -> 25
                                "Low" -> 15
                                else -> 20
                            },
                            category = suggestion.category
                        )
                    )
                    addedCount++
                }
            } catch (e: Exception) {
                // Ignore malformed parsed tasks
            }
        }

        if (addedCount > 0) {
            _toastMessage.value = "AI Agent successfully added $addedCount task(s) directly to your todo list!"
        }
    }
}

class TodoViewModelFactory(private val repository: TaskRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TodoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TodoViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
