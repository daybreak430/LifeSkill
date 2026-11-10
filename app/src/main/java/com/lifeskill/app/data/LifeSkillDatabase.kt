package com.lifeskill.app.data

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "entries",
    foreignKeys = [ForeignKey(
        entity = EntryRow::class,
        parentColumns = ["id"],
        childColumns = ["parentId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("parentId")],
)
internal data class EntryRow(
    @PrimaryKey val id: String,
    val parentId: String?,
    val kind: String,
    val name: String,
    val goal: String,
    val mastery: String,
    val contentVersion: Long,
    val masteryVersion: Long,
    val position: Long,
    val icon: String?,
    val shortName: String?,
)

@Entity(
    tableName = "edges",
    primaryKeys = ["skillId", "prerequisiteId"],
    foreignKeys = [
        ForeignKey(entity = EntryRow::class, parentColumns = ["id"], childColumns = ["skillId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = EntryRow::class, parentColumns = ["id"], childColumns = ["prerequisiteId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("prerequisiteId")],
)
internal data class EdgeRow(val skillId: String, val prerequisiteId: String)

@Entity(
    tableName = "chats",
    foreignKeys = [ForeignKey(
        entity = EntryRow::class,
        parentColumns = ["id"],
        childColumns = ["ownerId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("ownerId")],
)
internal data class ChatRow(@PrimaryKey val id: String, val ownerId: String?)

@Entity(tableName = "bootstrap")
internal data class BootstrapRow(@PrimaryKey val id: Int = 1)

@Entity(
    tableName = "turns",
    foreignKeys = [ForeignKey(
        entity = ChatRow::class,
        parentColumns = ["id"],
        childColumns = ["chatId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("id", unique = true), Index(value = ["chatId", "sequence"])],
)
internal data class TurnRow(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val id: String,
    val chatId: String,
    val speaker: String,
    val kind: String,
    val text: String,
    val contentVersion: Long,
    val exerciseId: String?,
    val resultId: String?,
    val choices: String?,
)

internal data class LibraryRows(val entries: List<EntryRow>, val edges: List<EdgeRow>)

@Dao
internal abstract class SkillDao {
    @Query("SELECT * FROM entries ORDER BY position, id")
    abstract suspend fun entries(): List<EntryRow>

    @Query("SELECT * FROM edges")
    abstract suspend fun edges(): List<EdgeRow>

    @Transaction
    open suspend fun library() = LibraryRows(entries(), edges())

    @Query("SELECT * FROM entries WHERE id = :id")
    abstract suspend fun entry(id: String): EntryRow?

    @Query("SELECT EXISTS(SELECT 1 FROM bootstrap WHERE id = 1)")
    abstract suspend fun initialized(): Boolean

    @Insert
    abstract suspend fun insertBootstrap(row: BootstrapRow)

    @Query("SELECT COALESCE(MAX(position), 0) + 1 FROM entries")
    abstract suspend fun nextPosition(): Long

    @Insert
    abstract suspend fun insertEntry(entry: EntryRow)

    @Update
    abstract suspend fun updateEntry(entry: EntryRow)

    @Query("DELETE FROM entries WHERE id = :id")
    abstract suspend fun deleteEntry(id: String)

    @Query("DELETE FROM edges WHERE skillId = :id")
    abstract suspend fun removePrerequisites(id: String)

    @Insert
    abstract suspend fun insertEdges(edges: List<EdgeRow>)

    @Query("SELECT * FROM chats WHERE id = :id")
    abstract suspend fun chat(id: String): ChatRow?

    @Insert
    abstract suspend fun insertChat(chat: ChatRow)

    @Query("SELECT * FROM turns WHERE chatId = :chatId ORDER BY sequence")
    abstract fun watchTurns(chatId: String): Flow<List<TurnRow>>

    // 以用户的学习操作排序，避免较早的对话收到迟到回复后抢占续学入口。
    @Query("""
        SELECT chats.ownerId FROM turns
        JOIN chats ON chats.id = turns.chatId
        WHERE turns.speaker = 'USER' AND chats.id = 'tutor:' || chats.ownerId
        ORDER BY turns.sequence DESC LIMIT 1
    """)
    abstract fun watchLatestStudySkillId(): Flow<String?>

    @Query("SELECT * FROM turns WHERE chatId = :chatId ORDER BY sequence DESC LIMIT :limit")
    abstract suspend fun recentTurns(chatId: String, limit: Int): List<TurnRow>

    @Query("SELECT * FROM turns WHERE chatId = :chatId AND exerciseId = :exerciseId AND kind = 'EXERCISE' LIMIT 1")
    abstract suspend fun exercise(chatId: String, exerciseId: String): TurnRow?

    @Query("""
        SELECT * FROM turns WHERE chatId = :chatId AND contentVersion = :version
        AND (kind = 'RESTART' OR (speaker = 'ASSISTANT' AND kind != 'OFF_TOPIC'))
        ORDER BY sequence DESC LIMIT 1
    """)
    abstract suspend fun latestTutorTurn(chatId: String, version: Long): TurnRow?

    @Query("SELECT * FROM turns WHERE id = :id AND chatId = :chatId")
    abstract suspend fun turn(chatId: String, id: String): TurnRow?

    @Insert
    abstract suspend fun insertTurn(turn: TurnRow)

    @Query("DELETE FROM turns WHERE id = :id AND chatId = :chatId")
    abstract suspend fun deleteTurn(chatId: String, id: String)

    @Query("DELETE FROM turns WHERE chatId = :chatId")
    abstract suspend fun clearTurns(chatId: String)
}

@Database(
    entities = [EntryRow::class, EdgeRow::class, ChatRow::class, TurnRow::class, BootstrapRow::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
internal abstract class LifeSkillDatabase : RoomDatabase() {
    abstract fun skills(): SkillDao
}
