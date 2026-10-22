package com.kid.babylog.feature.diary

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import com.kid.babylog.core.data.DatabaseRecovery
import com.kid.babylog.core.data.ThemeChoice
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.Illness
import com.kid.babylog.core.model.illnessMedicines
import com.kid.babylog.core.ui.LocalClock
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.components.AppSheet
import com.kid.babylog.core.ui.components.SheetAction
import com.kid.babylog.core.ui.components.SheetMenuItem
import com.kid.babylog.core.ui.eventIcon
import com.kid.babylog.core.ui.eventTimeLine
import com.kid.babylog.core.ui.eventTitle
import com.kid.babylog.core.ui.guide.GuideSheet
import com.kid.babylog.core.ui.guide.GuideTarget
import com.kid.babylog.core.ui.guide.guideTarget
import com.kid.babylog.core.ui.toLocalDate
import com.kid.babylog.feature.analytics.AnalyticsContent
import com.kid.babylog.feature.analytics.AnalyticsSection
import com.kid.babylog.feature.analytics.cryingWeekSummary
import com.kid.babylog.feature.children.ChildSelector
import com.kid.babylog.feature.children.ChildSheet
import com.kid.babylog.feature.children.ChildrenSheet
import com.kid.babylog.feature.development.DevelopmentContent
import com.kid.babylog.feature.development.DevelopmentViewModel
import com.kid.babylog.feature.development.WeekTipsBanner
import com.kid.babylog.feature.development.developmentWeek
import com.kid.babylog.feature.development.hasNewTips
import com.kid.babylog.feature.editor.CryingDoneSheet
import com.kid.babylog.feature.editor.EditEventSheet
import com.kid.babylog.feature.editor.FeedingStartSheet
import com.kid.babylog.feature.editor.NewEventSheet
import com.kid.babylog.feature.editor.NewEventKind
import com.kid.babylog.feature.editor.SleepStartSheet
import com.kid.babylog.feature.notes.CryingWeek
import com.kid.babylog.feature.notes.NotesContent
import com.kid.babylog.feature.notes.NotesFilter
import com.kid.babylog.feature.notes.NotesFilterSaver
import com.kid.babylog.feature.shade.ShadeMenuItem
import com.kid.babylog.feature.shade.rememberShadeToggle
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId

private enum class ChildSheetMode { NONE, ADD, EDIT }

private const val NONE = -1L

/**
 * Главный экран (ADR-0035): шапка с ребёнком, нижняя панель разделов и нижние листы (ADR-0018).
 * Подключает [DiaryViewModel] и [DevelopmentViewModel] (ADR-0037). Тему применяет `MainActivity`
 * (ADR-0017): экран только показывает выбор и сообщает о новом. Системная «Назад» сначала закрывает
 * открытое внутри раздела, затем из любого раздела возвращает на «Сегодня». Уведомление в шторке
 * (ADR-0042) включается в меню ([shadeEnabled]); его «Отметить» открывает лист плача [cryingDetailsId].
 *
 * Справка (ADR-0043) — последний пункт меню ([onOpenHelp]), точка на ⋮ — [helpBadge], карточка «Где справка»
 * вверху «Сегодня» — [helpPointer]. [example] — пример дневника в гайде справки: без экспорта, импорта и «Справки».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    viewModel: DiaryViewModel,
    theme: ThemeChoice,
    onThemeChange: (ThemeChoice) -> Unit,
    development: DevelopmentViewModel,
    initialSection: MainSection = MainSection.TODAY,
    initialDiaryView: DiaryView = DiaryView.DAYS,
    recovery: DatabaseRecovery? = null,
    onRecoverySeen: () -> Unit = {},
    shadeEnabled: Boolean = false,
    onShadeChange: (Boolean) -> Unit = {},
    cryingDetailsId: Long? = null,
    onCryingDetailsShown: () -> Unit = {},
    example: Boolean = false,
    onOpenHelp: (() -> Unit)? = null,
    helpBadge: Boolean = false,
    helpPointer: (@Composable () -> Unit)? = null,
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
    // Плач, остановленный касанием плитки или началом сна: лист «Плач закончился» спрашивает подробности
    // (ADR-0030). После засыпания лист открывается сам, а не кнопкой в снекбаре: снекбар гаснет через
    // 10 секунд, пока укладывают ребёнка. «Ещё плачет» в нём нет, пока идёт сон: спящий не плачет
    var finishedCryingId by rememberSaveable { mutableLongStateOf(NONE) }
    // «Отметить» в уведомлении в шторке: плач остановили там, причины спрашивает тот же лист
    LaunchedEffect(cryingDetailsId) {
        if (cryingDetailsId != null) {
            finishedCryingId = cryingDetailsId
            onCryingDetailsShown()
        }
    }
    val shadeToggle = rememberShadeToggle(shadeEnabled, onShadeChange)
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { text ->
            // С кнопкой сообщение висит дольше: остановку или случайный плач можно вернуть
            val button = state.messageAction
            val result = snackbarHostState.showSnackbar(
                message = text,
                actionLabel = when (button) {
                    is MessageAction.ResumeTimer, is MessageAction.CancelCrying -> "Отменить"
                    null -> null
                },
                duration = if (button != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            viewModel.consumeMessage(result == SnackbarResult.ActionPerformed)
        }
    }

    val child = state.child
    // id вместо объектов: переживают поворот экрана
    var section by rememberSaveable { mutableStateOf(initialSection) }
    var diaryView by rememberSaveable { mutableStateOf(initialDiaryView) }
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
    // Фильтр «Заметок» (ADR-0031) — здесь, а не в «Дневнике»: метку открывает и «Аналитика»
    var notesFilter by rememberSaveable(child?.id, stateSaver = NotesFilterSaver) { mutableStateOf<NotesFilter>(NotesFilter.All) }
    // Раздел, с которого откроется «Аналитика»: карточка плача на «Заметках» ведёт сразу в «Плач»
    var analyticsStart by rememberSaveable { mutableStateOf<AnalyticsSection?>(null) }
    // Экран болезни (ADR-0040) — внутри раздела «Сегодня» или «Дневник»; листы здоровья — в HealthSheetsHost
    var openIllnessId by rememberSaveable(child?.id) { mutableLongStateOf(NONE) }
    val healthSheets = rememberHealthSheets()
    val openIllness = state.events.firstNotNullOfOrNull { (it as? Illness)?.takeIf { illness -> illness.id == openIllnessId } }
    val today = now.toLocalDate()
    val selectedDate = if (selectedEpochDay == NONE) today else LocalDate.ofEpochDay(selectedEpochDay)
    val selectDay = { day: LocalDate -> selectedEpochDay = if (day == today) NONE else day.toEpochDay() }
    val openSection = { target: MainSection ->
        section = target
        analyticsStart = null
        openIllnessId = NONE
    }

    // «Развитие» (ADR-0037): неделя ребёнка, точка на панели и «новые» на полосе, пока неделю не открывали
    val developmentData by development.data.collectAsStateWithLifecycle()
    val seenWeeks by development.seenWeeks.collectAsStateWithLifecycle()
    val birth = child?.birthDate?.toLocalDate()
    val week = remember(developmentData, birth, today) {
        val data = developmentData
        if (data != null && birth != null) developmentWeek(data, birth, today) else null
    }
    val newTips = child != null && hasNewTips(week, child.id, seenWeeks)
    LaunchedEffect(section, child?.id, week?.week) {
        if (section == MainSection.DEVELOPMENT && child != null && week != null) development.markSeen(child.id, week.week)
    }
    // Объявлен раньше «Назад» внутри разделов, поэтому срабатывает после них
    BackHandler(enabled = section != MainSection.TODAY) { openSection(MainSection.TODAY) }
    // Экран болезни закрывается раньше раздела: он открыт внутри него
    BackHandler(enabled = openIllness != null) { openIllnessId = NONE }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) viewModel.exportTo(uri.toString()) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.importFrom(uri.toString()) }

    Scaffold(
        topBar = {
            // Шапка сливается с фоном экрана. Слева — выбор ребёнка: «Дневник Маши» фирменным шрифтом,
            // как логотип в макете, под ним возраст, вес и рост (ADR-0035, ADR-0027)
            TopAppBar(
                title = {
                    if (child != null) {
                        ChildSelector(child, state.lastWeight, state.lastHeight, now, onClick = { showChildren = true })
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
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.guideTarget(GuideTarget.MENU_BUTTON, CircleShape)) {
                        BadgedBox(badge = { if (helpBadge) Badge() }) {
                            AppIcon(R.drawable.ic_more, contentDescription = if (helpBadge) "Меню, новое в справке" else "Меню")
                        }
                    }
                },
            )
        },
        bottomBar = { MainNavigationBar(section, onSelect = openSection, developmentBadge = newTips) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (child != null) {
            val actions = DiaryActions(
                onStartSleep = { startingSleep = true },
                onEndSleep = { viewModel.endSleep() },
                onStartFeeding = { startingFeeding = true },
                onEndFeeding = { viewModel.endFeeding() },
                onSwitchBreast = { viewModel.switchBreast() },
                onStartCrying = { viewModel.startCrying() },
                onEndCrying = {
                    state.activeCrying?.let { finishedCryingId = it.id }
                    viewModel.endCrying()
                },
                onAdd = { newEvent = it },
                // Болезнь из «Дневника» открывается своим экраном: даты правят там, карандашом
                onEditEvent = { if (it is Illness) openIllnessId = it.id else editingId = it.id },
                onDeleteEvent = { deletingId = it.id },
                onSelectDate = selectDay,
                onPickDate = { showDaySelector = true },
                onHealth = { healthSheets.menu = true },
                onTemperature = { healthSheets.temperature = true },
                onOpenIllness = { openIllnessId = it.id },
            )
            if (openIllness != null && (section == MainSection.TODAY || section == MainSection.DIARY)) {
                IllnessScreen(
                    illness = openIllness,
                    state = state,
                    child = child,
                    now = now,
                    sheets = healthSheets,
                    viewModel = viewModel,
                    onBack = { openIllnessId = NONE },
                    onEditRecord = { editingId = it.id },
                    onAddMedicine = { newEvent = NewEventKind.MEDICINE },
                    modifier = Modifier.padding(padding),
                )
            } else when (section) {
                MainSection.TODAY -> TodayContent(
                    state = state,
                    child = child,
                    now = now,
                    actions = actions,
                    modifier = Modifier.padding(padding),
                    helpPointer = helpPointer,
                    tipsBanner = week?.takeIf { it.allTips.isNotEmpty() }?.let { current ->
                        { WeekTipsBanner(current, isNew = newTips, onClick = { openSection(MainSection.DEVELOPMENT) }) }
                    },
                )
                MainSection.DIARY -> DiarySection(diaryView, onView = { diaryView = it }, modifier = Modifier.padding(padding)) { view ->
                    when (view) {
                        DiaryView.DAYS -> HistoryContent(state, child, now, selectedDate, actions)
                        DiaryView.NOTES -> {
                            val minuteNow = now / 60_000
                            val cryingWeek = remember(state.events, minuteNow) {
                                cryingWeekSummary(state.events, now, ZoneId.systemDefault())?.let { CryingWeek(it.perDay, it.note) }
                            }
                            NotesContent(
                                events = state.events,
                                sex = child.sex,
                                now = now,
                                photoCounts = state.photoCounts,
                                filter = notesFilter,
                                onFilter = { notesFilter = it },
                                cryingWeek = cryingWeek,
                                onOpenAnalytics = {
                                    openSection(MainSection.ANALYTICS)
                                    analyticsStart = AnalyticsSection.CRYING
                                },
                                onAddNote = { newEvent = NewEventKind.NOTE },
                                onEdit = { editingId = it.id },
                                onDelete = { deletingId = it.id },
                                onOpenIllness = { openIllnessId = it.id },
                            )
                        }
                    }
                }
                MainSection.DEVELOPMENT -> DevelopmentContent(
                    child = child,
                    data = developmentData,
                    today = today,
                    onSetBirthDate = { childSheet = ChildSheetMode.EDIT },
                    modifier = Modifier.padding(padding),
                )
                MainSection.ANALYTICS -> AnalyticsContent(
                    events = state.events,
                    child = child,
                    now = now,
                    // «В историю» и строка ритма открывают день в «Дневнике», вид «По дням»
                    onOpenDay = { day ->
                        selectDay(day)
                        diaryView = DiaryView.DAYS
                        openSection(MainSection.DIARY)
                    },
                    modifier = Modifier.padding(padding),
                    initialSection = analyticsStart,
                    onOpenTag = { tag ->
                        notesFilter = NotesFilter.Tag(tag)
                        diaryView = DiaryView.NOTES
                        openSection(MainSection.DIARY)
                    },
                )
            }
        }
    }

    if (menuOpen) {
        AppSheet(onDismiss = { menuOpen = false }, edgeToEdge = true) { closer ->
            // Пункт закрывает меню и только потом открывает следующий лист или выбор файла
            val pick = { action: () -> Unit -> closer.close { menuOpen = false; action() } }
            SheetMenuItem(R.drawable.ic_theme, "Тема", onClick = { pick { showThemeSheet = true } })
            // Переключатель меню не закрывает: видно, что включилось, а разрешение спросит система
            ShadeMenuItem(shadeToggle)
            // Пример дневника в гайде (ADR-0043) не выгружают и не дополняют: он исчезнет вместе с гайдом
            if (!example) {
                // Архив с записями и фото (ADR-0045); импорт берёт и архив, и CSV — у CSV в разных приложениях свой MIME-тип
                SheetMenuItem(R.drawable.ic_export, "Экспорт в файл", onClick = {
                    pick { exportLauncher.launch("baby-diary-${now.toLocalDate()}.zip") }
                })
                SheetMenuItem(R.drawable.ic_import, "Импорт из файла", onClick = { pick { importLauncher.launch(arrayOf("*/*")) } })
            }
            if (onOpenHelp != null && !example) {
                SheetMenuItem(R.drawable.ic_help, "Справка", onClick = { pick(onOpenHelp) }, badge = helpBadge)
            }
        }
    }

    if (showThemeSheet) {
        GuideSheet(GuideTarget.THEME_SHEET) {
            ThemeSheet(theme = theme, onSelect = onThemeChange, onDismiss = { showThemeSheet = false })
        }
    }

    recovery?.let {
        DatabaseRecoveredSheet(
            recovery = it,
            onDismiss = onRecoverySeen,
            onImport = {
                onRecoverySeen()
                importLauncher.launch(arrayOf("*/*"))
            },
        )
    }

    if (child == null) return

    newEvent?.let { kind ->
        val illnessMedicines = remember(state.events, state.activeIllness, now / 60_000) {
            state.activeIllness?.let { state.events.illnessMedicines(it, now) }.orEmpty()
        }
        NewEventSheet(
            kind = kind,
            child = child,
            pastMedicines = state.pastMedicines,
            noteTags = state.noteTags,
            onDismiss = { newEvent = null },
            onSave = { event, photos ->
                viewModel.saveEvent(event, photos)
                newEvent = null
            },
            illnessMedicines = illnessMedicines,
        )
    }

    HealthSheetsHost(
        sheets = healthSheets,
        state = state,
        child = child,
        now = now,
        viewModel = viewModel,
        onAddMedicine = { newEvent = NewEventKind.MEDICINE },
        onOpenIllness = { openIllnessId = it.id },
    )

    if (startingSleep) {
        SleepStartSheet(
            activeFeeding = state.activeFeeding,
            activeCrying = state.activeCrying,
            onDismiss = { startingSleep = false },
            onSave = {
                state.activeCrying?.let { finishedCryingId = it.id }
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
                GuideSheet(GuideTarget.EVENT_EDITOR) {
                    EditEventSheet(
                        event = event,
                        child = child,
                        photos = it,
                        pastMedicines = state.pastMedicines,
                        cryingChoices = state.cryingChoices,
                        noteTags = state.noteTags,
                        onDismiss = { editingId = NONE },
                        onSave = { edited, edit ->
                            viewModel.saveEvent(edited, edit)
                            editingId = NONE
                        },
                    )
                }
            }
        }
    }

    // Лист открывается, когда у плача уже есть конец: остановка записывается в базу асинхронно
    (state.events.find { it.id == finishedCryingId } as? Crying)?.takeIf { !it.isActive }?.let { crying ->
        key(crying.id) {
            CryingDoneSheet(
                crying = crying,
                context = remember(crying.id) { cryingContext(state.events, crying) },
                choices = state.cryingChoices,
                sex = child.sex,
                onResume = {
                    viewModel.resumeCrying(crying)
                    finishedCryingId = NONE
                }.takeIf { state.activeSleep == null },
                onDismiss = { finishedCryingId = NONE },
                onSave = { start, reasons, helped, note ->
                    viewModel.saveCryingDetails(crying, start, reasons, helped, note)
                    finishedCryingId = NONE
                },
            )
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

    if (showDaySelector) GuideSheet(GuideTarget.DAY_SELECTOR) {
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

    if (showChildren) GuideSheet(GuideTarget.CHILDREN_SHEET) {
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
