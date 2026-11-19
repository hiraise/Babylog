import SwiftUI

/**
 Места экрана, на которые показывает гайд справки (ADR-0043). Цель, куда нажать, ставят модификатором
 `guideTarget` на сам элемент; экран или лист, о котором гайду надо знать, что он открыт, — тем же
 модификатором на его элемент. Пустой Spacer для метки не добавляй: в колонке со spacing он даст
 лишний промежуток.
 */
enum GuideTarget: String, Hashable, Sendable {
    case sleepTile = "SLEEP_TILE"
    case feedingTile = "FEEDING_TILE"
    case switchBreast = "SWITCH_BREAST"
    case cryingTile = "CRYING_TILE"
    case noteTile = "NOTE_TILE"
    case diaperTile = "DIAPER_TILE"
    case healthTile = "HEALTH_TILE"
    case weightTile = "WEIGHT_TILE"
    case heightTile = "HEIGHT_TILE"
    case illnessCard = "ILLNESS_CARD"
    case childSelector = "CHILD_SELECTOR"
    case menuButton = "MENU_BUTTON"
    case navDiary = "NAV_DIARY"
    case navDevelopment = "NAV_DEVELOPMENT"
    case navAnalytics = "NAV_ANALYTICS"
    case history = "HISTORY"
    case pastDay = "PAST_DAY"
    case dayPicker = "DAY_PICKER"
    case daySelector = "DAY_SELECTOR"
    case eventEditor = "EVENT_EDITOR"
    case notesButton = "NOTES_BUTTON"
    case notesFeed = "NOTES_FEED"
    case tagSummary = "TAG_SUMMARY"
    case analyticsOverview = "ANALYTICS_OVERVIEW"
    case analyticsSleepTile = "ANALYTICS_SLEEP_TILE"
    case analyticsDetail = "ANALYTICS_DETAIL"
    case development = "DEVELOPMENT"
    case skillsButton = "SKILLS_BUTTON"
    case skills = "SKILLS"
    case illnessScreen = "ILLNESS_SCREEN"
    case recoverButton = "RECOVER_BUTTON"
    case childrenSheet = "CHILDREN_SHEET"
    case addChild = "ADD_CHILD"
    case themeSheet = "THEME_SHEET"
}

/** Гайд, который идёт на экране прямо сейчас. Вне примера дневника `guide` — nil. */
@MainActor protocol GuideTracker: AnyObject {
    /** Куда нажать на текущем шаге; nil — шаг без цели или гайд закончен. */
    var target: GuideTarget? { get }

    /** Текст текущего шага для открытого нижнего листа: панель гайда остаётся под листом. */
    var sheetHint: String? { get }

    func shown(_ target: GuideTarget)
    func hidden(_ target: GuideTarget)
}

private struct GuideKey: EnvironmentKey {
    static var defaultValue: (any GuideTracker)? { nil }
}

private struct SheetTargetKey: EnvironmentKey {
    static let defaultValue: GuideTarget? = nil
}

extension EnvironmentValues {
    var guide: (any GuideTracker)? {
        get { self[GuideKey.self] }
        set { self[GuideKey.self] = newValue }
    }

    var sheetTarget: GuideTarget? {
        get { self[SheetTargetKey.self] }
        set { self[SheetTargetKey.self] = newValue }
    }
}

/** Листы внутри `content` — цель `target` для гайда справки. */
struct GuideSheet<Content: View>: View {
    let target: GuideTarget
    @ViewBuilder let content: Content

    var body: some View {
        content.environment(\.sheetTarget, target)
    }
}

enum GuideOutlineShape {
    case rounded
    case small
    case circle
}

/**
 Цель гайда: сообщает гайду, что элемент на экране, и обводит его, когда это цель текущего шага.
 Обводка — снаружи элемента и пульсирует. Ставь до клипа элемента, иначе он срежет обводку.
 */
private struct GuideTargetModifier: ViewModifier {
    @Environment(\.guide) private var guide
    @Environment(\.babyLogTheme) private var theme
    let target: GuideTarget
    let shape: GuideOutlineShape
    @State private var pulsing = false

    func body(content: Content) -> some View {
        content
            .overlay {
                if guide?.target == target {
                    outline
                        .padding(-BabyLogSizes.guideOutlineGap)
                        .opacity(pulsing ? 0.35 : 1)
                        .allowsHitTesting(false)
                        .onAppear {
                            pulsing = false
                            withAnimation(.easeInOut(duration: 0.7).repeatForever(autoreverses: true)) {
                                pulsing = true
                            }
                        }
                }
            }
            .accessibilityIdentifier("guide-target-\(target.rawValue)")
            .onAppear { guide?.shown(target) }
            .onDisappear { guide?.hidden(target) }
            .onChange(of: target) { old, new in
                guide?.hidden(old)
                guide?.shown(new)
            }
    }

    @ViewBuilder private var outline: some View {
        switch shape {
        case .rounded:
            RoundedRectangle(cornerRadius: theme.shapes.medium)
                .stroke(theme.colors.primary, lineWidth: BabyLogSizes.guideOutlineWidth)
        case .small:
            RoundedRectangle(cornerRadius: theme.shapes.small)
                .stroke(theme.colors.primary, lineWidth: BabyLogSizes.guideOutlineWidth)
        case .circle:
            Circle()
                .stroke(theme.colors.primary, lineWidth: BabyLogSizes.guideOutlineWidth)
        }
    }
}

extension View {
    func guideTarget(_ target: GuideTarget, shape: GuideOutlineShape = .rounded) -> some View {
        modifier(GuideTargetModifier(target: target, shape: shape))
    }

    @ViewBuilder func guideTarget(_ target: GuideTarget?) -> some View {
        if let target { guideTarget(target) } else { self }
    }
}

/** Текст шага гайда вверху нижнего листа. Вне гайда ничего не показывает. */
struct GuideSheetHint: View {
    @Environment(\.guide) private var guide
    @Environment(\.babyLogTheme) private var theme

    var body: some View {
        if let hint = guide?.sheetHint {
            HStack(alignment: .top, spacing: theme.spacing.s) {
                AppIcon("ic_help", tint: theme.colors.onPrimaryContainer)
                Text(hint)
                    .appTextStyle(theme.typography.bodyMedium)
                    .foregroundStyle(theme.colors.onPrimaryContainer)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, theme.spacing.m)
            .padding(.vertical, theme.spacing.s)
            .frame(maxWidth: .infinity)
            .background(theme.colors.primaryContainer, in: RoundedRectangle(cornerRadius: theme.shapes.small))
        }
    }
}
