import Testing
@testable import BabyLog

/** Системный лист врача получает ровно текст предпросмотра. */
struct DoctorShareTest {
    @Test func activityContainsOnlySummaryText() {
        let text = "Соня, 4 мес 26 дн.\nОРВИ\nТемпература 37,8\nВитамин D"
        #expect(DoctorShareSheet(text: text).activityItems == [text])
    }
}
