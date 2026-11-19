import SwiftUI

/** Полоса на шкале суток: доли суток от 0 до 1. `ring` — обвести цветом фона, чтобы отделить от соседней. */
struct TimelineBand {
    var from: Float
    var to: Float
    var color: Color
    var ring: Bool = false
}

/** Мгновенная отметка — риска во всю высоту полосы. */
struct TimelineTick {
    var at: Float
    var color: Color
}

/**
 Строка шкалы: сутки слева направо. `end` — докуда сутки прошли, дальше шкала пуста;
 `dots` — подгузники под шкалой; `pins` — заметки-булавки над шкалой; `description` — для VoiceOver.
 */
struct TimelineRow {
    var label: String
    var bands: [TimelineBand]
    var ticks: [TimelineTick]
    var dots: [Float]
    var end: Float
    var description: String
    var pins: [Float] = []
}

private let timelineLabelWidth: CGFloat = 56
private let timelineAxisHeight: CGFloat = 16
private let timelineRowHeight: CGFloat = 28
private let stripHeight: CGFloat = 16
private let trackRadius: CGFloat = 4
private let bandRadius: CGFloat = 3
private let minBandWidth: CGFloat = 2
private let tickWidth: CGFloat = 2
private let timelineRing: CGFloat = 1.5
private let timelineDotRadius: CGFloat = 2
private let timelineDotOffset: CGFloat = 6
private let timelineGridWidth: CGFloat = 1
private let pinWidth: CGFloat = 2
private let pinHeadRadius: CGFloat = 3

/**
 «Ритм дня» (ADR-0028): строка на сутки, шкала от 0 до 24 часов с делениями через 6 часов.
 Касание строки вызывает `onRowClick`; на обзоре оно nil — нажимается вся карточка.
 */
struct DayTimeline: View {
    @Environment(\.babyLogTheme) private var theme

    let rows: [TimelineRow]
    let dotColor: Color
    let onRowClick: ((Int) -> Void)?
    let onClickLabel: String?

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: theme.spacing.s) {
                Spacer().frame(width: timelineLabelWidth)
                Canvas { context, size in
                    for hour in stride(from: 0, through: 24, by: 6) {
                        let label = context.resolve(Text("\(hour)").font(theme.typography.labelSmall.font).foregroundColor(theme.colors.onSurfaceVariant))
                        let x = CGFloat(hour) / 24 * size.width
                        let left = hour == 0 ? 0 : hour == 24 ? size.width - label.measure(in: CGSize(width: CGFloat.infinity, height: CGFloat.infinity)).width : x - label.measure(in: CGSize(width: CGFloat.infinity, height: CGFloat.infinity)).width / 2
                        context.draw(label, at: CGPoint(x: left, y: 0), anchor: .topLeading)
                    }
                }
                .frame(height: timelineAxisHeight)
            }
            ForEach(rows.indices, id: \.self) { index in
                if let onRowClick {
                    Button {
                        onRowClick(index)
                    } label: {
                        rowView(rows[index])
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(rows[index].description)
                    .accessibilityHint(onClickLabel ?? "")
                } else {
                    rowView(rows[index])
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(rows[index].description)
                }
            }
        }
        .frame(maxWidth: .infinity)
    }

    private func rowView(_ row: TimelineRow) -> some View {
        HStack(spacing: theme.spacing.s) {
            Text(row.label)
                .appTextStyle(theme.typography.labelMedium)
                .foregroundStyle(theme.colors.onSurfaceVariant)
                .lineLimit(1)
                .frame(width: timelineLabelWidth, alignment: .leading)
            Canvas { context, size in
                drawTimelineRow(context: &context, size: size, row: row)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .frame(maxWidth: .infinity)
        .frame(height: timelineRowHeight)
        .contentShape(Rectangle())
    }

    private func drawTimelineRow(context: inout GraphicsContext, size: CGSize, row: TimelineRow) {
        let top = (size.height - stripHeight - timelineDotOffset) / 2
        let width = size.width
        let ring = timelineRing
        let track = CGRect(x: 0, y: top, width: width * CGFloat(row.end), height: stripHeight)
        context.fill(RoundedRectangle(cornerRadius: trackRadius).path(in: track), with: .color(theme.colors.surfaceVariant))
        for at in [Float(0.25), 0.5, 0.75] where at < row.end {
            var line = Path()
            line.move(to: CGPoint(x: width * CGFloat(at), y: top))
            line.addLine(to: CGPoint(x: width * CGFloat(at), y: top + stripHeight))
            context.stroke(line, with: .color(theme.colors.surfaceContainerLow), lineWidth: timelineGridWidth)
        }
        for band in row.bands {
            let left = CGFloat(band.from) * width
            let bandWidth = max(CGFloat(band.to - band.from) * width, minBandWidth)
            if band.ring {
                context.fill(Path(CGRect(x: left - ring, y: top, width: bandWidth + 2 * ring, height: stripHeight)), with: .color(theme.colors.surfaceContainerLow))
            }
            let rect = CGRect(x: left, y: top, width: bandWidth, height: stripHeight)
            context.fill(RoundedRectangle(cornerRadius: bandRadius).path(in: rect), with: .color(band.color))
        }
        for tick in row.ticks {
            let left = CGFloat(tick.at) * width - tickWidth / 2
            context.fill(Path(CGRect(x: left - ring, y: top, width: tickWidth + 2 * ring, height: stripHeight)), with: .color(theme.colors.surfaceContainerLow))
            context.fill(Path(CGRect(x: left, y: top, width: tickWidth, height: stripHeight)), with: .color(tick.color))
        }
        for at in row.dots {
            let center = CGPoint(x: CGFloat(at) * width, y: top + stripHeight + timelineDotOffset - timelineDotRadius)
            context.fill(Path(ellipseIn: CGRect(x: center.x - timelineDotRadius, y: center.y - timelineDotRadius, width: 2 * timelineDotRadius, height: 2 * timelineDotRadius)), with: .color(dotColor))
        }
        for at in row.pins {
            let x = CGFloat(at) * width
            let head = CGPoint(x: x, y: top - pinHeadRadius)
            var outline = Path()
            outline.move(to: head)
            outline.addLine(to: CGPoint(x: x, y: top + stripHeight))
            context.stroke(outline, with: .color(theme.colors.surfaceContainerLow), lineWidth: pinWidth + 2 * ring)
            context.fill(Path(ellipseIn: CGRect(x: head.x - pinHeadRadius - ring, y: head.y - pinHeadRadius - ring, width: 2 * (pinHeadRadius + ring), height: 2 * (pinHeadRadius + ring))), with: .color(theme.colors.surfaceContainerLow))
            var pin = Path()
            pin.move(to: head)
            pin.addLine(to: CGPoint(x: x, y: top + stripHeight))
            context.stroke(pin, with: .color(theme.colors.onSurface), lineWidth: pinWidth)
            context.fill(Path(ellipseIn: CGRect(x: head.x - pinHeadRadius, y: head.y - pinHeadRadius, width: 2 * pinHeadRadius, height: 2 * pinHeadRadius)), with: .color(theme.colors.onSurface))
        }
    }
}
