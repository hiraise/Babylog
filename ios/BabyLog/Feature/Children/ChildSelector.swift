import SwiftUI

/**
 Выбор ребёнка в шапке (ADR-0035): аватар, «Дневник Маши» и под ним возраст, последние
 вес и рост. Касание открывает `ChildrenSheet`; стрелка подсказывает, что это выбор.
 */
struct ChildSelector: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.timeZone) private var timeZone

    let child: Child
    let lastWeight: Weight?
    let lastHeight: Height?
    let now: Int64
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            HStack(spacing: theme.spacing.m) {
                Avatar(sex: child.sex, photo: child.photo?.jpeg)
                VStack(alignment: .leading, spacing: 0) {
                    Text(diaryTitle(child.name, sex: child.sex))
                        .appTextStyle(theme.typography.headlineSmall)
                        .foregroundStyle(theme.colors.primary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                        .truncationMode(.tail)
                    if let info = childInfo(
                        child: child,
                        lastWeight: lastWeight,
                        lastHeight: lastHeight,
                        today: toLocalDate(now, timeZone: timeZone),
                        timeZone: timeZone
                    ) {
                        Text(info)
                            .appTextStyle(theme.typography.bodySmall)
                            .foregroundStyle(theme.colors.onSurfaceVariant)
                            .lineLimit(1)
                            .truncationMode(.tail)
                    }
                }
                AppIcon("ic_expand", tint: theme.colors.onSurfaceVariant)
            }
            .padding(.trailing, theme.spacing.s)
            .background(theme.colors.background, in: RoundedRectangle(cornerRadius: theme.shapes.small))
        }
        .buttonStyle(.plain)
        .accessibilityHint("выбрать ребёнка")
    }
}

/** Лист «Дети»: все дети, у выбранного — отметка и карандаш, внизу — «Добавить ребёнка». */
struct ChildrenSheet: View {
    @Environment(\.babyLogTheme) private var theme
    @Environment(\.timeZone) private var timeZone

    @Binding var isPresented: Bool
    let children: [Child]
    let selectedId: Int64
    let now: Int64
    let onSelect: (Int64) -> Void
    let onEdit: () -> Void
    let onAdd: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        AppSheet(
            isPresented: $isPresented,
            onDismiss: onDismiss,
            title: "Дети",
            icon: "ic_child",
            edgeToEdge: true
        ) { closer in
            VStack(spacing: 0) {
                ForEach(children, id: \.id) { child in
                    let selected = child.id == selectedId
                    HStack(spacing: 0) {
                        Button {
                            closer.close { onSelect(child.id) }
                        } label: {
                            HStack(spacing: theme.spacing.m) {
                                Avatar(sex: child.sex, photo: child.photo?.jpeg, size: 36)
                                VStack(alignment: .leading, spacing: 0) {
                                    Text(child.name)
                                        .appTextStyle(theme.typography.bodyLarge)
                                        .lineLimit(1)
                                        .truncationMode(.tail)
                                    if let birthDate = child.birthDate {
                                        Text(formatAge(
                                            birth: toLocalDate(birthDate, timeZone: timeZone),
                                            today: toLocalDate(now, timeZone: timeZone),
                                            sex: child.sex,
                                            timeZone: timeZone
                                        ))
                                        .appTextStyle(theme.typography.bodySmall)
                                        .foregroundStyle(theme.colors.onSurfaceVariant)
                                    }
                                }
                                Spacer(minLength: 0)
                                if selected {
                                    AppIcon("ic_check", tint: theme.colors.primary)
                                }
                            }
                            .padding(.leading, theme.spacing.l)
                            .frame(maxWidth: .infinity, minHeight: BabyLogSizes.rowMinHeight)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(selected ? [.isSelected] : [])
                        if selected {
                            Button {
                                closer.close(then: onEdit)
                            } label: {
                                AppIcon("ic_edit", contentDescription: "Изменить данные ребёнка")
                                    .frame(width: BabyLogSizes.touchTarget, height: BabyLogSizes.touchTarget)
                            }
                            .buttonStyle(.plain)
                            .padding(.trailing, theme.spacing.xs)
                        }
                    }
                }
            }
            SheetMenuItem(icon: "ic_add_child", label: "Добавить ребёнка") {
                closer.close(then: onAdd)
            }
        }
    }
}

/** «4 мес 12 дн. · 6,4 кг · 62,5 см»: возраст, последние вес и рост; ничего не известно — nil. */
func childInfo(
    child: Child,
    lastWeight: Weight?,
    lastHeight: Height?,
    today: Date,
    timeZone: TimeZone = .current
) -> String? {
    let parts = [
        child.birthDate.map {
            formatAge(
                birth: toLocalDate($0, timeZone: timeZone),
                today: today,
                sex: child.sex,
                timeZone: timeZone
            )
        },
        lastWeight.map { formatWeight($0.grams) },
        lastHeight.map { formatHeight($0.millimeters) },
    ].compactMap { $0 }
    return parts.isEmpty ? nil : parts.joined(separator: " · ")
}
