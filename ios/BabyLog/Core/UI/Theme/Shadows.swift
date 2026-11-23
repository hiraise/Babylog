import SwiftUI

struct AppShadow {
    let color: Color
    let radius: CGFloat
    let x: CGFloat = 0
    let y: CGFloat = 0
}

/** Мягкая тень карточек и чипов; в тёмной теме `card` равна `nil` (ADR-0010). */
struct Shadows {
    let card: AppShadow?
}

let LightShadows = Shadows(card: AppShadow(color: .black.opacity(0.10), radius: 10))
let DarkShadows = Shadows(card: nil)
