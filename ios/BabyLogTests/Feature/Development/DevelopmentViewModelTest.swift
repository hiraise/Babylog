import Foundation
import Testing
@testable import BabyLog

@MainActor
@Suite(.serialized)
struct DevelopmentViewModelTest {
    @Test func loadsBundledDataInBackgroundAndTracksSeenWeeks() async {
        let seen = MemoryDevelopmentSeenStore()
        let model = DevelopmentViewModel(source: AssetTipsSource(), seen: seen)

        #expect(model.data == nil)
        #expect(await waitUntil { model.data != nil })
        #expect(model.data?.milestones.milestones.count == 6)
        #expect(model.data?.tips.isEmpty == false)

        model.markSeen(childId: 1, week: 22)
        #expect(await waitUntil { model.seenWeeks == [1: 22] })
        #expect(seen.seenWeeks.value == [1: 22])
    }
}
