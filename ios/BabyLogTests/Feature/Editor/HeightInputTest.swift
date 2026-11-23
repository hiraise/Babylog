import Testing
@testable import BabyLog

/** Рост вводят в сантиметрах с десятыми, а хранят в миллиметрах (ADR-0027). */
struct HeightInputTest {
    @Test func inputKeepsCentimetersAndOneTenth() {
        #expect(heightInput("62.5") == "62,5")
        #expect(heightInput("62,57") == "62,5")
        #expect(heightInput("62,") == "62,")
        #expect(heightInput("1000") == "100")
        #expect(heightInput("6 2,5 см") == "62,5")
        #expect(heightInput("62,5,3") == "62,5")
    }

    @Test func parsedHeightIsMillimetersWithinRange() {
        #expect(parseHeight("62,5") == 625)
        #expect(parseHeight("62") == 620)
        #expect(parseHeight("62,") == 620)
        #expect(parseHeight("20") == 200)
        #expect(parseHeight("150") == 1_500)
        for text in ["", ",5", "19,9", "150,1"] {
            #expect(parseHeight(text) == nil)
        }
    }

    @Test func heightIsShownInCentimetersWithoutZeroTenth() {
        #expect(formatHeight(625) == "62,5 см")
        #expect(formatHeight(620) == "62 см")
        for millimeters in [625, 620, 1_005] {
            #expect(parseHeight(heightCentimeters(millimeters)) == millimeters)
        }
    }
}
