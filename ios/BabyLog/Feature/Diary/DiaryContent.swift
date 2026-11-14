import SwiftUI

/** Действия пользователя на главном экране. */
struct DiaryActions {
    var onStartSleep: () -> Void = {}
    var onEndSleep: () -> Void = {}
    var onStartFeeding: () -> Void = {}
    var onEndFeeding: () -> Void = {}
    var onSwitchBreast: () -> Void = {}
    var onStartCrying: () -> Void = {}
    var onEndCrying: () -> Void = {}
    var onAdd: (NewEventKind) -> Void = { _ in }
    var onEditEvent: (any BabyEvent) -> Void = { _ in }
    var onDeleteEvent: (any BabyEvent) -> Void = { _ in }
    var onSelectDate: (Date) -> Void = { _ in }
    var onPickDate: () -> Void = {}
    /** Плитка «Здоровье», «Температура» на карточке болезни и её заголовок (ADR-0040). */
    var onHealth: () -> Void = {}
    var onTemperature: () -> Void = {}
    var onOpenIllness: (Illness) -> Void = { _ in }
}

/**
 Раздел «Сегодня»: плитки сна и кормления в ряд, под ними плач с «Заметкой»,
 плитки быстрых записей и сводка за сегодня. Ребёнок — в шапке. Пока ребёнок болеет, над таймерами —
 карточка болезни (`IllnessCard`, ADR-0040): ночью за ней и открывают «Сегодня».
 */
struct TodayContent: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.timeZone) private var timeZone

    let state: DiaryUiState
    let child: Child
    let now: Int64
    let actions: DiaryActions
    let tipsBanner: AnyView?
    let helpPointer: AnyView?

    init(
        state: DiaryUiState,
        child: Child,
        now: Int64,
        actions: DiaryActions,
        tipsBanner: AnyView? = nil,
        helpPointer: AnyView? = nil
    ) {
        self.state = state
        self.child = child
        self.now = now
        self.actions = actions
        self.tipsBanner = tipsBanner
        self.helpPointer = helpPointer
    }

    var body: some View {
        let illnessCard = state.activeIllness.map {
            illnessCardModel(events: state.events, illness: $0, now: now, timeZone: timeZone)
        }
        DiaryList {
            if let helpPointer { helpPointer }
            if let illnessCard {
                IllnessCard(
                    model: illnessCard,
                    now: now,
                    onOpen: { actions.onOpenIllness(illnessCard.illness) },
                    onTemperature: actions.onTemperature,
                    onMedicine: { actions.onAdd(.medicine) },
                    onNote: { actions.onAdd(.note) }
                )
            }
            VStack(spacing: theme.spacing.s) {
                HStack(spacing: theme.spacing.s) {
                    SleepCard(
                        child: child,
                        activeSleep: state.activeSleep,
                        lastSleepEnd: state.lastSleepEnd,
                        now: now,
                        onStart: actions.onStartSleep,
                        onEnd: actions.onEndSleep
                    )
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    FeedingCard(
                        activeFeeding: state.activeFeeding,
                        lastFeeding: state.lastFeeding,
                        now: now,
                        onStart: actions.onStartFeeding,
                        onEnd: actions.onEndFeeding,
                        onSwitchBreast: actions.onSwitchBreast
                    )
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                CryingNoteRow(
                    activeCrying: state.activeCrying,
                    lastCrying: state.lastCrying,
                    now: now,
                    onStartCrying: actions.onStartCrying,
                    onEndCrying: actions.onEndCrying,
                    onAddNote: { actions.onAdd(.note) }
                )
            }
            QuickActions(
                lastDiaper: state.lastDiaper,
                lastWeight: state.lastWeight?.start,
                lastHeight: state.lastHeight?.start,
                sleeping: state.activeSleep != nil,
                now: now,
                illnessDay: illnessCard?.day,
                onAdd: actions.onAdd,
                onHealth: actions.onHealth
            )
            DaySummaryCard(events: state.events, date: toLocalDate(now, timeZone: timeZone), now: now, sex: child.sex)
            if let tipsBanner { tipsBanner }
        }
    }
}

/** «Дневник», вид «По дням» (ADR-0035): выбор дня, сводка выбранного дня и его записи. Свайп листает дни. */
struct HistoryContent: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.timeZone) private var timeZone

    let state: DiaryUiState
    let child: Child
    let now: Int64
    let selectedDate: Date
    let actions: DiaryActions

    var body: some View {
        let today = toLocalDate(now, timeZone: timeZone)
        let days = historyDays(events: state.events, today: today, timeZone: timeZone)
        let dayEvents = state.events.filter { toLocalDate($0.start, timeZone: timeZone) == selectedDate }
        let prevDay = days.last { $0 < selectedDate }
        let nextDay = days.first { $0 > selectedDate }
        let emptyText = state.events.isEmpty
            ? "Пока нет записей.\nОтмечайте сон, кормления, подгузники и лекарства в разделе «Сегодня»."
            : "Нет записей за этот день"

        DiaryList {
            DayNavRow(
                selectedDate: selectedDate,
                today: today,
                hasPrev: prevDay != nil,
                hasNext: nextDay != nil,
                onPrev: { if let prevDay { actions.onSelectDate(prevDay) } },
                onNext: { if let nextDay { actions.onSelectDate(nextDay) } },
                onPick: actions.onPickDate
            )
            DaySummaryCard(events: state.events, date: selectedDate, now: now, sex: child.sex)
            if dayEvents.isEmpty {
                Text(emptyText)
                    .appTextStyle(theme.typography.bodyMedium)
                    .foregroundStyle(theme.colors.onSurfaceVariant)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, theme.spacing.xxl)
            }
            ForEach(dayEvents, id: \.id) { event in
                EventRow(
                    event: event,
                    sex: child.sex,
                    photoCount: state.photoCounts[event.id] ?? 0,
                    onEdit: { actions.onEditEvent(event) },
                    onDelete: { actions.onDeleteEvent(event) }
                )
            }
        }
        .gesture(DragGesture(minimumDistance: 20).onEnded { value in
            if value.translation.width > 90, let prevDay {
                actions.onSelectDate(prevDay)
            } else if value.translation.width < -90, let nextDay {
                actions.onSelectDate(nextDay)
            }
        })
    }
}

/** Лента раздела: карточки с общими отступами экрана. */
struct DiaryList<Content: View>: View {
    @Environment(\.babyLogTheme) private var theme
    @ViewBuilder let content: Content

    var body: some View {
        ScrollView {
            LazyVStack(spacing: theme.spacing.m) { content }
                .padding(EdgeInsets(
                    top: theme.spacing.m,
                    leading: theme.spacing.l,
                    bottom: theme.spacing.xl,
                    trailing: theme.spacing.l
                ))
        }
    }
}
