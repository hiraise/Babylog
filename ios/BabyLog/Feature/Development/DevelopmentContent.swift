import SwiftUI

private let tipsOnOverview = 3

/** Состояние обзора, экрана навыков и листов раздела; нажатия проверяются без SwiftUI. */
@MainActor @Observable final class DevelopmentContentModel {
    private(set) var showSkills: Bool
    private(set) var openTipId: String?
    private(set) var showAllTips = false
    private let initialSkills: Bool
    private var childId: Int64?

    init(childId: Int64, initialSkills: Bool = false) {
        self.childId = childId
        self.initialSkills = initialSkills
        showSkills = initialSkills
    }

    func openSkills() { showSkills = true }
    func backToOverview() { showSkills = false }
    func openTip(_ id: String) { openTipId = id }
    func dismissTip() { openTipId = nil }
    func openAllTips() { showAllTips = true }
    func dismissAllTips() { showAllTips = false }

    func openFromAllTips(_ id: String) {
        showAllTips = false
        openTipId = id
    }

    func updateChild(_ id: Int64) {
        guard childId != id else { return }
        childId = id
        showSkills = initialSkills
    }
}

/**
 Раздел «Развитие» (ADR-0035, ADR-0037): неделя жизни, советы недели, «когда показать педиатру»
 и окна навыков ВОЗ. «Подробнее» у навыков открывает экран навыков внутри раздела, «Назад»
 возвращает к обзору. Совет целиком и все советы недели — нижние листы. Без даты рождения раздел
 предлагает её указать (`onSetBirthDate`).
 */
struct DevelopmentContent: View {
    @Environment(\.timeZone) private var timeZone
    @State private var model: DevelopmentContentModel

    let child: Child
    let data: DevelopmentData?
    let today: Date
    let onSetBirthDate: () -> Void

    init(
        child: Child,
        data: DevelopmentData?,
        today: Date,
        onSetBirthDate: @escaping () -> Void,
        initialSkills: Bool = false
    ) {
        self.child = child
        self.data = data
        self.today = today
        self.onSetBirthDate = onSetBirthDate
        _model = State(initialValue: DevelopmentContentModel(childId: child.id, initialSkills: initialSkills))
    }

    var body: some View {
        let birth = child.birthDate.map { toLocalDate($0, timeZone: timeZone) }
        let week = currentDevelopmentWeek(data: data, child: child, today: today, timeZone: timeZone)
        Group {
            if data == nil {
                Color.clear
            } else if birth == nil {
                DiaryList { BirthDateCard(onSetBirthDate: onSetBirthDate) }
            } else if week == nil {
                DiaryList { NoteCard(text: "Советы появятся, когда малыш родится.") }
            } else if let week, let data {
                ZStack {
                    overview(week: week)
                        .opacity(model.showSkills ? 0 : 1)
                        .allowsHitTesting(!model.showSkills)
                        .accessibilityHidden(model.showSkills)
                    if model.showSkills {
                        skillsDetail(week: week, source: data.milestones.source)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .guideTarget(model.showSkills ? .skills : .development)
        .onChange(of: child.id) { _, id in model.updateChild(id) }

        if let tip = data?.tips.first(where: { $0.id == model.openTipId }) {
            TipSheet(
                isPresented: Binding(get: { true }, set: { if !$0 { model.dismissTip() } }),
                tip: tip,
                onDismiss: model.dismissTip
            )
        }
        if model.showAllTips, let week {
            AllTipsSheet(
                isPresented: Binding(get: { true }, set: { if !$0 { model.dismissAllTips() } }),
                week: week,
                onOpen: { model.openFromAllTips($0.id) },
                onDismiss: model.dismissAllTips
            )
        }
    }

    @ViewBuilder private func overview(week: DevelopmentWeek) -> some View {
        let birth = toLocalDate(child.birthDate ?? 0, timeZone: timeZone)
        DiaryList {
            WeekCard(week: week, age: formatAge(birth: birth, today: today, sex: child.sex, timeZone: timeZone))
            if week.allTips.isEmpty {
                NoteCard(text: "Советы — на первый год жизни. Для детей постарше их пока нет.")
            }
            if !week.tips.isEmpty {
                AppCard {
                    CardColumn {
                        CardTitle(icon: "ic_lightbulb", title: "Советы недели") {
                            if week.allTips.count > tipsOnOverview {
                                Button("все \(week.allTips.count)", action: model.openAllTips)
                                    .buttonStyle(.plain)
                                    .foregroundStyle(themeColors.primary)
                                    .appTextStyle(themeTypography.labelLarge)
                            }
                        }
                        ForEach(week.tips.prefix(tipsOnOverview), id: \.id) { tip in
                            TipRow(tip: tip) { model.openTip(tip.id) }
                        }
                    }
                }
            }
            if !week.doctorTips.isEmpty {
                AppCard(containerColor: themeColors.tertiaryContainer, contentColor: themeColors.onTertiaryContainer) {
                    CardColumn {
                        CardTitle(icon: "ic_stethoscope", title: "Когда показать педиатру")
                        ForEach(week.doctorTips, id: \.id) { tip in
                            DoctorTipRow(tip: tip) { model.openTip(tip.id) }
                        }
                    }
                }
            }
            AppCard {
                CardColumn {
                    CardTitle(icon: "ic_development", title: "Навыки по возрасту") {
                        Button("подробнее", action: model.openSkills)
                            .buttonStyle(.plain)
                            .foregroundStyle(themeColors.primary)
                            .appTextStyle(themeTypography.labelLarge)
                            .guideTarget(.skillsButton)
                    }
                    MilestoneChart(milestones: week.milestones, ageDays: week.ageDays)
                    Text(windowsSummary(week))
                        .appTextStyle(themeTypography.bodySmall)
                        .foregroundStyle(themeColors.onSurfaceVariant)
                }
            }
            Disclaimer()
        }
    }

    @ViewBuilder private func skillsDetail(week: DevelopmentWeek, source: TipSource) -> some View {
        DiaryList {
            HStack(spacing: 0) {
                Button(action: model.backToOverview) {
                    AppIcon("ic_chevron_left", contentDescription: "Назад к разделу")
                        .frame(width: BabyLogSizes.touchTarget, height: BabyLogSizes.touchTarget)
                }
                .buttonStyle(.plain)
                Text("Навыки по возрасту")
                    .appTextStyle(themeTypography.headlineSmall)
                    .accessibilityAddTraits(.isHeader)
            }
            AppCard {
                VStack(alignment: .leading, spacing: themeSpacing.m) {
                    Text("Полоса — возраст, в котором навык осваивают 98 детей из 100 (1-й и 99-й перцентили ВОЗ). Нет навыка и после правого края полосы — обсудите с педиатром.")
                        .appTextStyle(themeTypography.bodyMedium)
                    MilestoneChart(milestones: week.milestones, ageDays: week.ageDays, rowHeight: 40)
                    SourceLink(source: source)
                }
            }
            AppCard {
                VStack(alignment: .leading, spacing: themeSpacing.s) {
                    ForEach(week.milestones.indices, id: \.self) { index in
                        let item = week.milestones[index]
                        VStack(alignment: .leading, spacing: 0) {
                            Text(item.window.title).appTextStyle(themeTypography.bodyLarge)
                            Text("\(stateText(item.state)) · с \(formatMonthValue(item.window.months.lowerBound)) до \(formatMonthValue(item.window.months.upperBound)) мес")
                                .appTextStyle(themeTypography.bodySmall)
                                .foregroundStyle(themeColors.onSurfaceVariant)
                        }
                    }
                }
            }
            Disclaimer()
        }
    }

    private var themeColors: BabyLogColorScheme { theme.colors }
    private var themeTypography: BabyLogTypography { theme.typography }
    private var themeSpacing: Spacing { theme.spacing }
    @Environment(\.babyLogTheme) private var theme
}

/** Неделя выбранного ребёнка; nil — нет даты рождения или она ещё не наступила. */
func currentDevelopmentWeek(
    data: DevelopmentData?,
    child: Child,
    today: Date,
    timeZone: TimeZone
) -> DevelopmentWeek? {
    guard let data, let millis = child.birthDate else { return nil }
    return developmentWeek(data: data, birth: toLocalDate(millis, timeZone: timeZone), today: today, timeZone: timeZone)
}

private struct WeekCard: View {
    @Environment(\.babyLogTheme) private var theme

    let week: DevelopmentWeek
    let age: String

    var body: some View {
        AppCard(containerColor: theme.colors.primaryContainer, contentColor: theme.colors.onPrimaryContainer) {
            VStack(alignment: .leading, spacing: theme.spacing.xs) {
                Text(weekTitle(week.week))
                    .appTextStyle(theme.typography.headlineSmall)
                    .accessibilityAddTraits(.isHeader)
                Text("\(age) · новые советы — каждую неделю")
                    .appTextStyle(theme.typography.bodyMedium)
            }
        }
    }
}

/** Содержимое карточки с шагом `spacing.s`: `AppCard` сама шага между строками не даёт. */
private struct CardColumn<Content: View>: View {
    @Environment(\.babyLogTheme) private var theme
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.s) { content }
    }
}

private struct CardTitle<Action: View>: View {
    @Environment(\.babyLogTheme) private var theme

    let icon: String
    let title: String
    @ViewBuilder let action: Action

    var body: some View {
        HStack(spacing: theme.spacing.s) {
            AppIcon(icon, tint: theme.colors.primary)
            Text(title)
                .appTextStyle(theme.typography.titleMedium)
                .accessibilityAddTraits(.isHeader)
                .frame(maxWidth: .infinity, alignment: .leading)
            action
        }
    }
}

private extension CardTitle where Action == EmptyView {
    init(icon: String, title: String) {
        self.init(icon: icon, title: title, action: { EmptyView() })
    }
}

private struct BirthDateCard: View {
    @Environment(\.babyLogTheme) private var theme
    let onSetBirthDate: () -> Void

    var body: some View {
        AppCard {
            VStack(alignment: .leading, spacing: theme.spacing.m) {
                Text("Укажите дату рождения").appTextStyle(theme.typography.titleMedium)
                Text("По ней раздел подберёт советы на каждую неделю жизни и покажет окна навыков ВОЗ.")
                    .appTextStyle(theme.typography.bodyMedium)
                Button("Указать дату рождения", action: onSetBirthDate)
                    .buttonStyle(.borderedProminent)
                    .buttonBorderShape(.capsule)
                    .tint(theme.colors.primary)
                    .appTextStyle(theme.typography.labelLarge)
            }
        }
    }
}

private struct NoteCard: View {
    @Environment(\.babyLogTheme) private var theme
    let text: String

    var body: some View {
        AppCard { Text(text).appTextStyle(theme.typography.bodyMedium) }
    }
}

private struct Disclaimer: View {
    @Environment(\.babyLogTheme) private var theme

    var body: some View {
        Text("Справочная информация, не заменяет консультацию врача. Источники — у каждого совета.")
            .appTextStyle(theme.typography.bodySmall)
            .foregroundStyle(theme.colors.onSurfaceVariant)
    }
}

private func stateText(_ state: MilestoneState) -> String {
    switch state {
    case .ahead: "впереди"
    case .inWindow: "сейчас в окне"
    case .past: "окно прошло"
    }
}

/** Подпись под графиком на обзоре: какие окна сейчас открыты, а если ни одного — ближайшее. */
func windowsSummary(_ week: DevelopmentWeek) -> String {
    let inWindow = week.milestones.filter { $0.state == .inWindow }.map { $0.window.title.lowercased() }
    let next = week.milestones.first { $0.state == .ahead }?.window
    if !inWindow.isEmpty { return "Сейчас в окне: \(inWindow.joined(separator: ", "))." }
    if let next { return "Ближайшее окно — «\(next.title.lowercased())», с \(formatMonthValue(next.months.lowerBound)) мес." }
    return "Окна всех шести навыков уже прошли."
}

