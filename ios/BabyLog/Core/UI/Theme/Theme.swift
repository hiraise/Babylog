import SwiftUI

struct ThemeValues {
    let colors: BabyLogColorScheme
    let typography: BabyLogTypography
    let shapes: BabyLogShapes
    let spacing: Spacing
    let shadows: Shadows
    let chartColors: ChartColors
    let timerColors: TimerColors

    static let light = ThemeValues(
        colors: LightColors,
        typography: BabyLogTypeScale,
        shapes: BabyLogShapeScale,
        spacing: Spacing(),
        shadows: LightShadows,
        chartColors: LightChartColors,
        timerColors: makeTimerColors(LightColors, dark: false)
    )
}

private struct BabyLogThemeKey: EnvironmentKey {
    static let defaultValue = ThemeValues.light
}

private struct ClockKey: EnvironmentKey {
    static let defaultValue: any Clock = SystemClock()
}

extension EnvironmentValues {
    var babyLogTheme: ThemeValues {
        get { self[BabyLogThemeKey.self] }
        set { self[BabyLogThemeKey.self] = newValue }
    }

    var clock: any Clock {
        get { self[ClockKey.self] }
        set { self[ClockKey.self] = newValue }
    }
}

/**
 Тема BabyLog (ADR-0010): цвета, шрифты, формы, отступы и тени доступны через `babyLogTheme`.
 Динамический цвет не используется. `darkPalette` меняет только тёмную тему (ADR-0032);
 по умолчанию тёмный режим следует системе, палитра по умолчанию — «Классика» (ADR-0044).
 */
struct BabyLogTheme<Content: View>: View {
    @Environment(\.colorScheme) private var systemColorScheme

    let clock: any Clock
    let darkTheme: Bool?
    let darkPalette: DarkPalette
    let content: Content

    init(
        clock: any Clock,
        darkTheme: Bool? = nil,
        darkPalette: DarkPalette = .defaultPalette,
        @ViewBuilder content: () -> Content
    ) {
        self.clock = clock
        self.darkTheme = darkTheme
        self.darkPalette = darkPalette
        self.content = content()
    }

    var body: some View {
        let dark = darkTheme ?? (systemColorScheme == .dark)
        let colors = dark ? darkColors(darkPalette) : LightColors
        let values = ThemeValues(
            colors: colors,
            typography: BabyLogTypeScale,
            shapes: BabyLogShapeScale,
            spacing: Spacing(),
            shadows: dark ? DarkShadows : LightShadows,
            chartColors: dark ? darkChartColors(darkPalette) : LightChartColors,
            timerColors: makeTimerColors(colors, dark: dark)
        )
        content
            .environment(\.babyLogTheme, values)
            .environment(\.clock, clock)
            .environment(\.timeZone, clock.timeZone)
            .environment(\.colorScheme, dark ? .dark : .light)
    }
}
