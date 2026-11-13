import SwiftUI

/** Тип новой записи, которую открывают быстрые плитки; редакторы подключаются своими сессиями. */
enum NewEventKind: Sendable {
    case diaper
    case weight
    case height
    case note
    case medicine
}

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
    var onHealth: () -> Void = {}
}

/**
 Раздел «Сегодня»: плитки сна и кормления в ряд, под ними плач с «Заметкой»,
 плитки быстрых записей и сводка за сегодня. Ребёнок — в шапке.
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
        DiaryList {
            if let helpPointer { helpPointer }
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
                onAdd: actions.onAdd,
                onHealth: actions.onHealth
            )
            DaySummaryCard(events: state.events, date: toLocalDate(now, timeZone: timeZone), now: now, sex: child.sex)
            if let tipsBanner { tipsBanner }
        }
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
