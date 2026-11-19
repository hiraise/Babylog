import SwiftUI

private let sparkHeight: CGFloat = 24
private let sparkBarWidth: CGFloat = 8
private let sparkBarRadius: CGFloat = 2
private let sparkStroke: CGFloat = 2
private let sparkDot: CGFloat = 3
private let sparkRing: CGFloat = 1.5

/**
 Мини-график плитки обзора «Аналитики» (ADR-0029): те же столбики, части, зазоры и штриховка,
 что у `BarChart`, но без сетки, подписей и касаний. VoiceOver его не читает: число на плитке говорит то же.
 */
struct SparkBars: View {
    @Environment(\.babyLogTheme) private var theme

    let bars: [ChartBar]
    let maxValue: Float
    var bandColor: Color = .clear

    var body: some View {
        Canvas { context, size in
            guard !bars.isEmpty else { return }
            let slot = size.width / CGFloat(bars.count)
            for (index, bar) in bars.enumerated() {
                var local = context
                local.translateBy(x: CGFloat(index) * slot, y: 0)
                let localSize = CGSize(width: slot, height: size.height)
                if bar.band {
                    local.fill(Path(CGRect(origin: .zero, size: localSize)), with: .color(bandColor))
                }
                drawBar(
                    context: &local,
                    size: localSize,
                    bar: bar,
                    maxValue: maxValue,
                    topInset: 0,
                    maxWidth: sparkBarWidth,
                    cornerRadius: sparkBarRadius,
                    halo: bar.band ? theme.colors.surfaceContainerLow : nil
                )
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: sparkHeight)
        .accessibilityHidden(true)
    }
}

/** Мини-линия замеров: видна форма роста, а не шкала; один замер — одна точка справа. */
struct SparkLine: View {
    @Environment(\.babyLogTheme) private var theme

    let points: [(x: Float, y: Float)]
    let color: Color

    var body: some View {
        Canvas { context, size in
            guard !points.isEmpty else { return }
            let pad = sparkDot + sparkRing
            let xMin = points.map(\.x).min()!
            let xSpan = points.map(\.x).max()! - xMin
            let yMin = points.map(\.y).min()!
            let ySpan = points.map(\.y).max()! - yMin
            func x(_ value: Float) -> CGFloat {
                xSpan > 0 ? pad + CGFloat((value - xMin) / xSpan) * (size.width - 2 * pad) : size.width - pad
            }
            func y(_ value: Float) -> CGFloat {
                ySpan > 0 ? size.height - pad - CGFloat((value - yMin) / ySpan) * (size.height - 2 * pad) : size.height / 2
            }
            if points.count >= 2 {
                var line = Path()
                for (index, point) in points.enumerated() {
                    let cg = CGPoint(x: x(point.x), y: y(point.y))
                    if index == 0 { line.move(to: cg) } else { line.addLine(to: cg) }
                }
                context.stroke(line, with: .color(color), style: StrokeStyle(lineWidth: sparkStroke, lineCap: .round, lineJoin: .round))
            }
            let last = points.last!
            let center = CGPoint(x: x(last.x), y: y(last.y))
            context.fill(Path(ellipseIn: CGRect(x: center.x - pad, y: center.y - pad, width: 2 * pad, height: 2 * pad)), with: .color(theme.colors.surfaceContainerLow))
            context.fill(Path(ellipseIn: CGRect(x: center.x - sparkDot, y: center.y - sparkDot, width: 2 * sparkDot, height: 2 * sparkDot)), with: .color(color))
        }
        .frame(maxWidth: .infinity)
        .frame(height: sparkHeight)
        .accessibilityHidden(true)
    }
}
