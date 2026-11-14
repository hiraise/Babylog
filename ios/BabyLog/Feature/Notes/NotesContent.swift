import SwiftUI

/** Снизу ленты — место под кнопку «Заметка»: последняя запись не прячется под ней. */
private let fabClearance: CGFloat = 88

/**
 Плач за неделю над лентой — числа из «Аналитики»: `perDay` — «11 мин»,
 `note` — «0,7 раза в сутки · чаще с 19 до 22 ч». Касание открывает её раздел «Плач».
 */
struct CryingWeek: Hashable, Sendable {
    var perDay: String
    var note: String?
}

/** Плач за последние семь суток, включая сегодня; nil — за неделю плача не было. */
func cryingWeek(events: [any BabyEvent], now: Int64, timeZone: TimeZone) -> CryingWeek? {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = timeZone
    let today = toLocalDate(now, timeZone: timeZone)
    guard let first = calendar.date(byAdding: .day, value: -6, to: today),
          let end = calendar.date(byAdding: .day, value: 1, to: today) else { return nil }
    let fromMillis = Int64(first.timeIntervalSince1970 * 1000)
    let untilMillis = min(Int64(end.timeIntervalSince1970 * 1000), now)
    let cryings = events.compactMap { $0 as? Crying }.filter { $0.start < untilMillis && ($0.end ?? now) >= fromMillis }
    guard !cryings.isEmpty else { return nil }
    let total = cryings.reduce(Int64.zero) { result, crying in
        let start = max(crying.start, fromMillis)
        let end = min(crying.end ?? now, untilMillis)
        return result + max(end - start, 0)
    }
    let starts = cryings.filter { $0.start >= fromMillis && $0.start < untilMillis }
    let rate = String(format: "%.1f", locale: RU, Double(starts.count) / 7)
    var note = "\(rate) раза в сутки"
    if let peak = peakHours(starts.map {
        let components = calendar.dateComponents([.hour, .minute], from: Date(timeIntervalSince1970: Double($0.start) / 1000))
        return (components.hour ?? 0) * 60 + (components.minute ?? 0)
    }) {
        note += " · чаще с \(peak.from) до \(peak.to) ч"
    }
    return CryingWeek(perDay: formatDuration(total / 7), note: note)
}

/**
 Фильтр ленты сохраняется строкой: метка — с «#» впереди, остальное — словом. Так метка
 «плач» или «#» не спутается с фильтром: метки бывают любые, кроме пустых.
 */
extension NotesFilter {
    var savedKey: String {
        switch self {
        case .all: ""
        case .cryingOnly: "crying"
        case .notesOnly: "notes"
        case .illnesses: "illnesses"
        case let .tag(tag): "#\(tag)"
        }
    }

    init(savedKey: String) {
        if savedKey.hasPrefix("#") {
            self = .tag(String(savedKey.dropFirst()))
        } else {
            switch savedKey {
            case "crying": self = .cryingOnly
            case "notes": self = .notesOnly
            case "illnesses": self = .illnesses
            default: self = .all
            }
        }
    }
}

/**
 Вид «Заметки» (ADR-0031): лента плача и заметок по дням, свежие сверху. Над ней — плач за неделю
 и фильтры. Выбранная метка — сводка и записи с ней; метку выбирают и касанием метки на записи.
 Касание записи — правка, корзина — удаление, как в «Истории». «Заметка» внизу — новая заметка.
 Фильтр «Болезни» — вместо ленты болезни карточками (ADR-0040).
 */
struct NotesContent: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.timeZone) private var timeZone

    let events: [any BabyEvent]
    let sex: Sex?
    let now: Int64
    let photoCounts: [Int64: Int]
    let filter: NotesFilter
    let onFilter: (NotesFilter) -> Void
    let cryingWeek: CryingWeek?
    let onOpenAnalytics: () -> Void
    let onAddNote: () -> Void
    let onEdit: (any BabyEvent) -> Void
    let onDelete: (any BabyEvent) -> Void
    let onOpenIllness: (Illness) -> Void

    init(
        events: [any BabyEvent],
        sex: Sex?,
        now: Int64,
        photoCounts: [Int64: Int] = [:],
        filter: NotesFilter,
        onFilter: @escaping (NotesFilter) -> Void,
        cryingWeek: CryingWeek?,
        onOpenAnalytics: @escaping () -> Void,
        onAddNote: @escaping () -> Void,
        onEdit: @escaping (any BabyEvent) -> Void,
        onDelete: @escaping (any BabyEvent) -> Void,
        onOpenIllness: @escaping (Illness) -> Void = { _ in }
    ) {
        self.events = events
        self.sex = sex
        self.now = now
        self.photoCounts = photoCounts
        self.filter = filter
        self.onFilter = onFilter
        self.cryingWeek = cryingWeek
        self.onOpenAnalytics = onOpenAnalytics
        self.onAddNote = onAddNote
        self.onEdit = onEdit
        self.onDelete = onDelete
        self.onOpenIllness = onOpenIllness
    }

    var body: some View {
        let today = toLocalDate(now, timeZone: timeZone)
        let feed = notesFeed(events: events, filter: filter, timeZone: timeZone)
        let tags = filterTags(events: events)
        let summary: TagSummary? = if case let .tag(tag) = filter {
            tagSummary(events: events, tag: tag, now: now, timeZone: timeZone)
        } else {
            nil
        }
        let hasCrying = events.contains { $0 is Crying }
        let hasNotes = events.contains { $0 is Note }
        let illnesses = illnessEntries(events: events, now: now, timeZone: timeZone)

        ZStack(alignment: .bottomTrailing) {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: theme.spacing.m) {
                        Color.clear.frame(height: 0).id("notes-top")
                        if let cryingWeek, filter != .illnesses, summary == nil {
                            CryingWeekCard(week: cryingWeek, onOpen: onOpenAnalytics)
                        }
                        if !tags.isEmpty || hasCrying || !illnesses.isEmpty {
                            FilterRow(
                                filter: filter,
                                tags: tags,
                                hasCrying: hasCrying,
                                hasNotes: hasNotes,
                                hasIllnesses: !illnesses.isEmpty,
                                onFilter: onFilter
                            )
                        }
                        if filter == .illnesses {
                            IllnessEntriesView(entries: illnesses, today: today, onOpen: onOpenIllness)
                        }
                        if let summary {
                            TagSummaryItems(
                                summary: summary,
                                sex: sex,
                                records: feed.reduce(0) { $0 + $1.records.count },
                                onTag: { onFilter(.tag($0)) }
                            )
                        }
                        if feed.isEmpty && (filter != .illnesses || illnesses.isEmpty) {
                            EmptyHint(filter: filter)
                        }
                        ForEach(feed, id: \.date) { day in
                            Text(formatDayHeader(day.date, today: today, timeZone: timeZone))
                                .appTextStyle(theme.typography.titleSmall)
                                .foregroundStyle(theme.colors.onSurfaceVariant)
                                .padding(.horizontal, theme.spacing.xs)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .accessibilityAddTraits(.isHeader)
                            ForEach(day.records, id: \.id) { record in
                                FeedCard(
                                    record: record,
                                    photoCount: photoCounts[record.id] ?? 0,
                                    onEdit: { onEdit(record) },
                                    onDelete: { onDelete(record) },
                                    onTag: { onFilter(.tag($0)) }
                                )
                            }
                        }
                    }
                    .padding(EdgeInsets(
                        top: theme.spacing.m,
                        leading: theme.spacing.l,
                        bottom: fabClearance,
                        trailing: theme.spacing.l
                    ))
                }
                .onChange(of: filter) { _, _ in
                    proxy.scrollTo("notes-top", anchor: .top)
                }
            }
            Button(action: onAddNote) {
                HStack(spacing: theme.spacing.s) {
                    AppIcon("ic_add")
                    Text("Заметка").appTextStyle(theme.typography.labelLarge)
                }
                .foregroundStyle(theme.colors.onPrimaryContainer)
                .padding(.horizontal, theme.spacing.l)
                .frame(minHeight: BabyLogSizes.menuMinHeight)
                .background(theme.colors.primaryContainer, in: RoundedRectangle(cornerRadius: theme.shapes.medium))
                .cardShadow(shadow: theme.shadows.card)
            }
            .buttonStyle(.plain)
            .padding(theme.spacing.l)
        }
    }
}

/** «Плач: 11 мин в сутки за неделю», под ним — сколько раз и когда чаще; ведёт в «Аналитику». */
private struct CryingWeekCard: View {
    @Environment(\.babyLogTheme) private var theme
    let week: CryingWeek
    let onOpen: () -> Void

    var body: some View {
        AppCard(
            onClick: onOpen,
            onClickLabel: "открыть плач в аналитике",
            contentPadding: EdgeInsets(
                top: theme.spacing.m,
                leading: theme.spacing.l,
                bottom: theme.spacing.m,
                trailing: theme.spacing.s
            )
        ) {
            HStack(spacing: theme.spacing.m) {
                AppIcon("ic_analytics", tint: theme.colors.primary)
                VStack(alignment: .leading, spacing: 0) {
                    Text("Плач: \(week.perDay) в сутки за неделю")
                        .appTextStyle(theme.typography.bodyLarge)
                    Text([week.note, "подробнее в «Аналитике»"].compactMap { $0 }.joined(separator: " · "))
                        .appTextStyle(theme.typography.bodySmall)
                        .foregroundStyle(theme.colors.onSurfaceVariant)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                AppIcon("ic_chevron_right", tint: theme.colors.onSurfaceVariant)
            }
        }
        .frame(maxWidth: .infinity)
    }
}

/** «Все», «Плач», «Заметки», «Болезни» и метки — выбирается один; ряд листается вбок. */
private struct FilterRow: View {
    @Environment(\.babyLogTheme) private var theme
    let filter: NotesFilter
    let tags: [String]
    let hasCrying: Bool
    let hasNotes: Bool
    let hasIllnesses: Bool
    let onFilter: (NotesFilter) -> Void

    var body: some View {
        let selectedTag: String? = if case let .tag(tag) = filter { tag } else { nil }
        var seen: Set<String> = []
        let shownTags = (tags + (selectedTag.map { [$0] } ?? [])).filter { seen.insert($0).inserted }
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: theme.spacing.s) {
                ChoiceChip(selected: filter == .all, label: "Все") { onFilter(.all) }
                if hasCrying {
                    ChoiceChip(selected: filter == .cryingOnly, label: "Плач") { onFilter(.cryingOnly) }
                }
                if hasNotes {
                    ChoiceChip(selected: filter == .notesOnly, label: "Заметки") { onFilter(.notesOnly) }
                }
                if hasIllnesses {
                    ChoiceChip(selected: filter == .illnesses, label: "Болезни") { onFilter(.illnesses) }
                }
                ForEach(shownTags, id: \.self) { tag in
                    ChoiceChip(selected: tag == selectedTag, label: tag) { onFilter(.tag(tag)) }
                }
            }
        }
    }
}

/** Пустая лента: что сюда попадёт и откуда. */
private struct EmptyHint: View {
    @Environment(\.babyLogTheme) private var theme
    let filter: NotesFilter

    var body: some View {
        let text = switch filter {
        case .all: "Здесь будут плач и заметки.\nПлач отмечают плиткой на «Сегодня», заметку — кнопкой «Заметка»."
        case .cryingOnly: "Плача пока нет"
        case .notesOnly: "Заметок пока нет. Новая — кнопкой «Заметка»"
        case .illnesses: "Болезней пока нет"
        case .tag: "Записей с этой меткой нет"
        }
        Text(text)
            .appTextStyle(theme.typography.bodyMedium)
            .foregroundStyle(theme.colors.onSurfaceVariant)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .padding(.vertical, theme.spacing.xxl)
    }
}

/** Запись ленты: касание метки открывает её сводку, касание остальной карточки — правку. */
private struct FeedCard: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.timeZone) private var timeZone
    let record: any BabyEvent
    let photoCount: Int
    let onEdit: () -> Void
    let onDelete: () -> Void
    let onTag: (String) -> Void

    var body: some View {
        let lines = feedLines(record: record, photoCount: photoCount, timeZone: timeZone)
        AppCard(
            onClick: onEdit,
            onClickLabel: "изменить запись",
            contentPadding: EdgeInsets(
                top: theme.spacing.s,
                leading: theme.spacing.l,
                bottom: theme.spacing.s,
                trailing: theme.spacing.xs
            )
        ) {
            HStack(alignment: .top, spacing: theme.spacing.m) {
                AppIcon(eventIcon(record), tint: theme.colors.primary)
                    .padding(.top, theme.spacing.m)
                VStack(alignment: .leading, spacing: 0) {
                    Text(lines.title).appTextStyle(theme.typography.bodyLarge)
                    Text(lines.subtitle)
                        .appTextStyle(theme.typography.bodySmall)
                        .foregroundStyle(theme.colors.onSurfaceVariant)
                    if let comment = lines.comment {
                        Text(comment)
                            .appTextStyle(theme.typography.bodySmall)
                            .italic()
                            .foregroundStyle(theme.colors.onSurfaceVariant)
                    }
                    if !record.eventTags.isEmpty {
                        FlowRow(spacing: theme.spacing.xs) {
                            ForEach(record.eventTags, id: \.self) { tag in
                                TagLabel(tag: tag, onClick: { onTag(tag) })
                            }
                        }
                    }
                }
                .padding(.top, theme.spacing.s)
                .padding(.bottom, record.eventTags.isEmpty ? theme.spacing.s : 0)
                .frame(maxWidth: .infinity, alignment: .leading)
                Button(action: onDelete) {
                    AppIcon("ic_delete", contentDescription: "Удалить", tint: theme.colors.onSurfaceVariant)
                        .frame(width: BabyLogSizes.touchTarget, height: BabyLogSizes.touchTarget)
                }
                .buttonStyle(.plain)
            }
        }
        .frame(maxWidth: .infinity)
    }
}

private struct FeedLines {
    var title: String
    var subtitle: String
    var comment: String?
}

/** Заголовок, вторая строка и комментарий записи ленты. */
private func feedLines(record: any BabyEvent, photoCount: Int, timeZone: TimeZone) -> FeedLines {
    let photos = photoCount > 0 ? " · \(photoCount) фото" : ""
    if let crying = record as? Crying {
        let time = crying.end.map {
            "\(formatClock(crying.start, timeZone: timeZone))–\(formatClock($0, timeZone: timeZone))"
        } ?? "с \(formatClock(crying.start, timeZone: timeZone))"
        let helped = crying.helped.isEmpty ? "" : " · помогло: \(crying.helped.joined(separator: ", "))"
        return FeedLines(
            title: crying.end.map { "Плач · \(formatDuration($0 - crying.start))" } ?? "Плач · идёт",
            subtitle: time + helped,
            comment: crying.note.isEmpty ? nil : crying.note
        )
    }
    if let note = record as? Note {
        return FeedLines(
            title: note.note.isEmpty ? "Заметка" : note.note,
            subtitle: formatClock(note.start, timeZone: timeZone) + photos,
            comment: nil
        )
    }
    return FeedLines(
        title: eventTitle(record, sex: nil),
        subtitle: eventTimeLine(record, timeZone: timeZone) + photos,
        comment: record.note.isEmpty ? nil : record.note
    )
}

/** Метка на мягкой подложке; кнопка сохраняет поле нажатия 48 dp вокруг небольшой подписи. */
struct TagLabel: View {
    @Environment(\.babyLogTheme) private var theme
    let tag: String
    var count: Int?
    var onClick: (() -> Void)?

    var body: some View {
        if let onClick {
            Button(action: onClick) {
                label
            }
            .buttonStyle(.plain)
            .frame(minWidth: BabyLogSizes.touchTarget, minHeight: BabyLogSizes.touchTarget)
            .accessibilityHint("показать записи с меткой")
        } else {
            label
        }
    }

    private var label: some View {
        Text(count.map { "\(tag) \($0)" } ?? tag)
            .appTextStyle(theme.typography.labelMedium)
            .foregroundStyle(theme.colors.onSecondaryContainer)
            .padding(.horizontal, theme.spacing.s)
            .padding(.vertical, theme.spacing.xs)
            .background(theme.colors.secondaryContainer, in: Capsule())
    }
}
