import SwiftUI

/** Таймер сна: касание начинает сон, во время сна — заканчивает; в покое — окно бодрствования. */
struct SleepCard: View {
    let child: Child
    let activeSleep: Sleep?
    let lastSleepEnd: Int64?
    let now: Int64
    let onStart: () -> Void
    let onEnd: () -> Void

    var body: some View {
        TimerTile(
            icon: "ic_sleep",
            label: "Сон",
            runningSince: activeSleep?.start,
            now: now,
            startLabel: "начать сон",
            stopLabel: bySex(child.sex, masculine: "проснулся", feminine: "проснулась"),
            idleCaption: awakeCaption(lastSleepEnd: lastSleepEnd, now: now),
            onStart: onStart,
            onStop: onEnd
        )
    }
}

/** Таймер кормления: касание начинает и заканчивает его, ⇄ в углу меняет грудь. */
struct FeedingCard: View {
    @Environment(\.timeZone) private var timeZone

    let activeFeeding: Feeding?
    let lastFeeding: LastFeeding?
    let now: Int64
    let onStart: () -> Void
    let onEnd: () -> Void
    let onSwitchBreast: () -> Void

    var body: some View {
        TimerTile(
            icon: "ic_feeding",
            label: "Кормление",
            runningSince: activeFeeding?.start,
            now: now,
            startLabel: "начать кормление",
            stopLabel: "закончить кормление",
            runningLabel: activeFeeding.map { "\(breastLabel($0.breast)) грудь" },
            idleCaption: afterFeedingCaption(lastFeeding: lastFeeding, now: now, timeZone: timeZone),
            corner: AnyView(TimerCornerButton(icon: "ic_swap", description: "Сменить грудь", onClick: onSwitchBreast)),
            onStart: onStart,
            onStop: onEnd
        )
    }
}

/** Плач — широкая плитка под сном и кормлением, рядом с «Заметкой». */
struct CryingCard: View {
    let activeCrying: Crying?
    let lastCrying: Crying?
    let now: Int64
    let onStart: () -> Void
    let onEnd: () -> Void

    var body: some View {
        WideTimerTile(
            icon: "ic_crying",
            label: "Плач",
            runningSince: activeCrying?.start,
            now: now,
            startLabel: "начать плач",
            stopLabel: "закончить плач",
            idleCaption: cryingCaption(lastCrying: lastCrying, now: now),
            onStart: onStart,
            onStop: onEnd
        )
    }
}

/** Плач занимает ширину трёх быстрых плиток, «Заметка» стоит точно над «Ростом». */
struct CryingNoteRow: View {
    @Environment(\.babyLogTheme) private var theme

    let activeCrying: Crying?
    let lastCrying: Crying?
    let now: Int64
    let onStartCrying: () -> Void
    let onEndCrying: () -> Void
    let onAddNote: () -> Void

    var body: some View {
        let gap = theme.spacing.s
        GeometryReader { geometry in
            let noteWidth = (geometry.size.width - gap * CGFloat(quickActionCount - 1)) / CGFloat(quickActionCount)
            HStack(spacing: gap) {
                CryingCard(
                    activeCrying: activeCrying,
                    lastCrying: lastCrying,
                    now: now,
                    onStart: onStartCrying,
                    onEnd: onEndCrying
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                ActionTile(icon: "ic_note", label: "Заметка", onClick: onAddNote)
                    .frame(width: noteWidth, height: geometry.size.height)
            }
        }
        .frame(height: BabyLogSizes.cryingNoteRowHeight)
    }
}

/** «2 ч назад · 24 мин» — последний законченный плач; не было или давно — nil. */
func cryingCaption(lastCrying: Crying?, now: Int64) -> String? {
    guard let lastCrying, let end = lastCrying.end, let ago = sinceLast(end: end, now: now) else { return nil }
    return "\(formatAgo(ago)) · \(formatDuration(end - lastCrying.start))"
}

/** «не спит 1 ч 20 мин» — с конца последнего сна; сна не было или он давно — nil. */
func awakeCaption(lastSleepEnd: Int64?, now: Int64) -> String? {
    sinceLast(end: lastSleepEnd, now: now).map { "не спит \(formatDuration($0))" }
}

/**
 Две строки: чем кормили и во сколько начали, затем сколько прошло с конца.
 У бутылочки начало и конец — одно время. Не было или давно — nil.
 */
func afterFeedingCaption(lastFeeding: LastFeeding?, now: Int64, timeZone: TimeZone = .current) -> String? {
    guard let lastFeeding, let ago = sinceLast(end: lastFeeding.end, now: now) else { return nil }
    let what = lastFeeding.breast.map(breastLabel) ?? "бутылочка"
    let since = lastFeeding.breast == nil ? formatAgo(ago) : "закончилось \(formatAgo(ago))"
    return "\(what) в \(formatClock(lastFeeding.start, timeZone: timeZone))\n\(since)"
}

/** Сколько прошло с `end`; сутки и больше — nil: так долго не спят и не едят, просто не записывали. */
private func sinceLast(end: Int64?, now: Int64) -> Int64? {
    guard let end else { return nil }
    let elapsed = max(now - end, 0)
    return elapsed < sinceLastLimitMillis ? elapsed : nil
}

private let sinceLastLimitMillis: Int64 = 24 * 60 * 60 * 1000

/** Плитки быстрого добавления записей; подгузник выделяется после трёх часов бодрствования. */
struct QuickActions: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.timeZone) private var timeZone

    let lastDiaper: Int64?
    let lastWeight: Int64?
    let lastHeight: Int64?
    let sleeping: Bool
    let now: Int64
    let onAdd: (NewEventKind) -> Void
    let onHealth: () -> Void
    let illnessDay: Int?

    init(
        lastDiaper: Int64?,
        lastWeight: Int64?,
        lastHeight: Int64?,
        sleeping: Bool,
        now: Int64,
        illnessDay: Int? = nil,
        onAdd: @escaping (NewEventKind) -> Void,
        onHealth: @escaping () -> Void
    ) {
        self.lastDiaper = lastDiaper
        self.lastWeight = lastWeight
        self.lastHeight = lastHeight
        self.sleeping = sleeping
        self.now = now
        self.illnessDay = illnessDay
        self.onAdd = onAdd
        self.onHealth = onHealth
    }

    var body: some View {
        HStack(spacing: theme.spacing.s) {
            ActionTile(
                icon: "ic_diaper",
                label: "Подгузник",
                caption: diaperCaption(lastDiaper: lastDiaper, now: now),
                highlight: diaperOverdue(lastDiaper: lastDiaper, sleeping: sleeping, now: now)
            ) { onAdd(.diaper) }
            ActionTile(
                icon: "ic_first_aid",
                label: "Здоровье",
                caption: illnessDay.map { "\($0)-й день" },
                onClick: onHealth
            )
            ActionTile(
                icon: "ic_weight",
                label: "Вес",
                caption: measuredCaption(lastMeasured: lastWeight, now: now, timeZone: timeZone)
            ) { onAdd(.weight) }
            ActionTile(
                icon: "ic_height",
                label: "Рост",
                caption: measuredCaption(lastMeasured: lastHeight, now: now, timeZone: timeZone)
            ) { onAdd(.height) }
        }
    }
}

private let quickActionCount = 4

/** Когда взвешивали или мерили рост: «сегодня», «вчера», «15 окт.». */
func measuredCaption(lastMeasured: Int64?, now: Int64, timeZone: TimeZone = .current) -> String? {
    lastMeasured.map {
        formatShortDay(
            toLocalDate($0, timeZone: timeZone),
            today: toLocalDate(now, timeZone: timeZone),
            timeZone: timeZone
        )
    }
}

/** «2 ч 10 мин назад» — с последней смены подгузника; не меняли или давно — nil. */
func diaperCaption(lastDiaper: Int64?, now: Int64) -> String? {
    sinceLast(end: lastDiaper, now: now).map(formatAgo)
}

/** Пора менять подгузник после трёх часов, но не будить спящего ребёнка ради этого. */
func diaperOverdue(lastDiaper: Int64?, sleeping: Bool, now: Int64) -> Bool {
    !sleeping && (sinceLast(end: lastDiaper, now: now) ?? 0) >= diaperChangeMillis
}

private let diaperChangeMillis: Int64 = 3 * 60 * 60 * 1000

/** Строка истории: нажатие на карточку — правка, корзина — удаление. */
struct EventRow: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.timeZone) private var timeZone

    let event: any BabyEvent
    let sex: Sex?
    let photoCount: Int
    let onEdit: () -> Void
    let onDelete: () -> Void

    var body: some View {
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
            ItemRow(
                icon: eventIcon(event),
                title: eventTitle(event, sex: sex),
                subtitle: eventTimeLine(event, timeZone: timeZone) + (photoCount > 0 ? " · \(photoCount) фото" : ""),
                note: event.note
            ) {
                Button(action: onDelete) {
                    AppIcon("ic_delete", contentDescription: "Удалить", tint: theme.colors.onSurfaceVariant)
                        .frame(width: BabyLogSizes.touchTarget, height: BabyLogSizes.touchTarget)
                }
                .buttonStyle(.plain)
            }
        }
    }
}
