import SwiftUI

/** Разделы главного экрана (ADR-0035); переключает их нижняя панель `MainNavigationBar`. */
enum MainSection: String, CaseIterable, Hashable, Sendable {
    case today
    case diary
    case development
    case analytics

    var title: String {
        switch self {
        case .today: "Сегодня"
        case .diary: "Дневник"
        case .development: "Развитие"
        case .analytics: "Аналитика"
        }
    }

    var icon: String {
        switch self {
        case .today: "ic_today"
        case .diary: "ic_diary"
        case .development: "ic_development"
        case .analytics: "ic_analytics"
        }
    }
}

/** Вид «Дневника» (ADR-0035): записи по дням или плач и заметки (ADR-0031). */
enum DiaryView: String, CaseIterable, Hashable, Sendable {
    case days
    case notes

    var title: String {
        switch self {
        case .days: "По дням"
        case .notes: "Заметки"
        }
    }
}

/**
 Нижняя панель разделов (ADR-0035): до неё дотягивается большой палец, когда ребёнок на руках.
 Подписи видны у всех пунктов. Точка на «Развитии» — советы этой недели ещё не открывали.
 */
struct MainNavigationBar: View {
    @Environment(\.babyLogTheme) private var theme

    let selected: MainSection
    let onSelect: (MainSection) -> Void
    var developmentBadge = false

    var body: some View {
        HStack(spacing: 0) {
            ForEach(MainSection.allCases, id: \.self) { section in
                Button {
                    onSelect(section)
                } label: {
                    VStack(spacing: theme.spacing.xs) {
                        AppIcon(
                            section.icon,
                            contentDescription: section == .development && developmentBadge ? "есть новые советы" : nil,
                            tint: selected == section ? theme.colors.onSecondaryContainer : theme.colors.onSurfaceVariant
                        )
                        .frame(width: BabyLogSizes.touchTarget, height: BabyLogSizes.chipHeight)
                        .background(
                            selected == section ? theme.colors.secondaryContainer : .clear,
                            in: Capsule()
                        )
                        .overlay(alignment: .topTrailing) {
                            if section == .development && developmentBadge {
                                Circle()
                                    .fill(theme.colors.primary)
                                    .frame(width: BabyLogSizes.badge, height: BabyLogSizes.badge)
                                    .offset(x: theme.spacing.xs)
                            }
                        }
                        Text(section.title)
                            .appTextStyle(theme.typography.labelMedium)
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                    }
                    .foregroundStyle(selected == section ? theme.colors.onSurface : theme.colors.onSurfaceVariant)
                    .frame(maxWidth: .infinity, minHeight: BabyLogSizes.rowMinHeight)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected == section ? [.isSelected] : [])
            }
        }
        .frame(height: BabyLogSizes.navigationHeight)
        .frame(maxWidth: .infinity)
        .background(theme.colors.surfaceContainer)
    }
}

/**
 «Дневник» (ADR-0035): переключатель «По дням | Заметки» и под ним выбранный вид.
 Это кнопки, а не вкладки: вкладки рядом с нижней панелью путают уровни навигации.
 */
struct DiarySection<Content: View>: View {
    @Environment(\.babyLogTheme) private var theme

    let view: DiaryView
    let onView: (DiaryView) -> Void
    let content: (DiaryView) -> Content

    init(
        view: DiaryView,
        onView: @escaping (DiaryView) -> Void,
        @ViewBuilder content: @escaping (DiaryView) -> Content
    ) {
        self.view = view
        self.onView = onView
        self.content = content
    }

    var body: some View {
        VStack(spacing: 0) {
            Picker(
                "Вид дневника",
                selection: Binding(get: { view }, set: { onView($0) })
            ) {
                ForEach(DiaryView.allCases, id: \.self) { item in
                    Text(item.title).tag(item)
                }
            }
            .pickerStyle(.segmented)
            .padding(.leading, theme.spacing.l)
            .padding(.top, theme.spacing.m)
            .padding(.trailing, theme.spacing.l)
            content(view)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }
}
