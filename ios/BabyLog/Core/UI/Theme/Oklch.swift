import Foundation
import SwiftUI

private let gamutTolerance = 1e-4
private let chromaStep = 0.002

/**
 Цвет из OKLCH: светлота `l` (0–1), хрома `c`, оттенок `h` в градусах.
 Цвет вне охвата sRGB теряет хрому с шагом 0,002, пока не войдёт в него (ADR-0032).
 */
func oklch(_ l: Double, _ c: Double, _ h: Double) -> Color {
    let radians = h * Double.pi / 180
    var chroma = c
    while true {
        let rgb = oklabToLinearSrgb(l, chroma * cos(radians), chroma * sin(radians))
        if chroma <= 0 || rgb.allSatisfy({ $0 >= -gamutTolerance && $0 <= 1 + gamutTolerance }) {
            let channels = rgb.map { Int((encodeSrgb(min(max($0, 0), 1)) * 255).rounded()) }
            return Color.rgb(channels[0], channels[1], channels[2])
        }
        chroma -= chromaStep
    }
}

private func oklabToLinearSrgb(_ l: Double, _ a: Double, _ b: Double) -> [Double] {
    let long = pow(l + 0.3963377774 * a + 0.2158037573 * b, 3)
    let medium = pow(l - 0.1055613458 * a - 0.0638541728 * b, 3)
    let short = pow(l - 0.0894841775 * a - 1.2914855480 * b, 3)
    return [
        4.0767416621 * long - 3.3077115913 * medium + 0.2309699292 * short,
        -1.2684380046 * long + 2.6097574011 * medium - 0.3413193965 * short,
        -0.0041960863 * long - 0.7034186147 * medium + 1.7076147010 * short,
    ]
}

private func encodeSrgb(_ linear: Double) -> Double {
    linear <= 0.0031308 ? 12.92 * linear : 1.055 * pow(linear, 1 / 2.4) - 0.055
}
