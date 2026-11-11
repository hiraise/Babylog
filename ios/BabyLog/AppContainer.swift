import Observation

/// Ручная сборка зависимостей: весь граф приложения в одном месте, без DI-фреймворка (ADR-0007).
@Observable final class AppContainer {
    /// Системное время в текущем часовом поясе устройства (пояс может смениться на ходу).
    let clock: any Clock

    init(clock: any Clock = SystemClock()) {
        self.clock = clock
    }
}
