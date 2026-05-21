package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

// Brand Design Tokens: "Sleek Interface" (Material 3 Warm Light Theme)
val SleekBg = Color(0xFFFDF8F6)            // Soft warm cream background
val SleekTextPrimary = Color(0xFF1C1B1F)    // Dark charcoal primary text
val SleekTextSecondary = Color(0xFF49454F)  // Slate gray description/subtitle text
val M3Purple = Color(0xFF6750A4)            // Brand Purple Accent
val LavenderContainer = Color(0xFFEADDFF)   // Highlights container
val DeepViolet = Color(0xFF21005D)          // Contrast Deep Purple
val SleekBorder = Color(0xFFCAC4D0)         // Border line
val CleanWhiteCard = Color(0xFFFFFFFF)      // Standard Card surface
val FeaturedCardBg = Color(0xFFF7F2FA)      // Tinted active priority card background

// Priorities indicators colors (Harmonized with light design scheme)
val CoralHigh = Color(0xFFB3261E)           // High priority accent pink-red
val AmberMedium = Color(0xFFD0BCFF)         // Organizer lavender-blue hue
val EmeraldLow = Color(0xFF388E3C)          // Cozy success green

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase
    private lateinit var repository: TaskRepository
    private val viewModel: TodoViewModel by viewModels {
        TodoViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Room Database
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "todo_ai_database"
        ).fallbackToDestructiveMigration().build()

        repository = TaskRepository(database.taskDao())

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SleekBg
                ) {
                    TodoAppScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun TodoAppScreen(viewModel: TodoViewModel) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val selectedAgent by viewModel.selectedAgent.collectAsStateWithLifecycle()
    val messages by viewModel.agentMessages.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf("tasks") } // tasks, ai

    val scope = rememberCoroutineScope()

    // Handle incoming toast updates from VM
    LaunchedEffect(toastMessage) {
        toastMessage?.let { mess ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = mess,
                    duration = SnackbarDuration.Long
                )
            }
            viewModel.clearToast()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = Modifier
            .fillMaxSize()
            .background(SleekBg)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(SleekBg)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Style-heavy Adaptive Header matching Sleek Design spec layout
                HeaderBlock(tasks = tasks)

                // High-Touch Tab Navigator (Tasks / Agent Headquarters)
                TabNavigator(
                    activeTab = currentTab,
                    onTabSelected = { currentTab = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Multi-pane content layout in single screen container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (currentTab == "tasks") {
                        TasksView(
                            tasks = tasks,
                            onToggleCompletion = { id, done -> viewModel.toggleTask(id, done) },
                            onDeleteTask = { id -> viewModel.deleteTask(id) },
                            onDelegateToAgent = { task ->
                                viewModel.selectAgent("Brainstormer")
                                currentTab = "ai"
                                viewModel.sendMessageToAgent("Help me break down this task into sub-tasks: '${task.title}' - ${task.description}")
                            },
                            onClearAll = { viewModel.clearAllTasks() },
                            onShowAddDialog = { showAddTaskDialog = true }
                        )
                    } else {
                        AgentHeadquartersView(
                            selectedAgent = selectedAgent,
                            messages = messages,
                            isAiLoading = isAiLoading,
                            onAgentSelected = { viewModel.selectAgent(it) },
                            onSendMessage = { prompt -> viewModel.sendMessageToAgent(prompt) },
                            onClearHistory = { viewModel.clearAgentHistory() }
                        )
                    }
                }
            }

            // High-Contrast Material Floating Action Button for Tasks in active pill color scheme
            if (currentTab == "tasks") {
                FloatingActionButton(
                    onClick = { showAddTaskDialog = true },
                    containerColor = LavenderContainer,
                    contentColor = DeepViolet,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp)
                        .testTag("add_task_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Task",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }

    // Custom Styled Add Task Dialog
    if (showAddTaskDialog) {
        AddTaskDialog(
            onDismiss = { showAddTaskDialog = false },
            onConfirmAdd = { title, desc, prio, est, cat ->
                viewModel.addTask(title, desc, prio, est, cat)
                showAddTaskDialog = false
            }
        )
    }
}

@Composable
fun HeaderBlock(tasks: List<Task>) {
    val totalTasks = tasks.size
    val completedCount = tasks.count { it.isCompleted }
    val activeCount = totalTasks - completedCount

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // Main Navigation Header Row matching 'Sleek Interface' Design
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = SleekTextPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Tasks",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = SleekTextPrimary
                )
            }

            // Right side Action & Profile section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = SleekTextPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(LavenderContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "JD",
                        color = DeepViolet,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Adaptive Metrics Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            MetricCard(
                label = "Active Tasks",
                value = activeCount.toString(),
                color = CoralHigh,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "Completed",
                value = completedCount.toString(),
                color = EmeraldLow,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "Total List",
                value = totalTasks.toString(),
                color = M3Purple,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun MetricCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CleanWhiteCard)
            .border(1.dp, SleekBorder.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(
                text = label,
                fontSize = 11.sp,
                color = SleekTextSecondary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = value,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = SleekTextPrimary
                )
                Box(
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
    }
}

@Composable
fun TabNavigator(activeTab: String, onTabSelected: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF3EDF7))
            .border(1.dp, SleekBorder.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(4.dp)
    ) {
        val tabConfig = listOf(
            "tasks" to "My Tasks",
            "ai" to "Agent HQ"
        )

        tabConfig.forEach { (key, title) ->
            val isSelected = activeTab == key
            val animatedBg by animateColorAsState(
                targetValue = if (isSelected) Color(0xFFE8DEF8) else Color.Transparent,
                label = "tab_bg"
            )
            val animatedText by animateColorAsState(
                targetValue = if (isSelected) DeepViolet else SleekTextSecondary,
                label = "tab_text"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(animatedBg)
                    .clickable { onTabSelected(key) }
                    .padding(vertical = 10.dp)
                    .testTag("tab_$key"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (key == "tasks") Icons.Default.Checklist else Icons.Default.Psychology,
                        contentDescription = null,
                        tint = animatedText,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = title,
                        color = animatedText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun TasksView(
    tasks: List<Task>,
    onToggleCompletion: (Int, Boolean) -> Unit,
    onDeleteTask: (Int) -> Unit,
    onDelegateToAgent: (Task) -> Unit,
    onClearAll: () -> Unit,
    onShowAddDialog: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        // High-Fidelity Aura AI Assistant Status Banner from 'Sleek Interface' Spec
        AiAgentBanner(tasksCount = tasks.size)

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Priority Tasks",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = SleekTextSecondary,
                letterSpacing = 0.5.sp
            )

            if (tasks.isNotEmpty()) {
                TextButton(
                    onClick = onClearAll,
                    colors = ButtonDefaults.textButtonColors(contentColor = CoralHigh)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear All",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (tasks.isEmpty()) {
            EmptyTasksState(onShowAddDialog = onShowAddDialog)
        } else {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(
                    items = tasks,
                    key = { it.id }
                ) { task ->
                    TaskItemCard(
                        task = task,
                        onToggle = { onToggleCompletion(task.id, it) },
                        onDelete = { onDeleteTask(task.id) },
                        onDelegate = { onDelegateToAgent(task) }
                    )
                }

                item {
                    // Sync Planner Banner matching design spec
                    SyncSchedulerBanner()
                }
            }
        }
    }
}

@Composable
fun AiAgentBanner(tasksCount: Int) {
    val auraAdvice = if (tasksCount == 0) {
        "Your todo deck is clean. Provide an objective or open Agent Headquarters to build an structured roadmap!"
    } else {
        "I have organized your tasks by priority. Focus on high-impact steps first."
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(LavenderContainer)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(DeepViolet)
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "AI AGENT • AURA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepViolet,
                        letterSpacing = 1.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Active",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeepViolet
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "\"$auraAdvice\"",
                    fontSize = 13.sp,
                    color = DeepViolet,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun SyncSchedulerBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(M3Purple.copy(alpha = 0.08f))
            .border(1.dp, SleekBorder.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = M3Purple,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Next Sync: 2:00 PM",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = SleekTextPrimary
                )
            }
            Text(
                text = "Edit Schedule",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = M3Purple,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { /* Active schedule editor placeholder */ }
            )
        }
    }
}

@Composable
fun EmptyTasksState(onShowAddDialog: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 60.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(CleanWhiteCard)
                    .border(1.dp, SleekBorder.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlaylistAddCheck,
                    contentDescription = null,
                    tint = SleekTextSecondary,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Your Todo Deck is Clean",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = SleekTextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Create a task or open the Agent Headquarters to let AI generate sub-tasks automatically!",
                fontSize = 13.sp,
                color = SleekTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onShowAddDialog,
                colors = ButtonDefaults.buttonColors(containerColor = M3Purple, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Add Your First Task", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun TaskItemCard(
    task: Task,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onDelegate: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val priorityColor = when (task.priority) {
        "High" -> CoralHigh
        "Medium" -> AmberMedium
        "Low" -> EmeraldLow
        else -> M3Purple
    }

    val cardBg = if (task.isCompleted) CleanWhiteCard.copy(alpha = 0.7f) else {
        if (task.priority == "High") FeaturedCardBg else CleanWhiteCard
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(
                width = 1.dp,
                color = if (task.priority == "High" && !task.isCompleted) M3Purple.copy(alpha = 0.3f) else SleekBorder.copy(alpha = 0.25f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { expanded = !expanded }
            .padding(14.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            .testTag("task_item_${task.id}")
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Checkbox matching standard "Sleek Interface" outline check box
                IconButton(
                    onClick = { onToggle(!task.isCompleted) },
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("task_checkbox_${task.id}")
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (task.isCompleted) M3Purple else Color.Transparent)
                            .border(2.dp, if (task.isCompleted) M3Purple else SleekTextSecondary, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (task.isCompleted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Done",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                // Core Info Column
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (task.isCompleted) SleekTextSecondary.copy(alpha = 0.7f) else SleekTextPrimary,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (task.isCompleted) "Completed" else "Agent suggested: ${task.estimatedMinutes} min focus",
                        fontSize = 12.sp,
                        color = SleekTextSecondary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Badges Metadata Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Category Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(LavenderContainer.copy(alpha = 0.3f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = task.category,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = DeepViolet,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Priority Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(priorityColor.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = task.priority + " Priority",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = priorityColor
                            )
                        }
                    }
                }

                // Star Indicator matching Sleek HTML
                Icon(
                    imageVector = if (task.priority == "High") Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Priority Star",
                    tint = if (task.priority == "High") M3Purple else SleekTextSecondary.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Delete Action Button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("task_delete_${task.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Task",
                        tint = CoralHigh.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expanding layout (Description & Delegate Actions)
            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = SleekBorder.copy(alpha = 0.3f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                if (task.description.isNotBlank()) {
                    Text(
                        text = task.description,
                        fontSize = 13.sp,
                        color = SleekTextSecondary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                if (!task.isCompleted) {
                    // Task Expander Agent Launcher integration!
                    Button(
                        onClick = onDelegate,
                        colors = ButtonDefaults.buttonColors(containerColor = LavenderContainer),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = DeepViolet,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Delegate to Task Expander Agent",
                                fontSize = 12.sp,
                                color = DeepViolet,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AgentHeadquartersView(
    selectedAgent: String,
    messages: List<AgentMessage>,
    isAiLoading: Boolean,
    onAgentSelected: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var textInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val chatListState = rememberLazyListState()

    // Scroll to bottom as new messages arrive
    LaunchedEffect(messages.size, isAiLoading) {
        if (messages.isNotEmpty()) {
            chatListState.animateScrollToItem(messages.size)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Agent Selector Cards Grid
        AgentSelectorGrid(
            selectedAgent = selectedAgent,
            onAgentSelected = {
                onAgentSelected(it)
                textInput = ""
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Chat Bubble Box Panel
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(CleanWhiteCard)
                .border(1.dp, SleekBorder.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Message List
                LazyColumn(
                    state = chatListState,
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(items = messages) { msg ->
                        ChatBubble(message = msg)
                    }

                    if (isAiLoading) {
                        item {
                            AiLoadingBubble(agentName = selectedAgent)
                        }
                    }
                }

                // Contextual Quick Suggestions based on active agent
                QuickSuggestionsPanel(
                    agent = selectedAgent,
                    onSuggestionClicked = {
                        onSendMessage(it)
                    }
                )

                // Input Bar Row matching Sleek interface styles
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF3EDF7))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    // Start over chat layout
                    IconButton(
                        onClick = onClearHistory,
                        modifier = Modifier.testTag("agent_clear_history")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Clear History",
                            tint = CoralHigh
                        )
                    }

                    TextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = {
                            Text(
                                "Ask ${selectedAgent} Agent...",
                                color = SleekTextSecondary,
                                fontSize = 13.sp
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = SleekTextPrimary,
                            unfocusedTextColor = SleekTextPrimary,
                            cursorColor = M3Purple,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("agent_chat_input"),
                        singleLine = true
                    )

                    IconButton(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                onSendMessage(textInput.trim())
                                textInput = ""
                                keyboardController?.hide()
                            }
                        },
                        enabled = textInput.isNotBlank() && !isAiLoading,
                        modifier = Modifier.testTag("agent_chat_send")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (textInput.isNotBlank() && !isAiLoading) M3Purple else SleekTextSecondary
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

data class AgentItem(val key: String, val name: String, val icon: ImageVector)

@Composable
fun AgentSelectorGrid(selectedAgent: String, onAgentSelected: (String) -> Unit) {
    val agents = listOf(
        AgentItem("Brainstormer", "Task Expander", Icons.Default.AutoAwesome),
        AgentItem("Prioritizer", "Strategic Org", Icons.Default.TrendingUp),
        AgentItem("Encourager", "Focus Coach", Icons.Default.Favorite)
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(items = agents) { agent ->
            val isSelected = selectedAgent == agent.key
            val animatedBg by animateColorAsState(
                targetValue = if (isSelected) LavenderContainer else CleanWhiteCard,
                label = "agent_card_bg"
            )
            val animatedBorderColor by animateColorAsState(
                targetValue = if (isSelected) M3Purple else SleekBorder.copy(alpha = 0.4f),
                label = "agent_card_border"
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(animatedBg)
                    .border(1.dp, animatedBorderColor, RoundedCornerShape(14.dp))
                    .clickable { onAgentSelected(agent.key) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("agent_selector_${agent.key}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = agent.icon,
                        contentDescription = agent.name,
                        tint = if (isSelected) DeepViolet else SleekTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = agent.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) DeepViolet else SleekTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: AgentMessage) {
    val bubbleShape = if (message.isUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
    }

    val bubbleBg = if (message.isUser) M3Purple else Color(0xFFF3EDF7)
    val textColor = if (message.isUser) Color.White else SleekTextPrimary
    val alignment = if (message.isUser) Alignment.End else Alignment.Start

    Column(
        horizontalAlignment = alignment,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(bubbleShape)
                .background(bubbleBg)
                .padding(12.dp)
        ) {
            Text(
                text = decodeMarkdown(message.messageText),
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// Simple cleaner to make response rendering beautiful without rich formatting engine crashes
fun decodeMarkdown(input: String): String {
    var clean = input.replace(Regex("__ADD_TASK_START__(.*?)__ADD_TASK_END__", RegexOption.DOT_MATCHES_ALL), "")
    clean = clean.replace("**", "") // strip bold tags for basic readable display
    return clean.trim()
}

@Composable
fun AiLoadingBubble(agentName: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp))
            .background(Color(0xFFF3EDF7))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(
                color = M3Purple,
                strokeWidth = 2.dp,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = "$agentName Agent is analyzing...",
                color = SleekTextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun QuickSuggestionsPanel(agent: String, onSuggestionClicked: (String) -> Unit) {
    val suggestions = when (agent) {
        "Brainstormer" -> listOf(
            "Break down 'Build an app'",
            "Deconstruct 'Prepare for tech interview'",
            "Goal: 'Redecorate work room'"
        )
        "Prioritizer" -> listOf(
            "Which of my tasks is high stakes?",
            "Eat that frog: what is it?",
            "Help me estimate complexity"
        )
        "Encourager" -> listOf(
            "I'm feeling stuck today",
            "Give me a 5-minute kickstart step",
            "Help me commit to a start"
        )
        else -> emptyList()
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF3EDF7).copy(alpha = 0.5f))
    ) {
        items(items = suggestions) { sug ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CleanWhiteCard)
                    .border(1.dp, SleekBorder.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .clickable { sug?.let { onSuggestionClicked(it) } }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = sug,
                    fontSize = 11.sp,
                    color = SleekTextSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(onDismiss: () -> Unit, onConfirmAdd: (String, String, String, Int, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableStateOf("Medium") }
    var estMinutes by remember { mutableStateOf("25") }
    var selectedCategory by remember { mutableStateOf("Work") }

    val priorityOptions = listOf("High", "Medium", "Low")
    val categoryOptions = listOf("Work", "Personal", "Health", "Brainstorm")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CleanWhiteCard),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("add_task_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Add Objective",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = SleekTextPrimary
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SleekTextPrimary,
                        unfocusedTextColor = SleekTextPrimary,
                        focusedBorderColor = M3Purple,
                        unfocusedBorderColor = SleekBorder
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description (Optional)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SleekTextPrimary,
                        unfocusedTextColor = SleekTextPrimary,
                        focusedBorderColor = M3Purple,
                        unfocusedBorderColor = SleekBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Select Priority Segmented Row
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Select Priority", fontSize = 12.sp, color = SleekTextSecondary, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        priorityOptions.forEach { opt ->
                            val isSelected = selectedPriority == opt
                            val color = when (opt) {
                                "High" -> CoralHigh
                                "Medium" -> M3Purple // Harmonized priority tint
                                "Low" -> EmeraldLow
                                else -> M3Purple
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) color.copy(alpha = 0.2f) else SleekBg)
                                    .border(
                                        width = 1.1.dp,
                                        color = if (isSelected) color else SleekBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedPriority = opt }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = opt,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) color else SleekTextSecondary
                                )
                            }
                        }
                    }
                }

                // Select Category Row
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Select Category", fontSize = 12.sp, color = SleekTextSecondary, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        categoryOptions.take(4).forEach { opt ->
                            val isSelected = selectedCategory == opt
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) LavenderContainer else SleekBg)
                                    .border(
                                        width = 1.1.dp,
                                        color = if (isSelected) M3Purple else SleekBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedCategory = opt }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = opt,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) DeepViolet else SleekTextSecondary
                                )
                            }
                        }
                    }
                }

                // Estimated Duration in Minutes
                OutlinedTextField(
                    value = estMinutes,
                    onValueChange = { estMinutes = it },
                    label = { Text("Duration estimate (mins)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SleekTextPrimary,
                        unfocusedTextColor = SleekTextPrimary,
                        focusedBorderColor = M3Purple,
                        unfocusedBorderColor = SleekBorder
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Actions Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = SleekTextSecondary)
                    }

                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                onConfirmAdd(
                                    title.trim(),
                                    desc.trim(),
                                    selectedPriority,
                                    estMinutes.toIntOrNull() ?: 25,
                                    selectedCategory
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = M3Purple, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        enabled = title.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Add Task")
                    }
                }
            }
        }
    }
}
