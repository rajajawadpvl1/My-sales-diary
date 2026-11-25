package com.example.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.BackupToolsBottomSheet
import com.example.ui.components.DashboardSummaryRow
import com.example.ui.components.ManageCoursesDialog
import com.example.ui.screens.NotesScreen
import com.example.ui.screens.RemindersScreen
import com.example.ui.viewmodel.DiaryTab
import com.example.ui.viewmodel.SalesDiaryUiState
import com.example.ui.viewmodel.SalesDiaryViewModel
import com.example.ui.viewmodel.UiMessage
import com.example.util.DialerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SalesDiaryApp(
    viewModel: SalesDiaryViewModel,
    onDialPhone: ((String) -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val notesListState = rememberLazyListState()

    var showBackupSheet by remember { mutableStateOf(false) }
    var showManageCoursesDialog by remember { mutableStateOf(false) }

    // Refresh local calendar date whenever app resumes so reminders activate automatically on new days
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshTodayDate()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                is UiMessage.Info -> message.text
                is UiMessage.Error -> message.text
            }
            snackbarHostState.showSnackbar(text)
        }
    }

    // System Back navigation support for secondary tab or active edit mode
    BackHandler(enabled = uiState.selectedTab == DiaryTab.REMINDERS || uiState.form.isEditing) {
        when {
            uiState.form.isEditing -> viewModel.cancelEditing()
            uiState.selectedTab == DiaryTab.REMINDERS -> viewModel.selectTab(DiaryTab.NOTES)
        }
    }

    // Zero-permission Storage Access Framework launchers for CSV export & JSON backup/restore
    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val result = runCatching {
                    val csv = viewModel.generateCsvContent()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(csv.toByteArray(Charsets.UTF_8))
                        } ?: error("Unable to open file for writing.")
                    }
                }
                result.onSuccess {
                    viewModel.emitInfo("Leads exported to CSV successfully.")
                }.onFailure {
                    viewModel.emitError("CSV export failed: ${it.localizedMessage ?: "Unknown error"}")
                }
            }
        }
    }

    val backupJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val result = runCatching {
                    val json = viewModel.generateJsonBackupContent()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(json.toByteArray(Charsets.UTF_8))
                        } ?: error("Unable to open backup file for writing.")
                    }
                }
                result.onSuccess {
                    viewModel.emitInfo("JSON backup saved successfully.")
                }.onFailure {
                    viewModel.emitError("Backup failed: ${it.localizedMessage ?: "Unknown error"}")
                }
            }
        }
    }

    val restoreJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)
                            ?.use { it.readText() }
                            ?: error("Unable to read selected file.")
                    }
                }
                result.onSuccess { rawJson ->
                    viewModel.restoreBackupJson(rawJson)
                }.onFailure {
                    viewModel.emitError("Restore failed: ${it.localizedMessage ?: "Unknown error"}")
                }
            }
        }
    }

    val handleDial: (String) -> Unit = { phoneNumber ->
        if (onDialPhone != null) {
            onDialPhone(phoneNumber)
        } else {
            val opened = DialerHelper.openDialer(context, phoneNumber)
            if (!opened) {
                viewModel.emitError("No dialer app available on this device.")
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val useNavigationRail = maxWidth >= 840.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useNavigationRail) {
                SalesDiaryNavigationRail(
                    selectedTab = uiState.selectedTab,
                    urgentRemindersCount = uiState.stats.activeUrgentReminders,
                    onSelectTab = viewModel::selectTab,
                )
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    SalesDiaryHeader(
                        uiState = uiState,
                        onOpenBackupTools = { showBackupSheet = true },
                        onDueTodayClick = { viewModel.selectTab(DiaryTab.REMINDERS) },
                        onTotalLeadsClick = { viewModel.selectTab(DiaryTab.NOTES) },
                    )
                },
                bottomBar = {
                    if (!useNavigationRail) {
                        SalesDiaryBottomBar(
                            selectedTab = uiState.selectedTab,
                            urgentRemindersCount = uiState.stats.activeUrgentReminders,
                            onSelectTab = viewModel::selectTab,
                        )
                    }
                },
                floatingActionButton = {
                    if (uiState.selectedTab == DiaryTab.NOTES) {
                        FloatingActionButton(
                            onClick = {
                                viewModel.openNewLeadForm()
                                coroutineScope.launch {
                                    notesListState.animateScrollToItem(0)
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = RoundedCornerShape(16.dp),
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                            modifier = Modifier
                                .defaultMinSize(minWidth = 56.dp, minHeight = 56.dp)
                                .testTag("fab_add_lead"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = stringResource(R.string.action_quick_add),
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = 680.dp)
                            .fillMaxWidth(),
                    ) {
                        when (uiState.selectedTab) {
                            DiaryTab.NOTES -> {
                                NotesScreen(
                                    formState = uiState.form,
                                    leads = uiState.filteredLeads,
                                    totalLeadCount = uiState.allLeads.size,
                                    courses = uiState.courses,
                                    searchQuery = uiState.searchQuery,
                                    todayIso = uiState.todayIso,
                                    listState = notesListState,
                                    onToggleFormExpanded = viewModel::toggleFormExpanded,
                                    onStudentNameChange = viewModel::onStudentNameChange,
                                    onPhoneChange = viewModel::onPhoneNumberChange,
                                    onCourseSelected = viewModel::onCourseSelected,
                                    onCallDateSelected = viewModel::onCallDateSelected,
                                    onStatusSelected = viewModel::onStatusSelected,
                                    onCustomFollowUpDateSelected = viewModel::onCustomFollowUpDateSelected,
                                    onCustomFollowUpTimeSelected = viewModel::onCustomFollowUpTimeSelected,
                                    onScheduleRetryToggle = viewModel::onScheduleRetryToggle,
                                    onNotesChange = viewModel::onNotesChange,
                                    onSubmitLead = viewModel::submitLeadForm,
                                    onCancelEdit = viewModel::cancelEditing,
                                    onManageCoursesClick = { showManageCoursesDialog = true },
                                    onSearchQueryChange = viewModel::onSearchQueryChange,
                                    onEditLead = { lead ->
                                        viewModel.startEditingLead(lead)
                                        coroutineScope.launch {
                                            notesListState.animateScrollToItem(0)
                                        }
                                    },
                                    onDeleteLead = viewModel::deleteLead,
                                    onCallLead = handleDial,
                                )
                            }

                            DiaryTab.REMINDERS -> {
                                val coursePricesByName = remember(uiState.courses) {
                                    uiState.courses.associate { it.name.lowercase() to it.price }
                                }
                                RemindersScreen(
                                    reminders = uiState.reminders,
                                    coursePricesByName = coursePricesByName,
                                    todayIso = uiState.todayIso,
                                    onCallClick = handleDial,
                                    onQuickOutcome = { lead, newStatus ->
                                        viewModel.applyReminderAction(lead, newStatus)
                                    },
                                    onRescheduleOutcome = { lead, newStatus, dateIso, time, note ->
                                        viewModel.applyReminderAction(
                                            lead = lead,
                                            newStatus = newStatus,
                                            customDateIso = dateIso,
                                            customTime = time,
                                            updatedNote = note,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBackupSheet) {
        BackupToolsBottomSheet(
            leadCount = uiState.allLeads.size,
            onExportCsv = {
                exportCsvLauncher.launch("my_sales_diary_leads_${uiState.todayIso}.csv")
            },
            onBackupJson = {
                backupJsonLauncher.launch("my_sales_diary_backup_${uiState.todayIso}.json")
            },
            onRestoreJson = {
                restoreJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            },
            onManageCourses = { showManageCoursesDialog = true },
            onDismiss = { showBackupSheet = false },
        )
    }

    if (showManageCoursesDialog) {
        ManageCoursesDialog(
            courses = uiState.courses,
            onAddCourse = viewModel::addCourse,
            onUpdateCourse = viewModel::updateCourse,
            onDeleteCourse = viewModel::deleteCourse,
            onDismiss = { showManageCoursesDialog = false },
        )
    }
}

@Composable
private fun SalesDiaryHeader(
    uiState: SalesDiaryUiState,
    onOpenBackupTools: () -> Unit,
    onDueTodayClick: () -> Unit,
    onTotalLeadsClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.app_tagline),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    IconButton(
                        onClick = onOpenBackupTools,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("backup_tools_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Backup,
                            contentDescription = stringResource(R.string.action_backup_tools),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                DashboardSummaryRow(
                    stats = uiState.stats,
                    onDueTodayClick = onDueTodayClick,
                    onTotalLeadsClick = onTotalLeadsClick,
                )
            }
        }
    }
}

@Composable
private fun SalesDiaryBottomBar(
    selectedTab: DiaryTab,
    urgentRemindersCount: Int,
    onSelectTab: (DiaryTab) -> Unit,
) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets.navigationBars,
        ) {
            NavigationBarItem(
                selected = selectedTab == DiaryTab.NOTES,
                onClick = { onSelectTab(DiaryTab.NOTES) },
                icon = {
                    Icon(
                        imageVector = if (selectedTab == DiaryTab.NOTES) {
                            Icons.Filled.EditNote
                        } else {
                            Icons.Outlined.EditNote
                        },
                        contentDescription = stringResource(R.string.tab_notes),
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.tab_notes),
                        fontWeight = if (selectedTab == DiaryTab.NOTES) FontWeight.SemiBold else FontWeight.Medium,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.testTag("nav_tab_notes"),
            )

            NavigationBarItem(
                selected = selectedTab == DiaryTab.REMINDERS,
                onClick = { onSelectTab(DiaryTab.REMINDERS) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (urgentRemindersCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary,
                                ) {
                                    Text(
                                        text = if (urgentRemindersCount > 99) "99+" else urgentRemindersCount.toString(),
                                    )
                                }
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (selectedTab == DiaryTab.REMINDERS) {
                                Icons.Filled.NotificationsActive
                            } else {
                                Icons.Outlined.NotificationsActive
                            },
                            contentDescription = stringResource(R.string.tab_reminders),
                        )
                    }
                },
                label = {
                    Text(
                        text = stringResource(R.string.tab_reminders),
                        fontWeight = if (selectedTab == DiaryTab.REMINDERS) FontWeight.SemiBold else FontWeight.Medium,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.testTag("nav_tab_reminders"),
            )
        }
    }
}

@Composable
private fun SalesDiaryNavigationRail(
    selectedTab: DiaryTab,
    urgentRemindersCount: Int,
    onSelectTab: (DiaryTab) -> Unit,
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.statusBarsPadding(),
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        NavigationRailItem(
            selected = selectedTab == DiaryTab.NOTES,
            onClick = { onSelectTab(DiaryTab.NOTES) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == DiaryTab.NOTES) {
                        Icons.Filled.EditNote
                    } else {
                        Icons.Outlined.EditNote
                    },
                    contentDescription = stringResource(R.string.tab_notes),
                )
            },
            label = { Text(stringResource(R.string.tab_notes)) },
            modifier = Modifier.testTag("nav_tab_notes"),
        )
        Spacer(modifier = Modifier.height(8.dp))
        NavigationRailItem(
            selected = selectedTab == DiaryTab.REMINDERS,
            onClick = { onSelectTab(DiaryTab.REMINDERS) },
            icon = {
                BadgedBox(
                    badge = {
                        if (urgentRemindersCount > 0) {
                            Badge { Text(urgentRemindersCount.toString()) }
                        }
                    },
                ) {
                    Icon(
                        imageVector = if (selectedTab == DiaryTab.REMINDERS) {
                            Icons.Filled.NotificationsActive
                        } else {
                            Icons.Outlined.NotificationsActive
                        },
                        contentDescription = stringResource(R.string.tab_reminders),
                    )
                }
            },
            label = { Text(stringResource(R.string.tab_reminders)) },
            modifier = Modifier.testTag("nav_tab_reminders"),
        )
    }
}
