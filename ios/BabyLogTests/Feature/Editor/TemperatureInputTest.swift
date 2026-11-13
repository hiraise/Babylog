import Testing
@testable import BabyLog

/** Ввод температуры (ADR-0041): «38,4», «38.4» и «384» — одно и то же, вне 34–43 °C — нет. */
struct TemperatureInputTest {
    @Test func commaDotAndDigitsMeanTheSameTemperature() {
        #expect(parseTemperature("38,4") == 384)
        #expect(parseTemperature("38.4") == 384)
        #expect(parseTemperature("384") == 384)
        #expect(parseTemperature("38") == 380)
        #expect(parseTemperature(" 36,6 ") == 366)
    }

    @Test func incompleteOrOutOfRangeInputIsNotTemperature() {
        for text in ["", "3", "38,", "38,45", "33,9", "43,1", "500"] {
            #expect(parseTemperature(text) == nil)
        }
    }

    @Test func typingKeepsDigitsAndOneDecimalComma() {
        #expect(temperatureInput("38.4") == "38,4")
        #expect(temperatureInput("38,45") == "38,4")
        #expect(temperatureInput("38,") == "38,")
        #expect(temperatureInput("3845") == "384")
        #expect(temperatureInput("38,4°C") == "38,4")
        #expect(temperatureInput("366,6") == "36,6")
    }
}
