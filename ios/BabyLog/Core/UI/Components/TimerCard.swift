import SwiftUI

private let tileMotion = 0.25

/**
 Плитка таймера сна и кормления. В покое касание запускает таймер, во время работы — останавливает.
 Идущий таймер использует `timerColors`; высота не меняется при появлении времени, чтобы не дёргать ряды.
 `corner` — необязательная кнопка в правом верхнем углу, например смена груди.
 */
struct TimerTile: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.timeZone) private var timeZone
    @State private var lastElapsed: Int64 = 0

    let icon: String
    let label: String
    let runningSince: Int64?
    let now: Int64
    let startLabel: String
    let stopLabel: String
    let onStart: () -> Void
    let onStop: () -> Void
    let runningLabel: String?
    let idleCaption: String?
    let corner: AnyView?

    init(
        icon: String,
        label: String,
        runningSince: Int64?,
        now: Int64,
        startLabel: String,
        stopLabel: String,
        runningLabel: String? = nil,
        idleCaption: String? = nil,
        corner: AnyView? = nil,
        onStart: @escaping () -> Void,
        onStop: @escaping () -> Void
    ) {
        self.icon = icon
        self.label = label
        self.runningSince = runningSince
        self.now = now
        self.startLabel = startLabel
        self.stopLabel = stopLabel
        self.runningLabel = runningLabel
        self.idleCaption = idleCaption
        self.corner = corner
        self.onStart = onStart
        self.onStop = onStop
    }

    var body: some View {
        let spacing = theme.spacing
        let running = runningSince != nil
        let elapsed = runningSince.map { max(now - $0, 0) }
        let timer = theme.timerColors
        AppCard(
            onClick: running ? onStop : onStart,
            onClickLabel: running ? stopLabel : startLabel,
            contentPadding: EdgeInsets(top: spacing.s, leading: spacing.s, bottom: spacing.s, trailing: spacing.s),
            containerColor: running ? timer.container : theme.colors.surfaceContainerLow,
            contentColor: running ? timer.content : theme.colors.onSurface
        ) {
            ZStack(alignment: .topTrailing) {
                VStack(spacing: 0) {
                    AppIcon(
                        icon,
                        size: BabyLogSizes.timerIcon,
                        tint: running ? timer.content : theme.colors.primary
                    )
                    if running {
                        Text(formatTimer(elapsed ?? lastElapsed))
                            .padding(.top, spacing.xs)
                            .appTextStyle(theme.typography.displaySmall)
                            .lineLimit(1)
                            .minimumScaleFactor(0.6)
                            .transition(.opacity.combined(with: .move(edge: .top)))
                    }
                    Text(running ? runningLabel ?? label : label)
                        .padding(.top, spacing.xs)
                        .appTextStyle(theme.typography.labelMedium)
                        .multilineTextAlignment(.center)
                        .lineLimit(1)
                    if !running, let idleCaption {
                        Text(idleCaption)
                            .appTextStyle(theme.typography.bodySmall)
                            .foregroundStyle(theme.colors.onSurfaceVariant)
                            .multilineTextAlignment(.center)
                            .lineLimit(3)
                            .truncationMode(.tail)
                            .transition(.opacity.combined(with: .move(edge: .top)))
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(minHeight: BabyLogSizes.timerMinHeight, alignment: .center)
                if running, let corner {
                    corner
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .animation(.easeInOut(duration: tileMotion), value: running)
        .onAppear { if let elapsed { lastElapsed = elapsed } }
        .onChange(of: elapsed) { _, value in if let value { lastElapsed = value } }
    }
}

/** Кнопка-иконка в углу идущего таймера; цвет наследуется от содержимого плитки. */
struct TimerCornerButton: View {
    let icon: String
    let description: String
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            AppIcon(icon, contentDescription: description)
                .frame(width: BabyLogSizes.touchTarget, height: BabyLogSizes.touchTarget)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/** Широкая плитка плача: иконка слева, время и подписи в строку; поведение как у `TimerTile`. */
struct WideTimerTile: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.timeZone) private var timeZone

    let icon: String
    let label: String
    let runningSince: Int64?
    let now: Int64
    let startLabel: String
    let stopLabel: String
    let onStart: () -> Void
    let onStop: () -> Void
    let idleCaption: String?

    init(
        icon: String,
        label: String,
        runningSince: Int64?,
        now: Int64,
        startLabel: String,
        stopLabel: String,
        idleCaption: String? = nil,
        onStart: @escaping () -> Void,
        onStop: @escaping () -> Void
    ) {
        self.icon = icon
        self.label = label
        self.runningSince = runningSince
        self.now = now
        self.startLabel = startLabel
        self.stopLabel = stopLabel
        self.idleCaption = idleCaption
        self.onStart = onStart
        self.onStop = onStop
    }

    var body: some View {
        let spacing = theme.spacing
        let running = runningSince != nil
        let timer = theme.timerColors
        AppCard(
            onClick: running ? onStop : onStart,
            onClickLabel: running ? stopLabel : startLabel,
            contentPadding: EdgeInsets(top: spacing.m, leading: spacing.l, bottom: spacing.m, trailing: spacing.l),
            containerColor: running ? timer.container : theme.colors.surfaceContainerLow,
            contentColor: running ? timer.content : theme.colors.onSurface
        ) {
            HStack(spacing: spacing.l) {
                AppIcon(
                    icon,
                    size: BabyLogSizes.timerIcon,
                    tint: running ? timer.content : theme.colors.primary
                )
                if let runningSince {
                    Text(formatTimer(max(now - runningSince, 0)))
                        .appTextStyle(theme.typography.headlineMedium)
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                        .layoutPriority(-1)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(label).appTextStyle(theme.typography.labelMedium).lineLimit(1)
                        Text("с \(formatClock(runningSince, timeZone: timeZone))")
                            .appTextStyle(theme.typography.bodySmall)
                            .lineLimit(1)
                            .fixedSize()
                    }
                } else {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(label).appTextStyle(theme.typography.labelMedium).lineLimit(1)
                        if let idleCaption {
                            Text(idleCaption)
                                .appTextStyle(theme.typography.bodySmall)
                                .foregroundStyle(theme.colors.onSurfaceVariant)
                                .lineLimit(2)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .frame(maxWidth: .infinity, minHeight: BabyLogSizes.wideTimerMinHeight, alignment: .leading)
        }
        .animation(.easeInOut(duration: tileMotion), value: running)
    }
}
