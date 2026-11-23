import SwiftUI

/**
 Цвета рядов графиков: цвет закреплён за сущностью, а не за местом на графике.
 Каждый цвет даёт не меньше 3:1 к карточке; соседние ряды различимы и при дальтонизме.
 Новый ряд добавляется во все три набора, а не литералом в экране (ADR-0010, ADR-0032).
 */
struct ChartColors {
    let sleep: Color
    let daySleep: Color
    let feeding: Color
    let diaperWet: Color
    let diaperMixed: Color
    let diaperDirty: Color
    let weight: Color
    let height: Color
    let crying: Color
    let milestone: Color
    let milestoneOther: Color
    let temperature: Color
}

let LightChartColors = ChartColors(
    sleep: Color.argb(0xFF6250D6),
    daySleep: Color.argb(0xFF8D8BCF),
    feeding: Color.argb(0xFF348333),
    diaperWet: Color.argb(0xFF2A78D6),
    diaperMixed: Color.argb(0xFFD55181),
    diaperDirty: Color.argb(0xFFB37A00),
    weight: Color.argb(0xFFEB6834),
    height: Color.argb(0xFF0F8A6A),
    crying: Color.argb(0xFFB5338A),
    milestone: Color.argb(0xFF348333),
    milestoneOther: Color.argb(0xFF8C8C8C),
    temperature: Color.argb(0xFFA46A00)
)

let ClassicDarkChartColors = ChartColors(
    sleep: Color.argb(0xFF9085E9),
    daySleep: Color.argb(0xFFC6C3FD),
    feeding: Color.argb(0xFF3F9E3E),
    diaperWet: Color.argb(0xFF3987E5),
    diaperMixed: Color.argb(0xFFD55181),
    diaperDirty: Color.argb(0xFFC98500),
    weight: Color.argb(0xFFD95926),
    height: Color.argb(0xFF199E70),
    crying: Color.argb(0xFFE05A9C),
    milestone: Color.argb(0xFF3F9E3E),
    milestoneOther: Color.argb(0xFF9AA3AD),
    temperature: Color.argb(0xFFE3A13B)
)

let CalmDarkChartColors = ChartColors(
    sleep: Color.argb(0xFF8387EA),
    daySleep: Color.argb(0xFFC0C6FC),
    feeding: Color.argb(0xFF5FA350),
    diaperWet: Color.argb(0xFF3D99DF),
    diaperMixed: Color.argb(0xFFC0558D),
    diaperDirty: Color.argb(0xFFB98C06),
    weight: Color.argb(0xFFD8783D),
    height: Color.argb(0xFF02A99B),
    crying: Color.argb(0xFFBE4673),
    milestone: Color.argb(0xFF5FA350),
    milestoneOther: Color.argb(0xFF9AA2AA),
    temperature: Color.argb(0xFFD9A441)
)
