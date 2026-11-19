import SwiftUI

/** Замер: время и десятые градуса. */
struct TemperaturePoint {
    var time: Int64
    var tenths: Int
}

/** Строка приёмов под графиком: подпись (название лекарства) и время каждого приёма. */
struct MarkRow {
    var label: String
    var times: [Int64]
}

/** Отметка на шкале времени — вертикальный пунктир с подписью внизу: «+ отит» в день, с которого он стоит. */
struct TimeMarker {
    var time: Int64
    var label: String
}

private let temperaturePlotHeight: CGFloat = 140
private let temperatureAxisHeight: CGFloat = 20
private let temperatureRowHeight: CGFloat = 32
private let temperatureRowDotOffset: CGFloat = 24
private let temperatureLabelGutter: CGFloat = 28
private let temperatureLineWidth: CGFloat = 2
private let temperatureGridWidth: CGFloat = 1
private let temperaturePointRadius: CGFloat = 3
private let temperatureLastRadius: CGFloat = 5
private let temperatureLastRing: CGFloat = 2
private let temperatureMarkRadius: CGFloat = 4
private let temperatureDash: CGFloat = 3
private let temperatureMinLow = 360
private let temperatureMinHigh = 390

/**
 График температуры на экране болезни (ADR-0041): линия по замерам на шкале времени, сутки и целые
 градусы — сеткой, лекарства — нейтральными точками в отдельных строках. Без порогов и «опасных» зон.
 */
struct TemperatureChart: View {
    @Environment(\.babyLogTheme) private var theme

    let points: [TemperaturePoint]
    let marks: [MarkRow]
    let from: Int64
    let to: Int64
    let days: [(time: Int64, label: String)]
    let markers: [TimeMarker]
    let lineColor: Color
    let description: String

    var body: some View {
        Canvas { context, size in
            let left = temperatureLabelGutter
            let right = size.width - theme.spacing.s
            let top = theme.spacing.s
            let plotBottom = temperaturePlotHeight
            let span = max(to - from, 1)
            func x(_ time: Int64) -> CGFloat { left + CGFloat(time - from) / CGFloat(span) * (right - left) }
            let low = min(temperatureMinLow, Int(floor(Double(points.map(\.tenths).min() ?? temperatureMinLow) / 10)) * 10)
            let high = max(temperatureMinHigh, Int(ceil(Double(points.map(\.tenths).max() ?? temperatureMinHigh) / 10)) * 10)
            func y(_ tenths: Int) -> CGFloat { plotBottom - CGFloat(tenths - low) / CGFloat(high - low) * (plotBottom - top) }

            for (index, day) in days.enumerated() {
                if index > 0 {
                    var line = Path()
                    line.move(to: CGPoint(x: x(day.time), y: top))
                    line.addLine(to: CGPoint(x: x(day.time), y: plotBottom))
                    context.stroke(line, with: .color(theme.colors.outlineVariant), lineWidth: temperatureGridWidth)
                }
                let end = index + 1 < days.count ? days[index + 1].time : to
                let label = context.resolve(Text(day.label).font(theme.typography.labelSmall.font).foregroundColor(theme.colors.onSurfaceVariant))
                let center = (x(max(day.time, from)) + x(min(end, to))) / 2
                let labelX = min(max(center - label.measure(in: CGSize(width: CGFloat.infinity, height: CGFloat.infinity)).width / 2, left), right - label.measure(in: CGSize(width: CGFloat.infinity, height: CGFloat.infinity)).width)
                context.draw(label, at: CGPoint(x: labelX, y: plotBottom + theme.spacing.xs), anchor: .topLeading)
            }
            for tenths in stride(from: low, through: high, by: 10) {
                let lineY = y(tenths)
                var grid = Path()
                grid.move(to: CGPoint(x: left, y: lineY))
                grid.addLine(to: CGPoint(x: right, y: lineY))
                context.stroke(grid, with: .color(theme.colors.outlineVariant), lineWidth: temperatureGridWidth)
                if (tenths > low && tenths < high) || tenths == high {
                    drawChartLabel(context, text: "\(tenths / 10)", at: CGPoint(x: 0, y: lineY), anchor: .center, style: theme.typography.labelSmall, color: theme.colors.onSurfaceVariant)
                }
            }
            for marker in markers {
                let markerX = x(marker.time)
                var line = Path()
                line.move(to: CGPoint(x: markerX, y: top))
                line.addLine(to: CGPoint(x: markerX, y: plotBottom))
                context.stroke(line, with: .color(theme.colors.tertiary), style: StrokeStyle(lineWidth: temperatureGridWidth, dash: [temperatureDash, temperatureDash]))
                let label = context.resolve(Text(marker.label).font(theme.typography.labelSmall.font).foregroundColor(theme.colors.tertiary))
                let labelX = max(markerX - label.measure(in: CGSize(width: CGFloat.infinity, height: CGFloat.infinity)).width - theme.spacing.xs, left)
                context.draw(label, at: CGPoint(x: labelX, y: plotBottom - label.measure(in: CGSize(width: CGFloat.infinity, height: CGFloat.infinity)).height - theme.spacing.xs), anchor: .topLeading)
            }
            if points.count >= 2 {
                var line = Path()
                for (index, point) in points.enumerated() {
                    let cg = CGPoint(x: x(point.time), y: y(point.tenths))
                    if index == 0 { line.move(to: cg) } else { line.addLine(to: cg) }
                }
                context.stroke(line, with: .color(lineColor), style: StrokeStyle(lineWidth: temperatureLineWidth, lineCap: .round, lineJoin: .round))
            }
            for (index, point) in points.enumerated() {
                let center = CGPoint(x: x(point.time), y: y(point.tenths))
                let radius = index == points.count - 1 ? temperatureLastRadius : temperaturePointRadius
                if index == points.count - 1 {
                    context.fill(Path(ellipseIn: CGRect(x: center.x - radius - temperatureLastRing, y: center.y - radius - temperatureLastRing, width: 2 * (radius + temperatureLastRing), height: 2 * (radius + temperatureLastRing))), with: .color(theme.colors.surfaceContainerLow))
                }
                context.fill(Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: 2 * radius, height: 2 * radius)), with: .color(lineColor))
            }
            for (index, row) in marks.enumerated() {
                let rowTop = temperaturePlotHeight + temperatureAxisHeight + CGFloat(index) * temperatureRowHeight
                drawChartLabel(context, text: row.label, at: CGPoint(x: left, y: rowTop), anchor: .topLeading, style: theme.typography.labelSmall, color: theme.colors.onSurfaceVariant)
                for time in row.times {
                    let center = CGPoint(x: x(time), y: rowTop + temperatureRowDotOffset)
                    context.fill(Path(ellipseIn: CGRect(x: center.x - temperatureMarkRadius, y: center.y - temperatureMarkRadius, width: 2 * temperatureMarkRadius, height: 2 * temperatureMarkRadius)), with: .color(theme.colors.onSurfaceVariant))
                }
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: temperaturePlotHeight + temperatureAxisHeight + temperatureRowHeight * CGFloat(marks.count))
        .accessibilityLabel(description)
    }
}
