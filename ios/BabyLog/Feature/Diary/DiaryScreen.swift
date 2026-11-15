import SwiftUI

private enum ChildSheetMode {
    case add
    case edit
}

/**
 Главный экран (ADR-0035): шапка с ребёнком, нижняя панель разделов и нижние листы.
 Тему применяет `RootView` (ADR-0017): экран только сообщает о новом выборе, не пересоздавая окно.
 Справка (ADR-0043) — последний пункт меню, карточка «Где справка» — `helpPointer`.
 `example` — пример в гайде: без экспорта, импорта и перехода в справку.
 */
struct DiaryScreen: View {
    @Environment(\.babyLogTheme) private var themeValues
    @Environment(\.clock) private var clock
    @Environment(\.timeZone) private var timeZone

    @State private var navigation: DiaryNavigationState
    @State private var now: Int64?
    @State private var selectedDate: Date?
    @State private var showDaySelector = false
    @State private var showThemeSheet = false
    @State private var showChildren = false
    @State private var childSheet: ChildSheetMode?
    @State private var confirmChildDelete = false
    @State private var menuOpen = false
    @State private var showExporter = false
    @State private var showImporter = false
    @State private var showSleepStart = false
    @State private var showFeedingStart = false
    @State private var cryingDone: Crying?
    @State private var healthSheets = HealthSheets()
    @State private var openIllnessId: Int64?
    @State private var editingId: Int64?
    @State private var editingPhotos: [EventPhoto]?
    @State private var deletingEventId: Int64?
    @State private var newEventKind: NewEventKind?

    let model: DiaryViewModel
    let theme: ThemeChoice
    let onThemeChange: (ThemeChoice) -> Void
    let development: DevelopmentViewModel?
    let recovery: DatabaseRecovery?
    let onRecoverySeen: () -> Void
    let developmentBadge: Bool
    let example: Bool
    let onOpenHelp: (() -> Void)?
    let helpBadge: Bool
    let helpPointer: AnyView?

    init(
        model: DiaryViewModel,
        theme: ThemeChoice,
        onThemeChange: @escaping (ThemeChoice) -> Void,
        development: DevelopmentViewModel? = nil,
        initialSection: MainSection = .today,
        initialDiaryView: DiaryView = .days,
        initialNotesFilter: NotesFilter = .all,
        recovery: DatabaseRecovery? = nil,
        onRecoverySeen: @escaping () -> Void = {},
        developmentBadge: Bool = false,
        example: Bool = false,
        onOpenHelp: (() -> Void)? = nil,
        helpBadge: Bool = false,
        helpPointer: AnyView? = nil
    ) {
        self.model = model
        self.theme = theme
        self.onThemeChange = onThemeChange
        self.development = development
        self.recovery = recovery
        self.onRecoverySeen = onRecoverySeen
        self.developmentBadge = developmentBadge
        self.example = example
        self.onOpenHelp = onOpenHelp
        self.helpBadge = helpBadge
        self.helpPointer = helpPointer
        _navigation = State(initialValue: DiaryNavigationState(
            section: initialSection,
            diaryView: initialDiaryView,
            notesFilter: initialNotesFilter
        ))
    }

    var body: some View {
        let currentNow = now ?? clock.millis()
        let today = toLocalDate(currentNow, timeZone: timeZone)
        let days = historyDays(events: model.state.events, today: today, timeZone: timeZone)
        let selected = selectedDate ?? today
        let tipsWeek = model.state.child.map {
            currentDevelopmentWeek(data: development?.data, child: $0, today: today, timeZone: timeZone)
        } ?? nil
        let newTips = model.state.child.map {
            hasNewTips(week: tipsWeek, childId: $0.id, seenWeeks: development?.seenWeeks ?? [:])
        } ?? false

        VStack(spacing: 0) {
            header(now: currentNow)
            content(selected: selected, today: today, days: days)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(themeValues.colors.background)
            MainNavigationBar(
                selected: section,
                onSelect: {
                    navigation.select($0)
                    openIllnessId = nil
                },
                developmentBadge: developmentBadge || newTips
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
            openIllnessId = nil
        }
        .task(id: section == .development ? "\(model.state.child?.id ?? 0):\(tipsWeek?.week ?? 0)" : "") {
            if section == .development, let child = model.state.child, let tipsWeek {
                development?.markSeen(childId: child.id, week: tipsWeek.week)
            }
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
                if !example {
                    SheetMenuItem(icon: "ic_export", label: "Экспорт в файл") {
                        closer.close { showExporter = true }
                    }
                    SheetMenuItem(icon: "ic_import", label: "Импорт из файла") {
                        closer.close { showImporter = true }
                    }
                }
                if let onOpenHelp, !example {
                    SheetMenuItem(icon: "ic_help", label: "Справка", badge: helpBadge) {
                        closer.close { onOpenHelp() }
                    }
                }
            }
        }
        if showThemeSheet {
            GuideSheet(target: .themeSheet) {
                ThemeSheet(
                    isPresented: $showThemeSheet,
                    theme: theme,
                    onSelect: onThemeChange,
                    onDismiss: { showThemeSheet = false }
                )
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
            GuideSheet(target: .childrenSheet) {
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
            GuideSheet(target: .daySelector) {
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
        if let child = model.state.child {
            HealthSheetsHost(
                sheets: healthSheets,
                state: model.state,
                child: child,
                now: currentNow,
                model: model,
                onAddMedicine: { newEventKind = .medicine },
                onOpenIllness: { openIllnessId = $0.id }
            )
        }
        if let deletingEventId, let child = model.state.child,
           let event = model.state.events.first(where: { $0.id == deletingEventId }) {
            AppSheet(
                isPresented: Binding(get: { true }, set: { if !$0 { self.deletingEventId = nil } }),
                onDismiss: { self.deletingEventId = nil },
                title: "Удалить запись?",
                icon: "ic_delete",
                confirm: SheetAction(label: "Удалить", destructive: true) {
                    self.deletingEventId = nil
                    Task { await model.deleteEvent(event) }
                }
            ) { _ in
                Text(eventTitle(event, sex: child.sex))
                    .appTextStyle(themeValues.typography.bodyMedium)
            }
        }
        if let editingId, let editingPhotos, let child = model.state.child,
           let event = model.state.events.first(where: { $0.id == editingId }) {
            GuideSheet(target: .eventEditor) {
                EditEventSheet(
                    isPresented: Binding(get: { true }, set: { if !$0 { self.editingId = nil; self.editingPhotos = nil } }),
                    event: event,
                    child: child,
                    events: model.state.events,
                    photos: editingPhotos,
                    cryingChoices: cryingChoices(events: model.state.events),
                    onDismiss: { self.editingId = nil; self.editingPhotos = nil },
                    onSave: { edited, photos in
                        self.editingId = nil
                        self.editingPhotos = nil
                        Task { await model.saveEvent(edited, photos: photos) }
                    }
                )
            }
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
                AppIcon("ic_more", contentDescription: helpBadge ? "Меню, новое в справке" : "Меню")
                    .frame(width: BabyLogSizes.touchTarget, height: BabyLogSizes.touchTarget)
                    .overlay(alignment: .topTrailing) {
                        if helpBadge {
                            Circle()
                                .fill(themeValues.colors.primary)
                                .frame(width: BabyLogSizes.badge, height: BabyLogSizes.badge)
                                .offset(x: themeValues.spacing.xs)
                        }
                    }
            }
            .buttonStyle(.plain)
            .guideTarget(.menuButton, shape: .circle)
        }
        .padding(.horizontal, themeValues.spacing.l)
        .frame(minHeight: BabyLogSizes.rowMinHeight)
        .background(themeValues.colors.background)
    }

    @ViewBuilder private func content(selected: Date, today: Date, days: [Date]) -> some View {
        let openIllness = model.state.events.compactMap { $0 as? Illness }.first { $0.id == openIllnessId }
        let tipsWeek = model.state.child.map {
            currentDevelopmentWeek(data: development?.data, child: $0, today: today, timeZone: timeZone)
        } ?? nil
        let newTips = model.state.child.map {
            hasNewTips(week: tipsWeek, childId: $0.id, seenWeeks: development?.seenWeeks ?? [:])
        } ?? false
        if let openIllness, let child = model.state.child, section == .today || section == .diary {
            IllnessScreen(
                illness: openIllness,
                state: model.state,
                child: child,
                now: now ?? clock.millis(),
                sheets: healthSheets,
                model: model,
                onBack: { openIllnessId = nil },
                onEditRecord: edit,
                onAddMedicine: { newEventKind = .medicine }
            )
        } else {
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
                            onAdd: { newEventKind = $0 },
                            onEditEvent: edit,
                            onHealth: { healthSheets.menu = true },
                            onTemperature: { healthSheets.temperature = true },
                            onOpenIllness: { openIllnessId = $0.id }
                        ),
                        tipsBanner: makeTipsBanner(week: tipsWeek, isNew: newTips),
                        helpPointer: helpPointer
                    )
                } else {
                    SectionPlaceholder(title: "Сегодня", text: "Добавьте ребёнка, чтобы начать дневник.")
                }
            case .diary:
                if let child = model.state.child {
                    DiarySection(view: diaryView, onView: { navigation.diaryView = $0 }) { view in
                        switch view {
                        case .days:
                            HistoryContent(
                                state: model.state,
                                child: child,
                                now: now ?? clock.millis(),
                                selectedDate: selected,
                                actions: DiaryActions(
                                    onEditEvent: edit,
                                    onDeleteEvent: { deletingEventId = $0.id },
                                    onSelectDate: { selectedDate = $0 == today ? nil : $0 },
                                    onPickDate: { showDaySelector = true }
                                )
                            )
                        case .notes:
                            NotesContent(
                                events: model.state.events,
                                sex: child.sex,
                                now: now ?? clock.millis(),
                                photoCounts: model.state.photoCounts,
                                filter: navigation.notesFilter,
                                onFilter: { navigation.notesFilter = $0 },
                                cryingWeek: cryingWeek(events: model.state.events, now: now ?? clock.millis(), timeZone: timeZone),
                                onOpenAnalytics: { navigation.openCryingAnalytics() },
                                onAddNote: { newEventKind = .note },
                                onEdit: edit,
                                onDelete: { deletingEventId = $0.id },
                                onOpenIllness: { openIllnessId = $0.id }
                            )
                        }
                    }
                } else {
                    SectionPlaceholder(title: "Дневник", text: "Добавьте ребёнка, чтобы начать дневник.")
                }
            case .development:
                if let child = model.state.child {
                    DevelopmentContent(
                        child: child,
                        data: development?.data,
                        today: today,
                        onSetBirthDate: { childSheet = .edit }
                    )
                } else {
                    SectionPlaceholder(title: "Развитие", text: "Добавьте ребёнка, чтобы увидеть советы и навыки.")
                }
            case .analytics:
                if let child = model.state.child {
                    AnalyticsContent(
                        events: model.state.events,
                        child: child,
                        now: now ?? clock.millis(),
                        onOpenDay: { day in
                            selectedDate = day == today ? nil : day
                            navigation.openDay()
                        },
                        initialSection: navigation.analyticsSection,
                        onOpenTag: { navigation.openTag($0) }
                    )
                } else {
                    SectionPlaceholder(title: "Аналитика", text: "Добавьте ребёнка, чтобы начать дневник.")
                }
            }
        }
    }

    private var section: MainSection { navigation.section }
    private var diaryView: DiaryView { navigation.diaryView }

    private func makeTipsBanner(week: DevelopmentWeek?, isNew: Bool) -> AnyView? {
        guard let week, !week.allTips.isEmpty else { return nil }
        return AnyView(WeekTipsBanner(week: week, isNew: isNew) {
            navigation.select(.development)
            openIllnessId = nil
        })
    }

    private func edit(_ event: any BabyEvent) {
        Task {
            let photos = await model.eventPhotos(event.id)
            editingPhotos = photos
            editingId = event.id
        }
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


func diaryArchiveFilename(_ millis: Int64, timeZone: TimeZone) -> String {
    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = timeZone
    formatter.dateFormat = "yyyy-MM-dd"
    return "baby-diary-\(formatter.string(from: Date(timeIntervalSince1970: Double(millis) / 1000))).zip"
}
