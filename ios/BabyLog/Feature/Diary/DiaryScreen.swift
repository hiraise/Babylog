import SwiftUI

private enum ChildSheetMode {
    case add
    case edit
}

/**
 Главный экран (ADR-0035): шапка с ребёнком, нижняя панель разделов и нижние листы.
 Тему применяет `RootView` (ADR-0017): экран только сообщает о новом выборе, не пересоздавая окно.
 Разделы, редакторы и справка подключаются своими сессиями переноса.
 */
struct DiaryScreen: View {
    @Environment(\.babyLogTheme) private var themeValues
    @Environment(\.clock) private var clock
    @Environment(\.timeZone) private var timeZone

    @State private var section: MainSection
    @State private var diaryView: DiaryView
    @State private var now: Int64?
    @State private var selectedDate: Date?
    @State private var showDaySelector = false
    @State private var showThemeSheet = false
    @State private var showChildren = false
    @State private var childSheet: ChildSheetMode?
    @State private var confirmChildDelete = false
    @State private var menuOpen = false
    @State private var showHelp = false
    @State private var showExporter = false
    @State private var showImporter = false
    @State private var showSleepStart = false
    @State private var showFeedingStart = false
    @State private var cryingDone: Crying?
    @State private var showHealthMenu = false
    @State private var showTemperature = false
    @State private var newEventKind: NewEventKind?

    let model: DiaryViewModel
    let theme: ThemeChoice
    let onThemeChange: (ThemeChoice) -> Void
    let recovery: DatabaseRecovery?
    let onRecoverySeen: () -> Void
    let developmentBadge: Bool
    let onOpenHelp: (() -> Void)?

    init(
        model: DiaryViewModel,
        theme: ThemeChoice,
        onThemeChange: @escaping (ThemeChoice) -> Void,
        initialSection: MainSection = .today,
        initialDiaryView: DiaryView = .days,
        recovery: DatabaseRecovery? = nil,
        onRecoverySeen: @escaping () -> Void = {},
        developmentBadge: Bool = false,
        onOpenHelp: (() -> Void)? = nil
    ) {
        self.model = model
        self.theme = theme
        self.onThemeChange = onThemeChange
        self.recovery = recovery
        self.onRecoverySeen = onRecoverySeen
        self.developmentBadge = developmentBadge
        self.onOpenHelp = onOpenHelp
        _section = State(initialValue: initialSection)
        _diaryView = State(initialValue: initialDiaryView)
    }

    var body: some View {
        let currentNow = now ?? clock.millis()
        let today = toLocalDate(currentNow, timeZone: timeZone)
        let days = historyDays(events: model.state.events, today: today, timeZone: timeZone)
        let selected = selectedDate ?? today

        VStack(spacing: 0) {
            header(now: currentNow)
            content(selected: selected, today: today, days: days)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(themeValues.colors.background)
            MainNavigationBar(
                selected: section,
                onSelect: { section = $0 },
                developmentBadge: developmentBadge
            )
        }
        .background(themeValues.colors.background.ignoresSafeArea())
        .foregroundStyle(themeValues.colors.onBackground)
        .overlay(alignment: .bottom) {
            if let message = model.state.message {
                HStack(spacing: themeValues.spacing.m) {
                    Text(message)
                        .appTextStyle(themeValues.typography.bodyMedium)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    if model.state.messageAction != nil {
                        Button("Отменить") {
                            Task { await model.consumeMessage(performed: true) }
                        }
                        .appTextStyle(themeValues.typography.labelLarge)
                    }
                }
                .padding(themeValues.spacing.l)
                .background(themeValues.colors.surfaceContainerHigh, in: RoundedRectangle(cornerRadius: themeValues.shapes.small))
                .padding(themeValues.spacing.l)
                .padding(.bottom, BabyLogSizes.navigationHeight)
                .cardShadow(shadow: themeValues.shadows.card)
            }
        }
        .task {
            while !Task.isCancelled {
                now = clock.millis()
                try? await Task.sleep(for: .seconds(1))
            }
        }
        .task(id: model.state.message) {
            guard model.state.message != nil else { return }
            try? await Task.sleep(for: .seconds(model.state.messageAction == nil ? 4 : 10))
            if !Task.isCancelled { await model.consumeMessage() }
        }
        .onChange(of: model.state.child?.id) { _, _ in
            selectedDate = nil
        }
        .diaryFileExporter(
            isPresented: $showExporter,
            defaultFilename: diaryArchiveFilename(currentNow, timeZone: timeZone)
        ) { result in
            if case let .success(url) = result {
                Task { await model.exportTo(url) }
            }
        }
        .diaryFileImporter(isPresented: $showImporter) { result in
            if case let .success(url) = result {
                Task { await model.importFrom(url) }
            }
        }

        if menuOpen {
            AppSheet(isPresented: $menuOpen, onDismiss: { menuOpen = false }, edgeToEdge: true) { closer in
                SheetMenuItem(icon: "ic_theme", label: "Тема") {
                    closer.close { showThemeSheet = true }
                }
                SheetMenuItem(icon: "ic_export", label: "Экспорт в файл") {
                    closer.close { showExporter = true }
                }
                SheetMenuItem(icon: "ic_import", label: "Импорт из файла") {
                    closer.close { showImporter = true }
                }
                SheetMenuItem(icon: "ic_help", label: "Справка") {
                    closer.close {
                        if let onOpenHelp { onOpenHelp() } else { showHelp = true }
                    }
                }
            }
        }
        if showThemeSheet {
            ThemeModeSheet(
                isPresented: $showThemeSheet,
                choice: theme,
                onSelect: onThemeChange,
                onDismiss: { showThemeSheet = false }
            )
        }
        if showHelp {
            AppSheet(
                isPresented: $showHelp,
                onDismiss: { showHelp = false },
                title: "Справка",
                icon: "ic_help"
            ) { _ in
                Text("Темы и интерактивные гайды появятся здесь.")
                    .appTextStyle(themeValues.typography.bodyMedium)
            }
        }
        if let recovery {
            DatabaseRecoveredSheet(
                isPresented: Binding(get: { true }, set: { if !$0 { onRecoverySeen() } }),
                recovery: recovery,
                onDismiss: onRecoverySeen,
                onImport: {
                    onRecoverySeen()
                    showImporter = true
                }
            )
        }
        if showChildren, let child = model.state.child {
            ChildrenSheet(
                isPresented: $showChildren,
                children: model.state.children,
                selectedId: child.id,
                now: currentNow,
                onSelect: { model.selectChild($0) },
                onEdit: { childSheet = .edit },
                onAdd: { childSheet = .add },
                onDismiss: { showChildren = false }
            )
        }
        if let childSheet {
            ChildSheet(
                isPresented: Binding(
                    get: { true },
                    set: { if !$0 { self.childSheet = nil } }
                ),
                initial: childSheet == .edit ? model.state.child : nil,
                children: model.state.children,
                onDismiss: { self.childSheet = nil },
                onSave: { name, birthDate, sex, photo in
                    let mode = childSheet
                    self.childSheet = nil
                    Task {
                        if mode == .add {
                            await model.addChild(name: name, birthDate: birthDate, sex: sex, photo: photo)
                        } else if var child = model.state.child {
                            child.name = name
                            child.birthDate = birthDate
                            child.sex = sex
                            child.photo = photo
                            await model.updateChild(child)
                        }
                    }
                },
                onDelete: childSheet == .edit && model.state.children.count > 1 ? {
                    confirmChildDelete = true
                } : nil
            )
        }
        if confirmChildDelete, let child = model.state.child {
            AppSheet(
                isPresented: $confirmChildDelete,
                onDismiss: { confirmChildDelete = false },
                title: "Удалить «\(child.name)»?",
                icon: "ic_delete",
                confirm: SheetAction(label: "Удалить", destructive: true) {
                    Task { await model.deleteChild(child) }
                    confirmChildDelete = false
                }
            ) { _ in
                Text("Записи этого ребёнка (\(model.state.events.count)) тоже будут удалены. Отменить это нельзя.")
                    .appTextStyle(themeValues.typography.bodyMedium)
            }
        }
        if showDaySelector {
            DaySelectorSheet(
                isPresented: $showDaySelector,
                days: days,
                events: model.state.events,
                selectedDate: selected,
                today: today,
                onDismiss: { showDaySelector = false },
                onPick: { day in
                    selectedDate = day == today ? nil : day
                }
            )
        }
        if showSleepStart {
            SleepStartSheet(
                isPresented: $showSleepStart,
                activeFeeding: model.state.activeFeeding,
                activeCrying: model.state.activeCrying,
                onDismiss: { showSleepStart = false },
                onSave: { time in
                    showSleepStart = false
                    Task {
                        if let crying = await model.startSleep(start: time) { cryingDone = crying }
                    }
                }
            )
        }
        if showFeedingStart {
            FeedingStartSheet(
                isPresented: $showFeedingStart,
                activeSleep: model.state.activeSleep,
                lastBottle: model.state.lastBottle,
                onDismiss: { showFeedingStart = false },
                onSave: { breast, time in
                    showFeedingStart = false
                    Task { await model.startFeeding(start: time, breast: breast) }
                },
                onSaveBottle: { volume, milk, time in
                    showFeedingStart = false
                    Task { await model.addBottle(volumeMl: volume, milk: milk, time: time) }
                }
            )
        }
        if let crying = cryingDone {
            CryingDoneSheet(
                isPresented: Binding(get: { true }, set: { if !$0 { cryingDone = nil } }),
                crying: crying,
                context: cryingContext(events: model.state.events, crying: crying),
                choices: cryingChoices(events: model.state.events),
                sex: model.state.child?.sex,
                onResume: model.state.activeSleep == nil ? {
                    cryingDone = nil
                    Task { await model.resumeCrying(crying) }
                } : nil,
                onDismiss: { cryingDone = nil },
                onSave: { start, reasons, helped, note in
                    cryingDone = nil
                    Task { await model.saveCryingDetails(crying, start: start, reasons: reasons, helped: helped, note: note) }
                }
            )
        }
        if showHealthMenu {
            HealthSheet(
                isPresented: $showHealthMenu,
                state: model.state,
                now: currentNow,
                onDismiss: { showHealthMenu = false },
                onMedicine: {
                    showHealthMenu = false
                    newEventKind = .medicine
                },
                onTemperature: {
                    showHealthMenu = false
                    showTemperature = true
                }
            )
        }
        if showTemperature, let child = model.state.child {
            TemperatureSheet(
                isPresented: $showTemperature,
                initial: nil,
                last: model.state.lastTemperature,
                sex: child.sex,
                offerIllness: model.state.activeIllness == nil,
                onDismiss: { showTemperature = false },
                onSave: { tenths, method, time, note, startIllness in
                    showTemperature = false
                    Task {
                        await model.saveTemperature(
                            Temperature(childId: child.id, start: time, tenths: tenths, method: method, note: note),
                            startIllness: startIllness
                        )
                    }
                },
                onSaveWithMedicine: model.state.activeIllness == nil ? nil : { tenths, method, time, note in
                    showTemperature = false
                    Task {
                        await model.saveTemperature(
                            Temperature(childId: child.id, start: time, tenths: tenths, method: method, note: note),
                            startIllness: false
                        )
                        newEventKind = .medicine
                    }
                }
            )
        }
        if let kind = newEventKind, let child = model.state.child {
            NewEventSheet(
                isPresented: Binding(get: { true }, set: { if !$0 { newEventKind = nil } }),
                kind: kind,
                child: child,
                events: model.state.events,
                onDismiss: { newEventKind = nil },
                onSave: { event, photos in
                    newEventKind = nil
                    Task { await model.saveEvent(event, photos: photos) }
                }
            )
        }
    }

    @ViewBuilder private func header(now: Int64) -> some View {
        HStack(spacing: themeValues.spacing.s) {
            if let child = model.state.child {
                ChildSelector(
                    child: child,
                    lastWeight: model.state.lastWeight,
                    lastHeight: model.state.lastHeight,
                    now: now,
                    onClick: { showChildren = true }
                )
                .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                Text("Дневник малыша")
                    .appTextStyle(themeValues.typography.headlineSmall)
                    .foregroundStyle(themeValues.colors.primary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            Button { menuOpen = true } label: {
                AppIcon("ic_more", contentDescription: "Меню")
                    .frame(width: BabyLogSizes.touchTarget, height: BabyLogSizes.touchTarget)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, themeValues.spacing.l)
        .frame(minHeight: BabyLogSizes.rowMinHeight)
        .background(themeValues.colors.background)
    }

    @ViewBuilder private func content(selected: Date, today: Date, days: [Date]) -> some View {
        switch section {
        case .today:
            if let child = model.state.child {
                TodayContent(
                    state: model.state,
                    child: child,
                    now: now ?? clock.millis(),
                    actions: DiaryActions(
                        onStartSleep: { showSleepStart = true },
                        onEndSleep: { Task { await model.endSleep() } },
                        onStartFeeding: { showFeedingStart = true },
                        onEndFeeding: { Task { await model.endFeeding() } },
                        onSwitchBreast: { Task { await model.switchBreast() } },
                        onStartCrying: { Task { await model.startCrying() } },
                        onEndCrying: {
                            Task {
                                if let crying = await model.endCrying() { cryingDone = crying }
                            }
                        },
                        onAdd: { kind in
                            newEventKind = kind
                        },
                        onHealth: { showHealthMenu = true }
                    )
                )
            } else {
                SectionPlaceholder(title: "Сегодня", text: "Добавьте ребёнка, чтобы начать дневник.")
            }
        case .diary:
            DiarySection(view: diaryView, onView: { diaryView = $0 }) { view in
                switch view {
                case .days:
                    VStack(spacing: 0) {
                        DayNavRow(
                            selectedDate: selected,
                            today: today,
                            hasPrev: days.first.map { selected > $0 } ?? false,
                            hasNext: days.last.map { selected < $0 } ?? false,
                            onPrev: { moveDay(days: days, selected: selected, offset: -1) },
                            onNext: { moveDay(days: days, selected: selected, offset: 1) },
                            onPick: { showDaySelector = true }
                        )
                        SectionPlaceholder(title: "День", text: "Сводка и записи выбранного дня появятся здесь.")
                    }
                case .notes:
                    SectionPlaceholder(title: "Заметки", text: "Плач и заметки появятся здесь.")
                }
            }
        case .development:
            SectionPlaceholder(title: "Развитие", text: "Советы и навыки появятся здесь.")
        case .analytics:
            SectionPlaceholder(title: "Аналитика", text: "Графики и сводки появятся здесь.")
        }
    }

    private func moveDay(days: [Date], selected: Date, offset: Int) {
        guard let index = days.firstIndex(of: selected) else { return }
        let target = index + offset
        guard days.indices.contains(target) else { return }
        selectedDate = days[target]
    }

}

private struct SectionPlaceholder: View {
    @Environment(\.babyLogTheme) private var theme
    let title: String
    let text: String

    var body: some View {
        VStack(spacing: theme.spacing.m) {
            AppIcon("ic_diary", size: 40, tint: theme.colors.primary)
            Text(title).appTextStyle(theme.typography.titleLarge)
            Text(text)
                .appTextStyle(theme.typography.bodyMedium)
                .foregroundStyle(theme.colors.onSurfaceVariant)
                .multilineTextAlignment(.center)
        }
        .padding(theme.spacing.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private struct ThemeModeSheet: View {
    @Environment(\.babyLogTheme) private var theme
    @Binding var isPresented: Bool
    let choice: ThemeChoice
    let onSelect: (ThemeChoice) -> Void
    let onDismiss: () -> Void

    var body: some View {
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            title: "Тема",
            icon: "ic_theme",
            edgeToEdge: true
        ) { _ in
            VStack(spacing: 0) {
                modeRow(.system, "Как в системе")
                modeRow(.light, "Светлая")
                modeRow(.dark, "Тёмная")
            }
        }
    }

    private func modeRow(_ mode: ThemeMode, _ title: String) -> some View {
        Button {
            var selected = choice
            selected.mode = mode
            onSelect(selected)
        } label: {
            HStack(spacing: theme.spacing.m) {
                Image(systemName: choice.mode == mode ? "largecircle.fill.circle" : "circle")
                    .foregroundStyle(theme.colors.primary)
                Text(title).appTextStyle(theme.typography.bodyLarge)
                Spacer()
            }
            .padding(.horizontal, theme.spacing.l)
            .frame(maxWidth: .infinity, minHeight: BabyLogSizes.menuMinHeight)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(choice.mode == mode ? [.isSelected] : [])
    }
}

func diaryArchiveFilename(_ millis: Int64, timeZone: TimeZone) -> String {
    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = timeZone
    formatter.dateFormat = "yyyy-MM-dd"
    return "baby-diary-\(formatter.string(from: Date(timeIntervalSince1970: Double(millis) / 1000))).zip"
}
