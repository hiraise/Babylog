import Foundation
import GRDB
import Testing
@testable import BabyLog

private struct RoomSchema: Decodable {
    let database: Database
    struct Database: Decodable {
        let version: Int
        let entities: [Entity]
    }
    struct Entity: Decodable {
        let tableName: String
        let fields: [Field]
        let indices: [Index]?
        let foreignKeys: [ForeignKey]?
    }
    struct Field: Decodable {
        let columnName: String
        let affinity: String
        let notNull: Bool?
    }
    struct Index: Decodable {
        let name: String
        let unique: Bool
        let columnNames: [String]
    }
    struct ForeignKey: Decodable {
        let table: String
        let onDelete: String
        let onUpdate: String
        let columns: [String]
        let referencedColumns: [String]
    }
}

struct AppDatabaseTest {
    @Test func createdSchemaMatchesRoomVersionSeven() throws {
        let schemaURL = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("app/schemas/com.kid.babylog.core.data.room.AppDatabase/7.json")
        let schema = try JSONDecoder().decode(RoomSchema.self, from: Data(contentsOf: schemaURL))
        #expect(schema.database.version == AppDatabase.version)
        let database = try AppDatabase.inMemory()
        defer { try? database.close() }
        try database.queue.read { db in
            let tables = try String.fetchAll(
                db,
                sql: "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name != 'grdb_migrations' ORDER BY name"
            )
            #expect(tables == schema.database.entities.map(\.tableName).sorted())
            for entity in schema.database.entities {
                let columns = try Row.fetchAll(db, sql: "PRAGMA table_info(\(entity.tableName))").map { row in
                    (name: row["name"] as String, type: row["type"] as String, notNull: row["notnull"] as Int == 1)
                }
                #expect(columns.map(\.name) == entity.fields.map(\.columnName))
                #expect(columns.map(\.type) == entity.fields.map(\.affinity))
                #expect(columns.map(\.notNull) == entity.fields.map { $0.notNull ?? false })

                let indices = try Row.fetchAll(db, sql: "PRAGMA index_list(\(entity.tableName))")
                    .filter { row in (row["origin"] as String) == "c" }
                    .map { row -> (name: String, unique: Bool, columns: [String]) in
                        let name = row["name"] as String
                        let columns = try Row.fetchAll(db, sql: "PRAGMA index_info(\(name))").map { $0["name"] as String }
                        return (name: name, unique: row["unique"] as Int == 1, columns: columns)
                    }
                let expectedIndices = entity.indices ?? []
                #expect(indices.map(\.name) == expectedIndices.map(\.name))
                #expect(indices.map(\.unique) == expectedIndices.map(\.unique))
                #expect(indices.map(\.columns) == expectedIndices.map(\.columnNames))

                let foreignKeys = try Row.fetchAll(db, sql: "PRAGMA foreign_key_list(\(entity.tableName))").map { row in
                    (table: row["table"] as String, onDelete: row["on_delete"] as String,
                     onUpdate: row["on_update"] as String, columns: [row["from"] as String], referencedColumns: [row["to"] as String])
                }
                let expectedKeys = entity.foreignKeys ?? []
                #expect(foreignKeys.map(\.table) == expectedKeys.map(\.table))
                #expect(foreignKeys.map(\.onDelete) == expectedKeys.map(\.onDelete))
                #expect(foreignKeys.map(\.onUpdate) == expectedKeys.map(\.onUpdate))
                #expect(foreignKeys.map(\.columns) == expectedKeys.map(\.columns))
                #expect(foreignKeys.map(\.referencedColumns) == expectedKeys.map(\.referencedColumns))
            }
        }
    }
}
