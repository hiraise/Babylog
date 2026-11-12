import SwiftUI
import Testing
import UIKit
@testable import BabyLog

/** Системный launch screen и значок повторяют тему, чтобы холодный запуск не менял цвет. */
struct ThemeWindowTest {
    @Test func launchBackgroundMatchesThemeBackground() {
        let info = Bundle.main.object(forInfoDictionaryKey: "UILaunchScreen") as? [String: Any]
        #expect(info?["UIColorName"] as? String == "LaunchBackground")
        let color = UIColor(named: "LaunchBackground")
        #expect(color != nil)
        let light = color?.resolvedColor(with: UITraitCollection(userInterfaceStyle: .light))
        let dark = color?.resolvedColor(with: UITraitCollection(userInterfaceStyle: .dark))
        #expect(light.map { Color(uiColor: $0).hex } == LightColors.background.hex)
        #expect(dark.map { Color(uiColor: $0).hex } == darkColors(.defaultPalette).background.hex)
    }

    @Test func appIconAndAllVectorAssetsAreBundled() {
        #expect(UIImage(named: "ic_feeding") != nil)
        #expect(UIImage(named: "ic_diaper_mixed") != nil)
        #expect(UIImage(named: "ic_child_girl") != nil)
        #expect(UIImage(named: "ic_thermometer") != nil)
        #expect(Bundle.main.object(forInfoDictionaryKey: "CFBundleIcons") != nil)
    }
}
