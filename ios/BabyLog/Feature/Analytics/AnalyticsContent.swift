import SwiftUI

/** Состояние вкладки: общий период, открытый раздел и выбранный столбик «метрика:номер». */
@MainActor @Observable final class AnalyticsContentModel {
    private(set) var period: AnalyticsPeriod = .week
    private(set) var section: AnalyticsSection?
    var selection: String?
    private var childId: Int64?

    init(initialSection: AnalyticsSection? = nil) {
        section = initialSection
    }

    func selectPeriod(_ value: AnalyticsPeriod) {
        period = value
        selection = nil
    }

    func open(_ value: AnalyticsSection) {
        section = value
        selection = nil
    }

    func back() {
        section = nil
        selection = nil
    }

    func selectBar(metric: Metric, index: Int?) {
        let prefix = "\(metric):"
        selection = index.map { prefix + "\($0)" }
    }

    func selectedIndex(metric: Metric) -> Int? {
        let prefix = "\(metric):"
        guard let selection, selection.hasPrefix(prefix) else { return nil }
        return Int(selection.dropFirst(prefix.count))
    }

    /** При смене ребёнка выбор столбика сбрасывается, раздел остаётся открытым. */
    func updateChild(_ id: Int64) {
        if childId != nil && childId != id { selection = nil }
        childId = id
    }
}

/**
 Вкладка «Аналитика» (ADR-0028, ADR-0029): обзор плиток и подробный экран раздела. Всё считается
 из записей выбранного ребёнка при показе. «В историю» и строка ритма открывают день в «Дневнике»,
 метка в разделе «Метки» открывает её на «Заметках».
 */
struct AnalyticsContent: View {
    @Environment(\.timeZone) private var timeZone
    @State private var model: AnalyticsContentModel

    let events: [any BabyEvent]
    let child: Child
    let now: Int64
    let onOpenDay: (Date) -> Void
    let onOpenTag: (String) -> Void

    init(
        events: [any BabyEvent],
        child: Child,
        now: Int64,
        onOpenDay: @escaping (Date) -> Void,
        initialSection: AnalyticsSection? = nil,
        onOpenTag: @escaping (String) -> Void = { _ in }
    ) {
        self.events = events
        self.child = child
        self.now = now
        self.onOpenDay = onOpenDay
        self.onOpenTag = onOpenTag
        _model = State(initialValue: AnalyticsContentModel(initialSection: initialSection))
    }

    var body: some View {
        let analytics = analyticsModel(events: events, period: model.period, now: now, timeZone: timeZone, sex: child.sex)
        ZStack {
            AnalyticsOverview(
                model: analytics,
                period: model.period,
                onPeriod: model.selectPeriod,
                onOpen: model.open
            )
            .opacity(model.section == nil ? 1 : 0)
            .allowsHitTesting(model.section == nil)
            .accessibilityHidden(model.section != nil)
            if let section = model.section {
                AnalyticsDetail(
                    section: section,
                    model: analytics,
                    period: model.period,
                    onPeriod: model.selectPeriod,
                    selection: model.selection,
                    onSelection: { model.selection = $0 },
                    onOpenDay: onOpenDay,
                    onBack: model.back,
                    onOpenTag: onOpenTag
                )
            }
        }
        .onChange(of: child.id) { _, id in model.updateChild(id) }
    }
}

/** Отступы списка вкладки: сверху не меньше размытия тени карточек, как на других вкладках. */
func analyticsListPadding(_ theme: ThemeValues) -> EdgeInsets {
    EdgeInsets(top: theme.spacing.m, leading: theme.spacing.l, bottom: theme.spacing.xl, trailing: theme.spacing.l)
}

/** Чипы периода, пояснение к штриховке и — если в периоде болел — когда (ADR-0040). */
struct PeriodPicker: View {
    @Environment(\.babyLogTheme) private var theme

    let period: AnalyticsPeriod
    let onSelect: (AnalyticsPeriod) -> Void
    var illnessNote: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: theme.spacing.s) {
                ForEach(AnalyticsPeriod.allCases, id: \.self) { option in
                    ChoiceChip(selected: option == period, label: option.title) {
                        onSelect(option)
                    }
                }
            }
            Text(period == .all
                ? "Заштрихована неделя, которая ещё идёт: в средние входят только прошедшие сутки"
                : "Заштрихован день, который ещё идёт: в средние он не входит")
                .appTextStyle(theme.typography.bodySmall)
                .foregroundStyle(theme.colors.onSurfaceVariant)
            if let illnessNote {
                IllnessLegend(note: illnessNote)
            }
        }
    }
}

/** Карточка-подсказка вместо графиков: текст посередине. */
struct HintCard: View {
    @Environment(\.babyLogTheme) private var theme
    let text: String

    var body: some View {
        AppCard {
            Text(text)
                .appTextStyle(theme.typography.bodyMedium)
                .foregroundStyle(theme.colors.onSurfaceVariant)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
        }
        .frame(maxWidth: .infinity)
    }
}
