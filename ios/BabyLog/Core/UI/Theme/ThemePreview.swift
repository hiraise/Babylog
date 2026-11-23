import SwiftUI

/** Витрина темы для превью и сборки этапа S1b: значения берутся только из `BabyLogTheme`. */
struct ThemePreview: View {
    @Environment(\.babyLogTheme) private var theme

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: theme.spacing.xl) {
                Text("Дневник малыша")
                    .appTextStyle(theme.typography.headlineSmall)
                    .foregroundStyle(theme.colors.onBackground)
                colorRoles
                typeScale
                shapeAndSpacingScale
                components
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(theme.spacing.l)
        }
        .background(theme.colors.background)
    }

    private var colorRoles: some View {
        VStack(alignment: .leading, spacing: theme.spacing.s) {
            sectionTitle("Цвета")
            swatch("primary / onPrimary", container: theme.colors.primary, content: theme.colors.onPrimary)
            swatch(
                "primaryContainer / onPrimaryContainer",
                container: theme.colors.primaryContainer,
                content: theme.colors.onPrimaryContainer
            )
            swatch(
                "tertiaryContainer / onTertiaryContainer",
                container: theme.colors.tertiaryContainer,
                content: theme.colors.onTertiaryContainer
            )
            swatch("error / onError", container: theme.colors.error, content: theme.colors.onError)
        }
    }

    private var typeScale: some View {
        VStack(alignment: .leading, spacing: theme.spacing.s) {
            sectionTitle("Шрифты")
            Text("1:25:40").appTextStyle(theme.typography.displayMedium)
            Text("Дневник малыша").appTextStyle(theme.typography.headlineSmall)
            Text("Кормление · 14:30").appTextStyle(theme.typography.titleLarge)
            Text("Покормили левой грудью, уснул в 14:30").appTextStyle(theme.typography.bodyLarge)
            Text("5 мин назад")
                .appTextStyle(theme.typography.labelMedium)
                .foregroundStyle(theme.colors.onSurfaceVariant)
        }
    }

    private var shapeAndSpacingScale: some View {
        VStack(alignment: .leading, spacing: theme.spacing.s) {
            sectionTitle("Формы и отступы")
            HStack(spacing: theme.spacing.m) {
                ForEach([theme.shapes.extraSmall, theme.shapes.small, theme.shapes.large, theme.shapes.extraLarge], id: \.self) { radius in
                    RoundedRectangle(cornerRadius: radius, style: .circular)
                        .fill(theme.colors.surfaceContainerLow)
                        .frame(maxWidth: .infinity)
                        .frame(height: BabyLogSizes.avatar)
                        .cardShadow(shadow: theme.shadows.card)
                }
            }
            HStack(spacing: theme.spacing.s) {
                ForEach([theme.spacing.xs, theme.spacing.s, theme.spacing.m, theme.spacing.l, theme.spacing.xl], id: \.self) { step in
                    RoundedRectangle(cornerRadius: theme.shapes.extraSmall)
                        .fill(theme.colors.primary)
                        .frame(width: step, height: theme.spacing.s)
                }
            }
        }
    }

    private var components: some View {
        VStack(alignment: .leading, spacing: theme.spacing.m) {
            sectionTitle("Компоненты")
            HStack(spacing: theme.spacing.s) {
                ActionTile(icon: "ic_diaper", label: "Подгузник", caption: "25 мин назад") {}
                ActionTile(icon: "ic_first_aid", label: "Здоровье") {}
                ActionTile(icon: "ic_weight", label: "Вес", caption: "вчера") {}
            }
            HStack(spacing: theme.spacing.s) {
                ChoiceChip(selected: true, label: "Пописал", icon: "ic_diaper_wet") {}
                ChoiceChip(selected: false, label: "Покакал", icon: "ic_diaper_dirty") {}
            }
            HStack(spacing: theme.spacing.s) {
                HintChip(label: "Витамин D") {}
                HintChip(label: "Нурофен") {}
            }
            AppCard {
                ItemRow(
                    icon: "ic_medicine",
                    title: "Лекарство · Витамин D",
                    subtitle: "09:15",
                    note: "3 капли после еды"
                )
            }
        }
    }

    private func sectionTitle(_ title: String) -> some View {
        Text(title)
            .appTextStyle(theme.typography.titleMedium)
            .foregroundStyle(theme.colors.onBackground)
    }

    private func swatch(_ title: String, container: Color, content: Color) -> some View {
        Text(title)
            .appTextStyle(theme.typography.labelMedium)
            .foregroundStyle(content)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(theme.spacing.m)
            .background(container, in: RoundedRectangle(cornerRadius: theme.shapes.extraSmall, style: .circular))
    }
}

#Preview {
    BabyLogTheme(clock: SystemClock(), darkTheme: false) {
        ThemePreview()
    }
}
