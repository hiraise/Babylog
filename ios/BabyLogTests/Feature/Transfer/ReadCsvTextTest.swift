import Foundation
import Testing
@testable import BabyLog

/** Чтение выбранного файла перед импортом: файл не по размеру и не текст отклоняются до разбора. */
struct ReadCsvTextTest {
    @Test func textFileIsReadAsUTF8() throws {
        let text = "\u{FEFF}type,start\r\nNOTE,2026-08-15 21:50:00,,,Маша\r\n"
        #expect(try readCsvText(InputStream(data: Data(text.utf8))) == text)
    }

    @Test func fileBiggerThanAnyDiaryIsNotReadToEnd() throws {
        let endless = EndlessInputStream()
        do {
            _ = try readCsvText(endless)
            Issue.record("Бесконечный файл должен отклоняться по размеру")
        } catch let error as TransferError {
            #expect(error.message == "файл больше 20 МБ — это не дневник BabyLog")
        }
        #expect(endless.served <= maxImportBytes + 64 * 1024)
    }

    @Test func binaryFileIsRejected() throws {
        let jpegStart = Data([0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10])
        do {
            _ = try readCsvText(InputStream(data: jpegStart))
            Issue.record("Двоичный файл должен отклоняться")
        } catch let error as TransferError {
            #expect(error.message == "это не CSV-файл")
        }
    }
}

private final class EndlessInputStream: InputStream {
    private(set) var served = 0

    override func open() {}
    override func close() {}
    override var streamStatus: Stream.Status { .open }

    override func read(_ buffer: UnsafeMutablePointer<UInt8>, maxLength length: Int) -> Int {
        buffer.update(repeating: 97, count: length)
        served += length
        return length
    }
}
