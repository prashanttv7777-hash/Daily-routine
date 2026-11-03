package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.DailyTask
import com.example.data.model.RoutineItem
import com.example.data.preferences.AppThemeMode
import com.example.data.preferences.ThemePreferencesRepository
import com.example.data.repository.DailyLifeRepository
import com.example.notifications.TaskReminderScheduler
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.components.RoutineEditorSheet
import com.example.ui.components.TaskEditorSheet
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ScheduleScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.theme.MyDailyLifeTheme
import com.example.ui.viewmodel.DailyLifeViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val prefsRepo = remember(context) { ThemePreferencesRepository(context) }
            val prefs by prefsRepo.preferencesFlow.collectAsStateWithLifecycle(
                initialValue = com.example.data.preferences.UserPreferences()
            )

            MyDailyLifeTheme(themeMode = prefs.themeMode) {
                AppRootNavigation()
            }
        }
    }
}

@Composable
fun AppRootNavigation() {
    var currentUser by remember { mutableStateOf<FirebaseUser?>(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            }
        )
    } else {
        AuthenticatedMainContent(
            currentUser = user,
            onSignedOut = {
                currentUser = null
            }
        )
    }
}

private data class NavDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

@Composable
fun AuthenticatedMainContent(
    currentUser: FirebaseUser,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    // Instantiate ViewModel keyed by currentUser.uid with custom database ID
    val viewModel: DailyLifeViewModel = viewModel(
        key = currentUser.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                DailyLifeViewModel(
                    repository = DailyLifeRepository(db),
                    preferencesRepository = ThemePreferencesRepository(app),
                    reminderScheduler = TaskReminderScheduler(app),
                    currentUserId = currentUser.uid
                )
            }
        }
    )

    // Request POST_NOTIFICATIONS permission on Android 13+ when needed
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val tasksUiState by viewModel.tasksState.collectAsStateWithLifecycle()
    val routinesUiState by viewModel.routinesState.collectAsStateWithLifecycle()
    val backupsUiState by viewModel.backupsState.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val selectedDateStr by viewModel.selectedDate.collectAsStateWithLifecycle()
    val statusBannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBannerMessage) {
        statusBannerMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBannerMessage()
        }
    }

    val allTasks = (tasksUiState as? UiState.Success)?.data ?: emptyList()
    val allRoutines = (routinesUiState as? UiState.Success)?.data ?: emptyList()
    val allBackups = (backupsUiState as? UiState.Success)?.data ?: emptyList()

    val todayDateStr = remember { LocalDate.now().toString() }
    val todayTasks = remember(allTasks, todayDateStr) {
        allTasks.filter { DailyLifeViewModel.isTaskScheduledForDate(it, todayDateStr) }
            .sortedBy { it.startTime }
    }
    val todayStats = remember(allTasks, todayDateStr, allRoutines) {
        DailyLifeViewModel.calculateDailyStats(
            tasks = allTasks,
            dateStr = todayDateStr,
            activeRoutineCount = allRoutines.count { it.active }
        )
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var taskEditorTarget by remember { mutableStateOf<DailyTask?>(null) }
    var showTaskEditor by remember { mutableStateOf(false) }
    var taskEditorDefaultDate by remember { mutableStateOf(todayDateStr) }

    var routineEditorTarget by remember { mutableStateOf<RoutineItem?>(null) }
    var showRoutineEditor by remember { mutableStateOf(false) }

    // BackHandler on secondary tabs returns to Home Dashboard
    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    val destinations = listOf(
        NavDestination("Home", Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "nav_home"),
        NavDestination("Tasks", Icons.Filled.TaskAlt, Icons.Outlined.TaskAlt, "nav_tasks"),
        NavDestination("Schedule", Icons.Filled.Schedule, Icons.Outlined.Schedule, "nav_schedule"),
        NavDestination("Calendar", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth, "nav_calendar"),
        NavDestination("Reports", Icons.Filled.Analytics, Icons.Outlined.Analytics, "nav_reports")
    )

    val displayName = remember(currentUser) {
        currentUser.displayName?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: currentUser.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: "Friend"
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar {
                destinations.forEachIndexed { index, dest ->
                    val selected = selectedTab == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                contentDescription = dest.label
                            )
                        },
                        label = { Text(dest.label) },
                        modifier = Modifier.testTag(dest.tag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> DashboardScreen(
                    userName = displayName,
                    todayDateStr = todayDateStr,
                    todayStats = todayStats,
                    todayTasks = todayTasks,
                    allRoutines = allRoutines,
                    themeMode = userPreferences.themeMode,
                    onToggleTheme = {
                        val nextMode = when (userPreferences.themeMode) {
                            AppThemeMode.DARK -> AppThemeMode.LIGHT
                            else -> AppThemeMode.DARK
                        }
                        viewModel.setThemeMode(nextMode)
                    },
                    onSignOut = {
                        signOut(
                            context = context,
                            credentialManager = credentialManager,
                            onSignOutComplete = onSignedOut,
                            scope = scope
                        )
                    },
                    onAddTaskClick = {
                        taskEditorTarget = null
                        taskEditorDefaultDate = todayDateStr
                        showTaskEditor = true
                    },
                    onEditTask = { task ->
                        taskEditorTarget = task
                        taskEditorDefaultDate = task.date
                        showTaskEditor = true
                    },
                    onToggleTask = { viewModel.toggleTaskCompleted(it) },
                    onDeleteTask = { viewModel.deleteTask(it) },
                    onApplyRoutinesToToday = {
                        viewModel.applyActiveRoutinesToDate(todayDateStr, allRoutines, allTasks)
                    },
                    onNotifyPendingTasks = {
                        viewModel.sendPendingTasksNotification(todayTasks)
                    },
                    onSeedStarterPack = {
                        viewModel.seedStarterRoutinePack(todayDateStr)
                    }
                )

                1 -> TasksScreen(
                    allTasks = allTasks,
                    onAddTaskClick = {
                        taskEditorTarget = null
                        taskEditorDefaultDate = todayDateStr
                        showTaskEditor = true
                    },
                    onEditTask = { task ->
                        taskEditorTarget = task
                        taskEditorDefaultDate = task.date
                        showTaskEditor = true
                    },
                    onToggleTask = { viewModel.toggleTaskCompleted(it) },
                    onDeleteTask = { viewModel.deleteTask(it) }
                )

                2 -> ScheduleScreen(
                    todayDateStr = todayDateStr,
                    todayTasks = todayTasks,
                    routines = allRoutines,
                    onAddTaskClick = {
                        taskEditorTarget = null
                        taskEditorDefaultDate = todayDateStr
                        showTaskEditor = true
                    },
                    onEditTask = { task ->
                        taskEditorTarget = task
                        taskEditorDefaultDate = task.date
                        showTaskEditor = true
                    },
                    onToggleTask = { viewModel.toggleTaskCompleted(it) },
                    onAddRoutineClick = {
                        routineEditorTarget = null
                        showRoutineEditor = true
                    },
                    onEditRoutine = { routine ->
                        routineEditorTarget = routine
                        showRoutineEditor = true
                    },
                    onToggleRoutineActive = { viewModel.toggleRoutineActive(it) },
                    onDeleteRoutine = { viewModel.deleteRoutine(it) },
                    onSyncRoutinesToToday = {
                        viewModel.applyActiveRoutinesToDate(todayDateStr, allRoutines, allTasks)
                    },
                    onSeedStarterRoutines = {
                        viewModel.seedStarterRoutinePack(todayDateStr)
                    }
                )

                3 -> CalendarScreen(
                    selectedDateStr = selectedDateStr,
                    allTasks = allTasks,
                    onSelectDate = { viewModel.selectDate(it) },
                    onAddTaskForDate = { dateForNewTask ->
                        taskEditorTarget = null
                        taskEditorDefaultDate = dateForNewTask
                        showTaskEditor = true
                    },
                    onEditTask = { task ->
                        taskEditorTarget = task
                        taskEditorDefaultDate = task.date
                        showTaskEditor = true
                    },
                    onToggleTask = { viewModel.toggleTaskCompleted(it) },
                    onDeleteTask = { viewModel.deleteTask(it) }
                )

                4 -> AnalyticsScreen(
                    todayStats = todayStats,
                    allTasks = allTasks,
                    allRoutines = allRoutines,
                    backups = allBackups,
                    defaultReminderMinutes = userPreferences.defaultReminderMinutes,
                    onUpdateDefaultReminder = { viewModel.setDefaultReminderMinutes(it) },
                    onSendPendingNotification = { viewModel.sendPendingTasksNotification(todayTasks) },
                    onCreateCloudBackup = { label ->
                        viewModel.createCloudBackup(label, allTasks, allRoutines)
                    },
                    onRestoreBackup = { viewModel.restoreCloudBackup(it) },
                    onDeleteBackup = { viewModel.deleteBackup(it) },
                    onExportJson = { viewModel.exportBackupJson(allTasks, allRoutines) },
                    onImportJson = { viewModel.importBackupFromJson(it) }
                )
            }

            // Show stream error banner if Firestore returns an error
            val streamError = (tasksUiState as? UiState.Error)?.message
                ?: (routinesUiState as? UiState.Error)?.message
            AnimatedVisibility(
                visible = streamError != null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = streamError ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
            }
        }
    }

    if (showTaskEditor) {
        TaskEditorSheet(
            initialTask = taskEditorTarget,
            defaultDate = taskEditorDefaultDate,
            defaultReminderMinutes = userPreferences.defaultReminderMinutes,
            onDismiss = { showTaskEditor = false },
            onSave = { task, isEditing ->
                viewModel.saveTask(task, isEditing)
                showTaskEditor = false
            }
        )
    }

    if (showRoutineEditor) {
        RoutineEditorSheet(
            initialRoutine = routineEditorTarget,
            onDismiss = { showRoutineEditor = false },
            onSave = { routine, isEditing ->
                viewModel.saveRoutine(routine, isEditing)
                showRoutineEditor = false
            }
        )
    }
}
