import CoreGraphics

/**
 Скругления по макету: 11 — поля и меню, 18 — чипы и карточки, 24 — FAB,
 30 — пикеры и нижние листы. Кнопки остаются пилюлями по высоте (ADR-0010).
 */
struct BabyLogShapes {
    let extraSmall: CGFloat = 11
    let small: CGFloat = 18
    let medium: CGFloat = 18
    let large: CGFloat = 24
    let extraLarge: CGFloat = 30
}

let BabyLogShapeScale = BabyLogShapes()
