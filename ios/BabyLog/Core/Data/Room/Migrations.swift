import GRDB

/** Схема последней Room-версии создаётся сразу как `v7`; истории миграций 1→6 на iOS нет. */
func diaryMigrator(createFirstChild: Bool) -> DatabaseMigrator {
    var migrator = DatabaseMigrator()
    migrator.registerMigration("v7") { db in
        try db.execute(sql: """
            CREATE TABLE IF NOT EXISTS `children` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `birthDate` INTEGER, `sex` TEXT, `photo` BLOB)
            """)
        try db.execute(sql: """
            CREATE TABLE IF NOT EXISTS `events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `childId` INTEGER NOT NULL, `type` TEXT NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER, `detail` TEXT NOT NULL, `note` TEXT NOT NULL, FOREIGN KEY(`childId`) REFERENCES `children`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )
            """)
        try db.execute(sql: "CREATE INDEX IF NOT EXISTS `index_events_childId` ON `events` (`childId`)")
        try db.execute(sql: """
            CREATE TABLE IF NOT EXISTS `event_photos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eventId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `jpeg` BLOB NOT NULL, FOREIGN KEY(`eventId`) REFERENCES `events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )
            """)
        try db.execute(sql: "CREATE INDEX IF NOT EXISTS `index_event_photos_eventId` ON `event_photos` (`eventId`)")
        if createFirstChild {
            try db.execute(sql: "INSERT INTO `children` (`name`, `birthDate`) VALUES (?, NULL)", arguments: [Child.defaultName])
        }
    }
    return migrator
}
