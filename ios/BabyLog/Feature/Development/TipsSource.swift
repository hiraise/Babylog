import Foundation

/** Откуда раздел «Развитие» берёт советы и окна навыков (ADR-0037); в тестах — свои списки. */
protocol TipsSource: Sendable {
    func tips() throws -> [Tip]
    func milestones() throws -> MilestoneTable
}

/**
 Файлы `assets/development/`: разбираются один раз, при первом обращении. Чтение и разбор — работа с
 диском, вызывай не с главного потока.
 */
final class AssetTipsSource: TipsSource, @unchecked Sendable {
    private let parsedTips: LazyThrowingValue<[Tip]>
    private let parsedMilestones: LazyThrowingValue<MilestoneTable>

    init(bundle: Bundle = .main) {
        parsedTips = LazyThrowingValue {
            try parseTips(Self.read(Self.tipsFile, bundle: bundle))
        }
        parsedMilestones = LazyThrowingValue {
            try parseMilestones(Self.read(Self.milestonesFile, bundle: bundle))
        }
    }

    func tips() throws -> [Tip] {
        try parsedTips.get()
    }

    func milestones() throws -> MilestoneTable {
        try parsedMilestones.get()
    }

    private static func read(_ name: String, bundle: Bundle) throws -> String {
        guard let url = bundle.url(forResource: name, withExtension: "json") else {
            throw TipsSourceError.missingResource("\(name).json")
        }
        return try String(contentsOf: url, encoding: .utf8)
    }

    static let tipsFile = "tips_ru"
    static let milestonesFile = "who_motor_milestones"
}

private enum TipsSourceError: LocalizedError {
    case missingResource(String)

    var errorDescription: String? {
        switch self {
        case let .missingResource(name): "Нет встроенного ресурса \(name)"
        }
    }
}

/** Успешное значение вычисляется один раз; после ошибки чтения можно повторить, как в Kotlin `lazy`. */
private final class LazyThrowingValue<Value>: @unchecked Sendable {
    private let lock = NSLock()
    private let load: () throws -> Value
    private var cached: Value?

    init(_ load: @escaping () throws -> Value) {
        self.load = load
    }

    func get() throws -> Value {
        lock.lock()
        defer { lock.unlock() }
        if let cached { return cached }
        let value = try load()
        cached = value
        return value
    }
}
