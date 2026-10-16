package com.kid.babylog.feature.diary

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kid.babylog.R
import com.kid.babylog.core.ui.LocalClock
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.eventIcon
import com.kid.babylog.core.ui.eventTimeLine
import com.kid.babylog.core.ui.eventTitle
import com.kid.babylog.core.ui.toLocalDate
import com.kid.babylog.feature.children.ChildDialog
import com.kid.babylog.feature.editor.EditEventDialog
import com.kid.babylog.feature.editor.NewEventDialog
import com.kid.babylog.feature.editor.NewEventKind
import com.kid.babylog.feature.editor.SleepStartDialog
import kotlinx.coroutines.delay
import java.time.LocalDate

private enum class ChildDialogMode { NONE, ADD, EDIT }

private const val NONE = -1L

/** Главный экран: подключает [DiaryViewModel] к [DiaryContent] и показывает диалоги. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(viewModel: DiaryViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clock = LocalClock.current
    var now by remember { mutableLongStateOf(clock.millis()) }
    LaunchedEffect(clock) {
        while (true) {
            now = clock.millis()
            delay(1_000)
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val child = state.child
    // id вместо объектов: переживают поворот экрана
    var newEvent by rememberSaveable { mutableStateOf<NewEventKind?>(null) }
    var startingSleep by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableLongStateOf(NONE) }
    var deletingId by rememberSaveable { mutableLongStateOf(NONE) }
    var showDaySelector by rememberSaveable { mutableStateOf(false) }
    var childDialog by rememberSaveable { mutableStateOf(ChildDialogMode.NONE) }
    var confirmChildDelete by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // История по одному дню, по умолчанию сегодня; при смене ребёнка — снова сегодня
    var selectedEpochDay by rememberSaveable(child?.id) { mutableLongStateOf(now.toLocalDate().toEpochDay()) }
    val selectedDate = LocalDate.ofEpochDay(selectedEpochDay)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> if (uri != null) viewModel.exportTo(uri.toString()) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.importFrom(uri.toString()) }

    Scaffold(
        topBar = {
            // Шапка сливается с фоном экрана, название — фирменным шрифтом, как логотип в макете
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Дневник малыша",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        AppIcon(R.drawable.ic_more, contentDescription = "Меню")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        // Пункты меню M3 набирает labelLarge — стилем кнопок; меню — обычный текст
                        DropdownMenuItem(
                            text = { Text("Добавить ребёнка", style = MaterialTheme.typography.bodyLarge) },
                            leadingIcon = { AppIcon(R.drawable.ic_add_child, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                childDialog = ChildDialogMode.ADD
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Экспорт в CSV", style = MaterialTheme.typography.bodyLarge) },
                            leadingIcon = { AppIcon(R.drawable.ic_export, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                exportLauncher.launch("baby-diary-${now.toLocalDate()}.csv")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Импорт из CSV", style = MaterialTheme.typography.bodyLarge) },
                            leadingIcon = { AppIcon(R.drawable.ic_import, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                importLauncher.launch(arrayOf("*/*"))
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (child != null) {
            DiaryContent(
                state = state,
                child = child,
                now = now,
                selectedDate = selectedDate,
                actions = DiaryActions(
                    onSelectChild = viewModel::selectChild,
                    onEditChild = { childDialog = ChildDialogMode.EDIT },
                    onStartSleep = { startingSleep = true },
                    onEndSleep = { viewModel.endSleep() },
                    onAdd = { newEvent = it },
                    onEditEvent = { editingId = it.id },
                    onDeleteEvent = { deletingId = it.id },
                    onSelectDate = { selectedEpochDay = it.toEpochDay() },
                    onPickDate = { showDaySelector = true },
                ),
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (child == null) return

    newEvent?.let { kind ->
        NewEventDialog(
            kind = kind,
            childId = child.id,
            onDismiss = { newEvent = null },
            onSave = {
                viewModel.saveEvent(it)
                newEvent = null
            },
        )
    }

    if (startingSleep) {
        SleepStartDialog(
            childName = child.name,
            onDismiss = { startingSleep = false },
            onSave = {
                viewModel.startSleep(it)
                startingSleep = false
            },
        )
    }

    state.events.find { it.id == editingId }?.let { event ->
        key(event.id) {
            EditEventDialog(
                event = event,
                onDismiss = { editingId = NONE },
                onSave = {
                    viewModel.saveEvent(it)
                    editingId = NONE
                },
            )
        }
    }

    state.events.find { it.id == deletingId }?.let { event ->
        AlertDialog(
            onDismissRequest = { deletingId = NONE },
            icon = { AppIcon(eventIcon(event), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Удалить запись?") },
            text = { Text("${eventTitle(event)}\n${eventTimeLine(event)}") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEvent(event)
                    deletingId = NONE
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingId = NONE }) { Text("Отмена") } },
        )
    }

    if (showDaySelector) {
        DaySelectorDialog(
            days = historyDays(state.events, now.toLocalDate()),
            events = state.events,
            selectedDate = selectedDate,
            today = now.toLocalDate(),
            onDismiss = { showDaySelector = false },
            onPick = { day ->
                selectedEpochDay = day.toEpochDay()
                showDaySelector = false
            },
        )
    }

    when (childDialog) {
        ChildDialogMode.ADD -> ChildDialog(
            initial = null,
            children = state.children,
            onDismiss = { childDialog = ChildDialogMode.NONE },
            onSave = { name, birthDate ->
                viewModel.addChild(name, birthDate)
                childDialog = ChildDialogMode.NONE
            },
        )
        ChildDialogMode.EDIT -> ChildDialog(
            initial = child,
            children = state.children,
            onDismiss = { childDialog = ChildDialogMode.NONE },
            onSave = { name, birthDate ->
                viewModel.updateChild(child.copy(name = name, birthDate = birthDate))
                childDialog = ChildDialogMode.NONE
            },
            // Последнего ребёнка удалить нельзя
            onDelete = if (state.children.size > 1) {
                {
                    childDialog = ChildDialogMode.NONE
                    confirmChildDelete = true
                }
            } else null,
        )
        ChildDialogMode.NONE -> Unit
    }

    if (confirmChildDelete) {
        AlertDialog(
            onDismissRequest = { confirmChildDelete = false },
            icon = { AppIcon(R.drawable.ic_delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Удалить «${child.name}»?") },
            text = { Text("Записи этого ребёнка (${state.events.size}) тоже будут удалены. Отменить это нельзя.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteChild(child)
                    confirmChildDelete = false
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmChildDelete = false }) { Text("Отмена") } },
        )
    }
}
