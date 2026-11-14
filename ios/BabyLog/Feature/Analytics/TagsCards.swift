import SwiftUI

private let tagNameWidth: CGFloat = 124
private let tagCountWidth: CGFloat = 28
private let tagStripHeight: CGFloat = 12
private let tagCellGap: CGFloat = 1.5
private let tagCellRadius: CGFloat = 2
private let tagLineIconSize: CGFloat = 18
private let tagRowMinHeight: CGFloat = 48

/**
 «Как часто» (ADR-0031): по строке на метку — сколько раз за период и в какие сутки или недели
 она была. Касание строки открывает метку на «Заметках».
 */
struct TagsFrequencyCard: View {
    @Environment(\.babyLogTheme) private var theme
    let model: TagsModel
    let byWeeks: Bool
    let onOpenTag: (String) -> Void

    var body: some View {
        AppCard {
            VStack(alignment: .leading, spacing: theme.spacing.xs) {
                CardHeader(icon: "ic_tag", title: "Как часто", trailing: byWeeks ? "по неделям" : "по дням")
                Text(model.counts())
                    .appTextStyle(theme.typography.bodySmall)
                    .foregroundStyle(theme.colors.onSurfaceVariant)
                if model.frequencies.isEmpty {
                    Text("За этот период меток нет")
                        .appTextStyle(theme.typography.bodyMedium)
                        .foregroundStyle(theme.colors.onSurfaceVariant)
                }
                ForEach(model.frequencies.indices, id: \.self) { index in
                    let tag = model.frequencies[index]
                    let marked = tag.marks.count { $0 }
                    Button {
                        onOpenTag(tag.tag)
                    } label: {
                        HStack(spacing: theme.spacing.s) {
                            Text(tag.tag)
                                .appTextStyle(theme.typography.bodyMedium)
                                .foregroundStyle(theme.colors.primary)
                                .lineLimit(1)
                                .truncationMode(.tail)
                                .frame(width: tagNameWidth, alignment: .leading)
                            Text("\(tag.count)")
                                .appTextStyle(theme.typography.titleSmall)
                                .frame(width: tagCountWidth, alignment: .trailing)
                            MarksStrip(marks: tag.marks)
                                .frame(maxWidth: .infinity)
                        }
                        .frame(maxWidth: .infinity, minHeight: tagRowMinHeight)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityHint("показать записи с меткой")
                    .accessibilityLabel("\(tag.tag): \(tag.count) \(plural(tag.count, one: "раз", few: "раза", many: "раз")), \(marked) из \(tag.marks.count) \(byWeeks ? "недель" : "суток")")
                }
                if model.untaggedNotes > 0 {
                    let n = model.untaggedNotes
                    Text("Ещё \(n) \(plural(n, one: "заметка", few: "заметки", many: "заметок")) без меток — в счёт не входят, они есть в ленте «Заметок»")
                        .appTextStyle(theme.typography.bodySmall)
                        .foregroundStyle(theme.colors.onSurfaceVariant)
                }
            }
        }
        .frame(maxWidth: .infinity)
    }
}

/** Клетки по столбикам периода: была метка — `secondary`, не было — `surfaceContainerHigh`. */
private struct MarksStrip: View {
    @Environment(\.babyLogTheme) private var theme
    let marks: [Bool]

    var body: some View {
        Canvas { context, size in
            guard !marks.isEmpty else { return }
            let cell = (size.width - tagCellGap * CGFloat(marks.count - 1)) / CGFloat(marks.count)
            for (index, marked) in marks.enumerated() {
                let rect = CGRect(x: CGFloat(index) * (cell + tagCellGap), y: 0, width: cell, height: size.height)
                context.fill(
                    RoundedRectangle(cornerRadius: tagCellRadius).path(in: rect),
                    with: .color(marked ? theme.colors.secondary : theme.colors.surfaceContainerHigh)
                )
            }
        }
        .frame(height: tagStripHeight)
    }
}

/** «Метки и режим»: сон и плач в сутки с меткой против суток без неё; мало данных — подсказка. */
struct TagsRoutineCard: View {
    @Environment(\.babyLogTheme) private var theme
    let model: TagsModel

    var body: some View {
        AppCard {
            VStack(alignment: .leading, spacing: theme.spacing.m) {
                CardHeader(icon: "ic_schedule", title: "Метки и режим", trailing: "сутки с меткой и без неё")
                ForEach(model.routine.indices, id: \.self) { index in
                    let tag = model.routine[index]
                    VStack(alignment: .leading, spacing: theme.spacing.xs) {
                        Text("\(tag.tag) · \(tag.days) \(plural(tag.days, one: "день", few: "дня", many: "дней"))")
                            .appTextStyle(theme.typography.titleSmall)
                        if tag.sleep == nil {
                            Text("мало данных: нужно хотя бы \(minRoutineDays) дня с меткой и \(minRoutineDays) без")
                                .appTextStyle(theme.typography.bodySmall)
                                .foregroundStyle(theme.colors.onSurfaceVariant)
                        } else {
                            FlowRow(spacing: theme.spacing.l, rowSpacing: theme.spacing.xs) {
                                RoutineLine(icon: "ic_sleep", text: tag.sleep!)
                                if let crying = tag.crying {
                                    RoutineLine(icon: "ic_crying", text: crying)
                                }
                            }
                        }
                    }
                    .accessibilityElement(children: .combine)
                }
            }
        }
        .frame(maxWidth: .infinity)
    }
}

private struct RoutineLine: View {
    @Environment(\.babyLogTheme) private var theme
    let icon: String
    let text: String

    var body: some View {
        HStack(spacing: theme.spacing.xs) {
            AppIcon(icon, size: tagLineIconSize, tint: theme.colors.primary)
            Text(text)
                .appTextStyle(theme.typography.bodyMedium)
                .lineLimit(1)
        }
    }
}

/** Что именно считаем: текст заметок не разбирается, это совпадения, а не причины. */
struct TagsFootnote: View {
    @Environment(\.babyLogTheme) private var theme

    var body: some View {
        Text("Считаем по меткам и времени записей, текст заметок не разбираем — всё остаётся на телефоне. Это совпадения в записях, а не причины.")
            .appTextStyle(theme.typography.bodySmall)
            .foregroundStyle(theme.colors.onSurfaceVariant)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}
