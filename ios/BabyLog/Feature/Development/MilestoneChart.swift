import SwiftUI

/** Длина месяца в днях, как в таблице ВОЗ: месяцы там — дни, делённые на 30,4375. */
let daysPerMonth = 30.4375

private let scaleMonths = 18
private let scaleDays = Int((Double(scaleMonths) * daysPerMonth).rounded())
private let axisMonths = [0, 6, 12, scaleMonths]
private let labelRoom = 16.0
private let barHeight = 8.0
private let nowLineWidth = 2.0

/** Возраст в месяцах с одним знаком после запятой, как у ВОЗ: 115 дней — «3,8». */
func formatMonths(_ days: Int) -> String {
    formatMonthValue(Double(days) / daysPerMonth)
}

/** Месяцы из таблицы ВОЗ по-русски: 13.5 — «13,5». */
func formatMonthValue(_ months: Double) -> String {
    String(format: "%.1f", locale: RU, months)
}

/**
 Окна навыков ВОЗ (ADR-0037): строка на навык — подпись и полоса окна на шкале 0–18 месяцев,
 вертикальная линия — возраст ребёнка. Окно, в котором ребёнок сейчас, — `chartColors.milestone`
 и подпись полужирным, остальные — `milestoneOther`. Подложка полосы — `surfaceContainerHighest`.
 Для VoiceOver график — одна фраза: возраст и окно каждого навыка в месяцах.
 */
struct MilestoneChart: View {
    @Environment(\.babyLogTheme) private var theme

    let milestones: [(window: MilestoneWindow, state: MilestoneState)]
    let ageDays: Int
    var rowHeight = 36.0
    var labelWidth = 136.0

    var body: some View {
        HStack(spacing: theme.spacing.s) {
            VStack(spacing: 0) {
                ForEach(milestones.indices, id: \.self) { index in
                    let item = milestones[index]
                    Text(item.window.title)
                        .appTextStyle(theme.typography.bodySmall)
                        .fontWeight(item.state == .inWindow ? .semibold : .regular)
                        .lineLimit(2)
                        .truncationMode(.tail)
                        .frame(width: labelWidth, height: rowHeight, alignment: .leading)
                }
            }
            .padding(.top, labelRoom)
            Canvas { context, size in
                let top = labelRoom
                let bottom = top + rowHeight * Double(milestones.count)
                func x(_ days: Int) -> Double {
                    size.width * Double(min(max(days, 0), scaleDays)) / Double(scaleDays)
                }
                for (index, item) in milestones.enumerated() {
                    let barTop = top + rowHeight * Double(index) + (rowHeight - barHeight) / 2
                    let background = Path(roundedRect: CGRect(x: 0, y: barTop, width: size.width, height: barHeight), cornerRadius: barHeight / 2)
                    context.fill(background, with: .color(theme.colors.surfaceContainerHighest))
                    let left = x(item.window.window.lowerBound)
                    let right = x(item.window.window.upperBound)
                    let window = Path(roundedRect: CGRect(x: left, y: barTop, width: right - left, height: barHeight), cornerRadius: barHeight / 2)
                    context.fill(window, with: .color(item.state == .inWindow ? theme.chartColors.milestone : theme.chartColors.milestoneOther))
                }
                let nowX = x(ageDays)
                var nowLine = Path()
                nowLine.move(to: CGPoint(x: nowX, y: top))
                nowLine.addLine(to: CGPoint(x: nowX, y: bottom))
                context.stroke(nowLine, with: .color(theme.colors.onSurface), lineWidth: nowLineWidth)
                drawLabel(
                    context: context,
                    text: "сейчас",
                    font: theme.typography.labelSmall.font,
                    bold: true,
                    color: theme.colors.onSurface,
                    centerX: nowX,
                    top: 0,
                    width: size.width
                )
                for months in axisMonths {
                    drawLabel(
                        context: context,
                        text: months == scaleMonths ? "\(months) мес" : "\(months)",
                        font: theme.typography.labelSmall.font,
                        color: theme.colors.onSurfaceVariant,
                        centerX: x(Int((Double(months) * daysPerMonth).rounded())),
                        top: bottom,
                        width: size.width
                    )
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: chartHeight)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(description)
    }

    private var chartHeight: Double {
        labelRoom * 2 + rowHeight * Double(milestones.count)
    }

    private var description: String {
        let windows = milestones.map { item in
            " \(item.window.title) — с \(formatMonthValue(item.window.months.lowerBound)) до \(formatMonthValue(item.window.months.upperBound)) мес" +
                (item.state == .inWindow ? ", сейчас в окне" : "")
        }.joined(separator: ";")
        return "Окна навыков ВОЗ, возраст сейчас — \(formatMonths(ageDays)) мес. \(windows)"
    }

    private func drawLabel(
        context: GraphicsContext,
        text: String,
        font: Font,
        bold: Bool = false,
        color: Color,
        centerX: Double,
        top: Double,
        width: Double
    ) {
        let resolved = context.resolve(Text(text).font(font).fontWeight(bold ? .semibold : .regular).foregroundColor(color))
        let measured = resolved.measure(in: CGSize(width: width, height: labelRoom))
        let left = min(max(centerX - measured.width / 2, 0), width - measured.width)
        context.draw(resolved, at: CGPoint(x: left, y: top + (labelRoom - measured.height) / 2), anchor: .topLeading)
    }
}
