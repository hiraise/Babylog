import Foundation
import Testing

struct BabyLogAppTests {
    @Test func applicationMetadataAndSharedResourcesAreBundled() {
        let bundle = Bundle.main

        #expect(bundle.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String == "Дневник малыша")
        #expect(bundle.object(forInfoDictionaryKey: "ITSAppUsesNonExemptEncryption") as? Bool == false)

        for name in ["inter_regular", "inter_medium", "inter_semibold", "nunito_extrabold"] {
            #expect(bundle.url(forResource: name, withExtension: "ttf") != nil)
        }
        for name in ["tips_ru", "who_motor_milestones"] {
            #expect(bundle.url(forResource: name, withExtension: "json") != nil)
        }
    }
}
