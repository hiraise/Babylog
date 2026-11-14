import SwiftUI

/** Деление оси: значение в единицах графика и подпись. */
struct AxisTick {
    var value: Float
    var label: String
}

private let lineChartHeight: CGFloat = 168
private let lineStroke: CGFloat = 2
private let gridWidth: CGFloat = 1
private let dotRadius: CGFloat = 3.5
private let lastDotRadius: CGFloat = 5
private let dotRing: CGFloat = 2

/**
 Линия с точками измерений — вес за всё время (ADR-0028). Последняя точка крупнее, каждая отделена
 от линии кольцом цвета карточки. `points` — пары (x, y) в единицах осей; x от 0 до `xMax`.
 */
struct LineChart: View {
    @Environment(\.babyLogTheme) private var theme

    let points: [(x: Float, y: Float)]
    let xMax: Float
    let yMin: Float
    let yMax: Float
    let yTicks: [AxisTick]
    let xTicks: [AxisTick]
    let color: Color
    let description: String

    var body: some View {
        Canvas { context, size in
            let yLabels = yTicks.map {
                context.resolve(Text($0.label).font(theme.typography.labelSmall.font).foregroundColor(theme.colors.onSurfaceVariant))
            }
            let xLabels = xTicks.map {
                context.resolve(Text($0.label).font(theme.typography.labelSmall.font).foregroundColor(theme.colors.onSurfaceVariant))
            }
            let left = (yLabels.map { $0.measure(in: CGSize(width: CGFloat.infinity, height: CGFloat.infinity)).width }.max() ?? 0) + theme.spacing.s
            let right = size.width - theme.spacing.s
            let top = theme.spacing.s
            let bottom = size.height - (xLabels.map { $0.measure(in: CGSize(width: CGFloat.infinity, height: CGFloat.infinity)).height }.max() ?? 0) - theme.spacing.xs
            func x(_ value: Float) -> CGFloat { left + CGFloat(value / xMax) * (right - left) }
            func y(_ value: Float) -> CGFloat { bottom - CGFloat((value - yMin) / (yMax - yMin)) * (bottom - top) }

            for (index, tick) in yTicks.enumerated() {
                let lineY = y(tick.value)
                var grid = Path()
                grid.move(to: CGPoint(x: left, y: lineY))
                grid.addLine(to: CGPoint(x: right, y: lineY))
                context.stroke(grid, with: .color(theme.colors.outlineVariant), lineWidth: gridWidth)
                context.draw(yLabels[index], at: CGPoint(x: yLabels[index].measure(in: CGSize(width: CGFloat.infinity, height: CGFloat.infinity)).width / 2, y: lineY), anchor: .center)
            }
            for (index, tick) in xTicks.enumerated() {
                let width = xLabels[index].measure(in: CGSize(width: CGFloat.infinity, height: CGFloat.infinity)).width
                let labelX = min(max(x(tick.value) - width / 2, left - width / 2), size.width - width)
                context.draw(xLabels[index], at: CGPoint(x: labelX, y: bottom + theme.spacing.xs), anchor: .topLeading)
            }
            var line = Path()
            for (index, point) in points.enumerated() {
                let cg = CGPoint(x: x(point.x), y: y(point.y))
                if index == 0 { line.move(to: cg) } else { line.addLine(to: cg) }
            }
            context.stroke(line, with: .color(color), style: StrokeStyle(lineWidth: lineStroke, lineCap: .round, lineJoin: .round))
            for (index, point) in points.enumerated() {
                let radius = index == points.count - 1 ? lastDotRadius : dotRadius
                let center = CGPoint(x: x(point.x), y: y(point.y))
                context.fill(Path(ellipseIn: CGRect(x: center.x - radius - dotRing, y: center.y - radius - dotRing, width: 2 * (radius + dotRing), height: 2 * (radius + dotRing))), with: .color(theme.colors.surfaceContainerLow))
                context.fill(Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: 2 * radius, height: 2 * radius)), with: .color(color))
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: lineChartHeight)
        .accessibilityLabel(description)
    }
}
