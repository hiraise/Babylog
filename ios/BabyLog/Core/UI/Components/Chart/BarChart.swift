import SwiftUI

/** Часть столбика: `value` в единицах шкалы и цвет ряда из `BabyLogTheme.chartColors`. */
struct BarPart {
    var value: Float
    var color: Color
}

/**
 Столбик `BarChart`: `parts` снизу вверх, `label` — подпись под ним (пустая — без подписи),
 `description` — что прочитает VoiceOver. `partial` — сутки или неделя ещё идут: столбик заштрихован.
 `band` — сутки болезни (ADR-0040): за столбиком полоса цвета `bandColor` во всю высоту слота;
 соседние полосы сливаются в одну. Сам столбик на полосе — в кайме цвета карточки.
 */
struct ChartBar {
    var parts: [BarPart]
    var label: String
    var description: String
    var partial: Bool = false
    var band: Bool = false
}

private let chartHeight: CGFloat = 112
private let maxBarWidth: CGFloat = 24
private let barRadius: CGFloat = 4
private let barGap: CGFloat = 2
private let hatchStep: CGFloat = 5
private let hatchWidth: CGFloat = 1.5
private let chartLineWidth: CGFloat = 1
private let haloWidth: CGFloat = 1.5
private let barShare: CGFloat = 0.62
private let dimmed: Double = 0.4
private let partialFill: Double = 0.18

/**
 Столбчатая диаграмма «Аналитики» (ADR-0028): верх столбика скруглён, части разделяет зазор,
 незаконченный период заштрихован. Касание выбирает столбик и бледнит остальные; повторное касание
 снимает выбор. Сетка и подписи шкалы справа, подписи столбиков — под общей базовой линией.
 */
struct BarChart: View {
    @Environment(\.babyLogTheme) private var theme

    let bars: [ChartBar]
    let maxValue: Float
    let maxLabel: String
    let midLabel: String
    let selected: Int?
    let onSelect: (Int?) -> Void
    var bandColor: Color = .clear

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                Canvas { context, size in
                    let top = theme.spacing.s
                    let mid = top + (size.height - top) / 2
                    let right = size.width - theme.spacing.xxl
                    for y in [top, mid, size.height - chartLineWidth / 2] {
                        var line = Path()
                        line.move(to: CGPoint(x: 0, y: y))
                        line.addLine(to: CGPoint(x: right, y: y))
                        context.stroke(line, with: .color(theme.colors.outlineVariant), lineWidth: chartLineWidth)
                    }
                    drawChartLabel(context, text: maxLabel, at: CGPoint(x: size.width, y: top), anchor: .center, style: theme.typography.labelSmall, color: theme.colors.onSurfaceVariant, trailing: true)
                    drawChartLabel(context, text: midLabel, at: CGPoint(x: size.width, y: mid), anchor: .center, style: theme.typography.labelSmall, color: theme.colors.onSurfaceVariant, trailing: true)
                }
                HStack(spacing: 0) {
                    ForEach(bars.indices, id: \.self) { index in
                        let bar = bars[index]
                        Button {
                            onSelect(selected == index ? nil : index)
                        } label: {
                            Canvas { context, size in
                                if bar.band {
                                    context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(bandColor))
                                }
                                drawBar(
                                    context: &context,
                                    size: size,
                                    bar: bar,
                                    maxValue: maxValue,
                                    topInset: theme.spacing.s,
                                    halo: bar.band ? theme.colors.surfaceContainerLow : nil
                                )
                            }
                            .opacity(selected != nil && selected != index ? dimmed : 1)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(bar.description)
                        .accessibilityAddTraits(selected == index ? .isSelected : [])
                    }
                }
                .padding(.trailing, theme.spacing.xxl)
            }
            .frame(height: chartHeight)
            HStack(spacing: 0) {
                ForEach(bars.indices, id: \.self) { index in
                    Text(bars[index].label)
                        .appTextStyle(theme.typography.labelSmall)
                        .foregroundStyle(selected == index ? theme.colors.onSurface : theme.colors.onSurfaceVariant)
                        .lineLimit(1)
                        .fixedSize()
                        .frame(maxWidth: .infinity)
                }
            }
            .padding(.trailing, theme.spacing.xxl)
        }
        .frame(maxWidth: .infinity)
    }
}

@discardableResult
func drawChartLabel(
    _ context: GraphicsContext,
    text: String,
    at point: CGPoint,
    anchor: UnitPoint,
    style: AppTextStyle,
    color: Color,
    trailing: Bool = false
) -> GraphicsContext.ResolvedText {
    let resolved = context.resolve(Text(text).font(style.font).foregroundColor(color))
    let adjusted = trailing ? CGPoint(x: point.x - resolved.measure(in: CGSize(width: CGFloat.infinity, height: CGFloat.infinity)).width / 2, y: point.y) : point
    context.draw(resolved, at: adjusted, anchor: anchor)
    return resolved
}

/** Общий рисунок большого столбика и мини-графика: части, зазоры, кайма и штриховка. */
func drawBar(
    context: inout GraphicsContext,
    size: CGSize,
    bar: ChartBar,
    maxValue: Float,
    topInset: CGFloat,
    maxWidth: CGFloat = maxBarWidth,
    cornerRadius: CGFloat = barRadius,
    halo: Color? = nil
) {
    guard maxValue > 0 else { return }
    let width = min(maxWidth, size.width * barShare)
    let left = (size.width - width) / 2
    let plot = size.height - topInset
    let visible = bar.parts.filter { $0.value > 0 }
    if let halo, !visible.isEmpty {
        let heights = visible.map { max(CGFloat($0.value / maxValue) * plot, chartLineWidth) }
        let top = size.height - heights.reduce(0, +) - barGap * CGFloat(visible.count - 1)
        let ring = haloWidth
        let rect = CGRect(x: left - ring, y: top - ring, width: width + 2 * ring, height: size.height - top + ring)
        let shape = UnevenRoundedRectangle(
            topLeadingRadius: cornerRadius + ring,
            bottomLeadingRadius: 0,
            bottomTrailingRadius: 0,
            topTrailingRadius: cornerRadius + ring
        )
        context.fill(shape.path(in: rect), with: .color(halo))
    }
    var bottom = size.height
    for (index, part) in visible.enumerated() {
        let top = bottom - max(CGFloat(part.value / maxValue) * plot, chartLineWidth)
        let rect = CGRect(x: left, y: top, width: width, height: bottom - top)
        let shape = UnevenRoundedRectangle(
            topLeadingRadius: index == visible.count - 1 ? cornerRadius : 0,
            bottomLeadingRadius: 0,
            bottomTrailingRadius: 0,
            topTrailingRadius: index == visible.count - 1 ? cornerRadius : 0
        )
        let path = shape.path(in: rect)
        if bar.partial {
            context.fill(path, with: .color(part.color.opacity(partialFill)))
            var hatch = context
            hatch.clip(to: path)
            let height = bottom - top
            var x = left - height
            while x < left + width {
                var line = Path()
                line.move(to: CGPoint(x: x, y: bottom))
                line.addLine(to: CGPoint(x: x + height, y: top))
                hatch.stroke(line, with: .color(part.color), lineWidth: hatchWidth)
                x += hatchStep
            }
        } else {
            context.fill(path, with: .color(part.color))
        }
        bottom = top - barGap
    }
}
