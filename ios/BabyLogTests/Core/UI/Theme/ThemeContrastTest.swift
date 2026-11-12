import Foundation
import SwiftUI
import Testing
@testable import BabyLog

private struct Role {
    let name: String
    let path: KeyPath<BabyLogColorScheme, Color>
}

private let roles = [
    Role(name: "primary", path: \.primary), Role(name: "onPrimary", path: \.onPrimary),
    Role(name: "primaryContainer", path: \.primaryContainer), Role(name: "onPrimaryContainer", path: \.onPrimaryContainer),
    Role(name: "inversePrimary", path: \.inversePrimary), Role(name: "secondary", path: \.secondary),
    Role(name: "onSecondary", path: \.onSecondary), Role(name: "secondaryContainer", path: \.secondaryContainer),
    Role(name: "onSecondaryContainer", path: \.onSecondaryContainer), Role(name: "tertiary", path: \.tertiary),
    Role(name: "onTertiary", path: \.onTertiary), Role(name: "tertiaryContainer", path: \.tertiaryContainer),
    Role(name: "onTertiaryContainer", path: \.onTertiaryContainer), Role(name: "background", path: \.background),
    Role(name: "onBackground", path: \.onBackground), Role(name: "surface", path: \.surface),
    Role(name: "onSurface", path: \.onSurface), Role(name: "surfaceVariant", path: \.surfaceVariant),
    Role(name: "onSurfaceVariant", path: \.onSurfaceVariant), Role(name: "surfaceTint", path: \.surfaceTint),
    Role(name: "inverseSurface", path: \.inverseSurface), Role(name: "inverseOnSurface", path: \.inverseOnSurface),
    Role(name: "error", path: \.error), Role(name: "onError", path: \.onError),
    Role(name: "errorContainer", path: \.errorContainer), Role(name: "onErrorContainer", path: \.onErrorContainer),
    Role(name: "outline", path: \.outline), Role(name: "outlineVariant", path: \.outlineVariant),
    Role(name: "scrim", path: \.scrim), Role(name: "surfaceBright", path: \.surfaceBright),
    Role(name: "surfaceDim", path: \.surfaceDim), Role(name: "surfaceContainerLowest", path: \.surfaceContainerLowest),
    Role(name: "surfaceContainerLow", path: \.surfaceContainerLow), Role(name: "surfaceContainer", path: \.surfaceContainer),
    Role(name: "surfaceContainerHigh", path: \.surfaceContainerHigh), Role(name: "surfaceContainerHighest", path: \.surfaceContainerHighest),
    Role(name: "primaryFixed", path: \.primaryFixed), Role(name: "primaryFixedDim", path: \.primaryFixedDim),
    Role(name: "onPrimaryFixed", path: \.onPrimaryFixed), Role(name: "onPrimaryFixedVariant", path: \.onPrimaryFixedVariant),
    Role(name: "secondaryFixed", path: \.secondaryFixed), Role(name: "secondaryFixedDim", path: \.secondaryFixedDim),
    Role(name: "onSecondaryFixed", path: \.onSecondaryFixed), Role(name: "onSecondaryFixedVariant", path: \.onSecondaryFixedVariant),
    Role(name: "tertiaryFixed", path: \.tertiaryFixed), Role(name: "tertiaryFixedDim", path: \.tertiaryFixedDim),
    Role(name: "onTertiaryFixed", path: \.onTertiaryFixed), Role(name: "onTertiaryFixedVariant", path: \.onTertiaryFixedVariant),
]

private let surfaces = [
    "background", "surface", "surfaceVariant", "surfaceBright", "surfaceDim", "surfaceContainerLowest",
    "surfaceContainerLow", "surfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest",
].map { name in roles.first { $0.name == name }! }
private let noAccentSurfaces = Set(["surfaceVariant", "surfaceDim", "surfaceContainerHighest"])
private let accentSurfaces = surfaces.filter { !noAccentSurfaces.contains($0.name) }
private let textMinimum = 4.5
private let nonTextMinimum = 3.0

private struct ContrastPair {
    let foreground: Role
    let background: Role
    let minimum: Double
}

private func on(_ foreground: String, _ background: String, minimum: Double = textMinimum) -> ContrastPair {
    ContrastPair(
        foreground: roles.first { $0.name == foreground }!,
        background: roles.first { $0.name == background }!,
        minimum: minimum
    )
}

private let pairs = [
    on("onPrimary", "primary"), on("onPrimaryContainer", "primaryContainer"),
    on("onSecondary", "secondary"), on("onSecondaryContainer", "secondaryContainer"),
    on("onTertiary", "tertiary"), on("onTertiaryContainer", "tertiaryContainer"),
    on("onError", "error"), on("onErrorContainer", "errorContainer"),
    on("onBackground", "background"), on("inverseOnSurface", "inverseSurface"),
    on("inversePrimary", "inverseSurface"), on("onPrimaryFixed", "primaryFixed"),
    on("onPrimaryFixedVariant", "primaryFixed"), on("onPrimaryFixed", "primaryFixedDim"),
    on("onSecondaryFixed", "secondaryFixed"), on("onSecondaryFixedVariant", "secondaryFixed"),
    on("onTertiaryFixed", "tertiaryFixed"), on("onTertiaryFixedVariant", "tertiaryFixed"),
] + ["onSurface", "onSurfaceVariant"].flatMap { text in surfaces.map { on(text, $0.name) } }
    + ["primary", "error"].flatMap { text in accentSurfaces.map { on(text, $0.name) } }
    + surfaces.map { on("outline", $0.name, minimum: nonTextMinimum) }

private struct Palette {
    let name: String
    let colors: BabyLogColorScheme
    let chart: ChartColors
}

private let palettes = [Palette(name: "светлая", colors: LightColors, chart: LightChartColors)]
    + DarkPalette.allCases.map { Palette(name: "тёмная \($0.rawValue)", colors: darkColors($0), chart: darkChartColors($0)) }

private struct Series {
    let name: String
    let path: KeyPath<ChartColors, Color>
}

private let chartSeries = [
    Series(name: "sleep", path: \.sleep), Series(name: "daySleep", path: \.daySleep),
    Series(name: "feeding", path: \.feeding), Series(name: "diaperWet", path: \.diaperWet),
    Series(name: "diaperMixed", path: \.diaperMixed), Series(name: "diaperDirty", path: \.diaperDirty),
    Series(name: "weight", path: \.weight), Series(name: "height", path: \.height),
    Series(name: "crying", path: \.crying), Series(name: "milestone", path: \.milestone),
    Series(name: "milestoneOther", path: \.milestoneOther), Series(name: "temperature", path: \.temperature),
]
private let chartNeighbors = [(0, 2), (0, 1), (1, 2), (1, 8), (0, 8), (2, 8), (3, 4), (4, 5), (9, 10)]

private struct Vision {
    let name: String
    let matrix: [[Double]]?
    let minimum: Double
}
private let visions = [
    Vision(name: "обычное зрение", matrix: nil, minimum: 15),
    Vision(name: "протанопия", matrix: [
        [0.152286, 1.052583, -0.204868], [0.114503, 0.786281, 0.099216], [-0.003882, -0.048116, 1.051998],
    ], minimum: 8),
    Vision(name: "дейтеранопия", matrix: [
        [0.367322, 0.860646, -0.227968], [0.280085, 0.672501, 0.047413], [-0.011820, 0.042940, 0.968881],
    ], minimum: 8),
]

struct ThemeContrastTest {
    @Test func textAndOutlinePairsReachWCAGContrast() {
        let failures = palettes.flatMap { palette in
            pairs.compactMap { pair -> String? in
                let ratio = contrast(palette.colors[keyPath: pair.foreground.path], palette.colors[keyPath: pair.background.path])
                return ratio >= pair.minimum ? nil : "\(palette.name): \(pair.foreground.name) на \(pair.background.name) — \(ratio):1"
            }
        }
        #expect(failures.isEmpty, Comment(rawValue: failures.joined(separator: "\n")))
    }

    @Test func chartColorsStandOutOnTheCard() {
        let failures = palettes.flatMap { palette in
            chartSeries.compactMap { series -> String? in
                let ratio = contrast(palette.chart[keyPath: series.path], palette.colors.surfaceContainerLow)
                return ratio >= nonTextMinimum ? nil : "\(palette.name): \(series.name) — \(ratio):1"
            }
        }
        #expect(failures.isEmpty, Comment(rawValue: failures.joined(separator: "\n")))
    }

    @Test func neighboringChartColorsDifferUnderColorBlindness() {
        var failures: [String] = []
        for palette in palettes {
            for (first, second) in chartNeighbors {
                for vision in visions {
                    let difference = difference(
                        palette.chart[keyPath: chartSeries[first].path],
                        palette.chart[keyPath: chartSeries[second].path],
                        vision: vision.matrix
                    )
                    if difference < vision.minimum {
                        failures.append("\(palette.name), \(vision.name): \(chartSeries[first].name)/\(chartSeries[second].name) — \(difference)")
                    }
                }
            }
        }
        #expect(failures.isEmpty, Comment(rawValue: failures.joined(separator: "\n")))
    }

    @Test func runningTimerStandsOutAmongTheCards() {
        let checked = [Palette(name: "светлая", colors: LightColors, chart: LightChartColors)]
            + DarkPalette.allCases.filter { $0 != .classic }.map {
                Palette(name: "тёмная \($0.rawValue)", colors: darkColors($0), chart: darkChartColors($0))
            }
        let failures = checked.compactMap { palette -> String? in
            let dark = palette.name != "светлая"
            let ratio = contrast(makeTimerColors(palette.colors, dark: dark).container, palette.colors.surfaceContainerLow)
            return ratio >= 2 ? nil : "\(palette.name): \(ratio):1"
        }
        #expect(failures.isEmpty, Comment(rawValue: failures.joined(separator: "\n")))
    }

    @Test func surfaceTintEqualsSurface() {
        for palette in palettes {
            #expect(palette.colors.surfaceTint.hex == palette.colors.surface.hex)
        }
    }

    @Test func rolesListCoversEveryColorSchemeColor() {
        let mirrored = Mirror(reflecting: LightColors).children.compactMap(\.label)
        #expect(Set(mirrored) == Set(roles.map(\.name)))
        #expect(mirrored.count == roles.count)
    }

    @Test func illnessColorsKeepTextAndBarsReadable() {
        var failures: [String] = []
        for palette in palettes {
            let colors = palette.colors
            let checks = [
                (contrast(colors.onTertiaryContainer, colors.illnessContainer), 4.5, "текст"),
                (contrast(colors.tertiary, colors.illnessContainer), 4.5, "второстепенный текст"),
                (contrast(colors.illnessBand, colors.surfaceContainerLow), 1.08, "полоса"),
            ]
            for (ratio, minimum, name) in checks where ratio < minimum {
                failures.append("\(palette.name): \(name) — \(ratio):1, нужно \(minimum):1")
            }
        }
        #expect(failures.isEmpty, Comment(rawValue: failures.joined(separator: "\n")))
    }
}

private func contrast(_ first: Color, _ second: Color) -> Double {
    let firstLuminance = first.luminance
    let secondLuminance = second.luminance
    return (max(firstLuminance, secondLuminance) + 0.05) / (min(firstLuminance, secondLuminance) + 0.05)
}

private func difference(_ first: Color, _ second: Color, vision: [[Double]]?) -> Double {
    let x = oklab(first, vision: vision)
    let y = oklab(second, vision: vision)
    return 100 * sqrt(pow(x[0] - y[0], 2) + pow(x[1] - y[1], 2) + pow(x[2] - y[2], 2))
}

private func oklab(_ color: Color, vision: [[Double]]?) -> [Double] {
    let channels = [color.rgba.red, color.rgba.green, color.rgba.blue].map { channel in
        channel <= 0.04045 ? channel / 12.92 : pow((channel + 0.055) / 1.055, 2.4)
    }
    let transformed = vision?.map { row in
        min(max(row[0] * channels[0] + row[1] * channels[1] + row[2] * channels[2], 0), 1)
    } ?? channels
    let long = cbrt(0.4122214708 * transformed[0] + 0.5363325363 * transformed[1] + 0.0514459929 * transformed[2])
    let medium = cbrt(0.2119034982 * transformed[0] + 0.6806995451 * transformed[1] + 0.1073969566 * transformed[2])
    let short = cbrt(0.0883024619 * transformed[0] + 0.2817188376 * transformed[1] + 0.6299787005 * transformed[2])
    return [
        0.2104542553 * long + 0.7936177850 * medium - 0.0040720468 * short,
        1.9779984951 * long - 2.4285922050 * medium + 0.4505937099 * short,
        0.0259040371 * long + 0.7827717662 * medium - 0.8086757660 * short,
    ]
}

extension Color {
    var hex: String {
        String(
            format: "#%02X%02X%02X",
            Int((rgba.red * 255).rounded()),
            Int((rgba.green * 255).rounded()),
            Int((rgba.blue * 255).rounded())
        )
    }
}
