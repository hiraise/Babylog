package com.kid.babylog.feature.diary

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.kid.babylog.core.data.ThemeMode
import com.kid.babylog.core.ui.LocalClock
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.components.AppSheet
import com.kid.babylog.core.ui.components.SheetAction
import com.kid.babylog.core.ui.components.SheetMenuItem
import com.kid.babylog.core.ui.eventIcon
import com.kid.babylog.core.ui.eventTimeLine
import com.kid.babylog.core.ui.eventTitle
import com.kid.babylog.core.ui.toLocalDate
import com.kid.babylog.feature.children.ChildSelector
import com.kid.babylog.feature.children.ChildSheet
import com.kid.babylog.feature.children.ChildrenSheet
import com.kid.babylog.feature.editor.EditEventSheet
import com.kid.babylog.feature.editor.FeedingStartSheet
import com.kid.babylog.feature.editor.NewEventSheet
import com.kid.babylog.feature.editor.NewEventKind
import com.kid.babylog.feature.editor.SleepStartSheet
import kotlinx.coroutines.delay
import java.time.LocalDate

private enum class ChildSheetMode { NONE, ADD, EDIT }

private const val NONE = -1L

/**
 * Главный экран: подключает [DiaryViewModel] к [DiaryContent] и показывает нижние листы (ADR-0018).
 * Тему применяет `MainActivity` (ADR-0017): экран только показывает выбор и сообщает о новом.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    viewModel: DiaryViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    initialTab: DiaryTab = DiaryTab.TODAY,
) {
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
        state.message?.let { text ->
            // Остановленный касанием таймер можно вернуть — сообщение висит дольше
            val undo = state.canUndo
            val result = snackbarHostState.showSnackbar(
                message = text,
                actionLabel = if (undo) "Отменить" else null,
                duration = if (undo) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            viewModel.consumeMessage(undo = result == SnackbarResult.ActionPerformed)
        }
    }

    val child = state.child
    // id вместо объектов: переживают поворот экрана
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var newEvent by rememberSaveable { mutableStateOf<NewEventKind?>(null) }
    var startingSleep by rememberSaveable { mutableStateOf(false) }
    var startingFeeding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableLongStateOf(NONE) }
    var deletingId by rememberSaveable { mutableLongStateOf(NONE) }
    var showDaySelector by rememberSaveable { mutableStateOf(false) }
    var showThemeSheet by rememberSaveable { mutableStateOf(false) }
    var childSheet by rememberSaveable { mutableStateOf(ChildSheetMode.NONE) }
    var confirmChildDelete by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showChildren by rememberSaveable { mutableStateOf(false) }
    // История по одному дню, по умолчанию сегодня; при смене ребёнка — снова сегодня
    // NONE — «сегодня»: после полуночи история сама переходит на новый день. Запоминается только
    // день, выбранный вручную; при смене ребёнка — снова сегодня
    var selectedEpochDay by rememberSaveable(child?.id) { mutableLongStateOf(NONE) }
    val today = now.toLocalDate()
    val selectedDate = if (selectedEpochDay == NONE) today else LocalDate.ofEpochDay(selectedEpochDay)
    val selectDay = { day: LocalDate -> selectedEpochDay = if (day == today) NONE else day.toEpochDay() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> if (uri != null) viewModel.exportTo(uri.toString()) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.importFrom(uri.toString()) }

    Scaffold(
        topBar = {
            Column {
                // Шапка сливается с фоном экрана. Слева — выбор ребёнка: «Дневник Маши» фирменным шрифтом,
                // как логотип в макете, под ним возраст и вес (ADR-0019)
                TopAppBar(
                    title = {
                        if (child != null) {
                            ChildSelector(child, state.lastWeight, now, onClick = { showChildren = true })
                        } else {
                            Text(
                                "Дневник малыша",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                    ),
                    actions = {
                        IconButton(onClick = { menuOpen = true }) {
                            AppIcon(R.drawable.ic_more, contentDescription = "Меню")
                        }
                    },
                )
                DiaryTabs(tab) { tab = it }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (child != null) {
            val actions = DiaryActions(
                onStartSleep = { startingSleep = true },
                onEndSleep = { viewModel.endSleep() },
                onStartFeeding = { startingFeeding = true },
                onEndFeeding = { viewModel.endFeeding() },
                onSwitchBreast = { viewModel.switchBreast() },
                onAdd = { newEvent = it },
                onEditEvent = { editingId = it.id },
                onDeleteEvent = { deletingId = it.id },
                onSelectDate = selectDay,
                onPickDate = { showDaySelector = true },
            )
            when (tab) {
                DiaryTab.TODAY -> TodayContent(state, child, now, actions, Modifier.padding(padding))
                DiaryTab.HISTORY -> HistoryContent(state, child, now, selectedDate, actions, Modifier.padding(padding))
            }
        }
    }

    if (menuOpen) {
        AppSheet(onDismiss = { menuOpen = false }, edgeToEdge = true) { closer ->
            // Пункт закрывает меню и только потом открывает следующий лист или выбор файла
            val pick = { action: () -> Unit -> closer.close { menuOpen = false; action() } }
            SheetMenuItem(R.drawable.ic_theme, "Тема", onClick = { pick { showThemeSheet = true } })
            SheetMenuItem(R.drawable.ic_export, "Экспорт в CSV", onClick = {
                pick { exportLauncher.launch("baby-diary-${now.toLocalDate()}.csv") }
            })
            SheetMenuItem(R.drawable.ic_import, "Импорт из CSV", onClick = { pick { importLauncher.launch(arrayOf("*/*")) } })
        }
    }

    if (showThemeSheet) {
        ThemeSheet(mode = themeMode, onSelect = onThemeModeChange, onDismiss = { showThemeSheet = false })
    }

    if (child == null) return

    newEvent?.let { kind ->
        NewEventSheet(
            kind = kind,
            child = child,
            pastMedicines = state.pastMedicines,
            onDismiss = { newEvent = null },
            onSave = { event, photos ->
                viewModel.saveEvent(event, photos)
                newEvent = null
            },
        )
    }

    if (startingSleep) {
        SleepStartSheet(
            activeFeeding = state.activeFeeding,
            onDismiss = { startingSleep = false },
            onSave = {
                viewModel.startSleep(it)
                startingSleep = false
            },
        )
    }

    if (startingFeeding) {
        FeedingStartSheet(
            activeSleep = state.activeSleep,
            lastBottle = state.lastBottle,
            onDismiss = { startingFeeding = false },
            onSave = { breast, time ->
                viewModel.startFeeding(time, breast)
                startingFeeding = false
            },
            onSaveBottle = { volumeMl, milk, time ->
                viewModel.addBottle(volumeMl, milk, time)
                startingFeeding = false
            },
        )
    }

    state.events.find { it.id == editingId }?.let { event ->
        key(event.id) {
            // Лист открывается, когда фото записи загружены: форма берёт их один раз, при открытии
            val photos by remember { viewModel.eventPhotos(event.id) }.collectAsState(initial = null)
            photos?.let {
                EditEventSheet(
                    event = event,
                    child = child,
                    photos = it,
                    pastMedicines = state.pastMedicines,
                    onDismiss = { editingId = NONE },
                    onSave = { edited, edit ->
                        viewModel.saveEvent(edited, edit)
                        editingId = NONE
                    },
                )
            }
        }
    }

    state.events.find { it.id == deletingId }?.let { event ->
        AppSheet(
            onDismiss = { deletingId = NONE },
            title = "Удалить запись?",
            icon = eventIcon(event),
            confirm = SheetAction("Удалить", destructive = true) {
                viewModel.deleteEvent(event)
                deletingId = NONE
            },
        ) {
            Text("${eventTitle(event, child.sex)}\n${eventTimeLine(event)}", style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (showDaySelector) {
        DaySelectorSheet(
            days = historyDays(state.events, now.toLocalDate()),
            events = state.events,
            selectedDate = selectedDate,
            today = now.toLocalDate(),
            onDismiss = { showDaySelector = false },
            onPick = { day ->
                selectDay(day)
                showDaySelector = false
            },
        )
    }

    if (showChildren) {
        ChildrenSheet(
            children = state.children,
            selectedId = child.id,
            now = now,
            onSelect = {
                viewModel.selectChild(it)
                showChildren = false
            },
            onEdit = {
                showChildren = false
                childSheet = ChildSheetMode.EDIT
            },
            onAdd = {
                showChildren = false
                childSheet = ChildSheetMode.ADD
            },
            onDismiss = { showChildren = false },
        )
    }

    when (childSheet) {
        ChildSheetMode.ADD -> ChildSheet(
            initial = null,
            children = state.children,
            onDismiss = { childSheet = ChildSheetMode.NONE },
            onSave = { name, birthDate, sex, photo ->
                viewModel.addChild(name, birthDate, sex, photo)
                childSheet = ChildSheetMode.NONE
            },
        )
        ChildSheetMode.EDIT -> ChildSheet(
            initial = child,
            children = state.children,
            onDismiss = { childSheet = ChildSheetMode.NONE },
            onSave = { name, birthDate, sex, photo ->
                viewModel.updateChild(child.copy(name = name, birthDate = birthDate, sex = sex, photo = photo))
                childSheet = ChildSheetMode.NONE
            },
            // Последнего ребёнка удалить нельзя
            onDelete = if (state.children.size > 1) {
                {
                    childSheet = ChildSheetMode.NONE
                    confirmChildDelete = true
                }
            } else null,
        )
        ChildSheetMode.NONE -> Unit
    }

    if (confirmChildDelete) {
        AppSheet(
            onDismiss = { confirmChildDelete = false },
            title = "Удалить «${child.name}»?",
            icon = R.drawable.ic_delete,
            confirm = SheetAction("Удалить", destructive = true) {
                viewModel.deleteChild(child)
                confirmChildDelete = false
            },
        ) {
            Text(
                "Записи этого ребёнка (${state.events.size}) тоже будут удалены. Отменить это нельзя.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
