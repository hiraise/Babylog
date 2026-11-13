import CoreGraphics

/**
 Шкала отступов (ADR-0010): `padding` и `spacing` в экранах берут шаг отсюда,
 а не числовой литерал. `l` — край экрана и карточка, `m` — между карточками,
 `s` и `xs` — внутри карточки, `xl` и `xxl` — между крупными блоками.
 */
struct Spacing {
    let xs: CGFloat = 4
    let s: CGFloat = 8
    let m: CGFloat = 12
    let l: CGFloat = 16
    let xl: CGFloat = 24
    let xxl: CGFloat = 32
}

/** Размеры самих элементов; отступы между ними задаёт только `Spacing`. */
enum BabyLogSizes {
    static let chipIcon: CGFloat = 18
    static let chipHeight: CGFloat = 32
    static let icon: CGFloat = 24
    static let contextIcon: CGFloat = 20
    static let actionIcon: CGFloat = 26
    static let timerIcon: CGFloat = 32
    static let avatar: CGFloat = 40
    static let healthRowIcon: CGFloat = 44
    static let actionMinHeight: CGFloat = 72
    static let cryingNoteRowHeight: CGFloat = 96
    static let timerMinHeight: CGFloat = 104
    static let wideTimerMinHeight: CGFloat = 48
    static let menuMinHeight: CGFloat = 56
    static let textFieldMinHeight: CGFloat = 56
    static let noteTextMinHeight: CGFloat = 96
    static let touchTarget: CGFloat = 48
    static let badge: CGFloat = 8
    static let navigationHeight: CGFloat = 80
    static let rowMinHeight: CGFloat = 64
    static let daySelectorMaxHeight: CGFloat = 420
    static let illnessChartHeight: CGFloat = 150
    static let illnessChartLabelsHeight: CGFloat = 20
}
