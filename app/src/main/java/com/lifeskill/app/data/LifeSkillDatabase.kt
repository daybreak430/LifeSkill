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
    val directionColor: String?,
    val sourceNotes: String? = null,
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
    val exerciseNumber: Int? = null,
    val searchContext: String? = null,
    val attachments: String? = null,
)

@Entity(
    tableName = "chat_attachment_parts",
    primaryKeys = ["attachmentId", "part"],
    foreignKeys = [ForeignKey(
        entity = TurnRow::class, parentColumns = ["id"], childColumns = ["turnId"], onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("turnId")],
)
internal data class ChatAttachmentPart(val attachmentId: String, val part: Int, val turnId: String, val data: ByteArray)

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

    @Query("SELECT * FROM entries WHERE parentId IS :parentId")
    abstract suspend fun siblings(parentId: String?): List<EntryRow>

    @Query("SELECT edges.* FROM edges JOIN entries ON entries.id = edges.skillId WHERE entries.parentId = :treeId")
    abstract suspend fun edgesInTree(treeId: String): List<EdgeRow>

    @Query("SELECT EXISTS(SELECT 1 FROM bootstrap WHERE id = :id)")
    abstract suspend fun initialized(id: Int = 1): Boolean

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

    @Insert
    abstract suspend fun insertAttachmentPart(part: ChatAttachmentPart)

    @Query("""
        SELECT data FROM chat_attachment_parts
        JOIN turns ON turns.id = chat_attachment_parts.turnId
        WHERE turns.chatId = :chatId AND attachmentId = :id AND part = :part
    """)
    abstract suspend fun attachmentPart(chatId: String, id: String, part: Int): ByteArray?

    @Query("""
        SELECT COUNT(*) FROM chat_attachment_parts
        JOIN turns ON turns.id = chat_attachment_parts.turnId
        WHERE turns.chatId = :chatId AND attachmentId = :id AND part >= 0
    """)
    abstract suspend fun attachmentPartCount(chatId: String, id: String): Int

    @Query("""
        SELECT turns.* FROM turns
        JOIN chats ON chats.id = turns.chatId
        JOIN entries ON entries.id = chats.ownerId
        WHERE (entries.id = :entryId OR entries.parentId = :entryId)
        AND entries.kind = 'SKILL' AND chats.id = 'tutor:' || entries.id
        ORDER BY turns.sequence
    """)
    abstract suspend fun tutorTurnsForExport(entryId: String): List<TurnRow>

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

    @Query("""
        SELECT * FROM turns WHERE chatId = :chatId AND contentVersion = :version
        AND speaker = 'ASSISTANT' AND kind IN (:kinds)
        ORDER BY sequence DESC LIMIT :limit
    """)
    abstract suspend fun recentTeaching(chatId: String, version: Long, kinds: List<String>, limit: Int): List<TurnRow>

    @Query("""
        SELECT COUNT(*) FROM (
            SELECT sequence FROM turns WHERE chatId = :chatId AND contentVersion = :version
            AND speaker = 'ASSISTANT' AND kind IN (:kinds) AND sequence > COALESCE((
                SELECT MAX(sequence) FROM turns WHERE chatId = :chatId AND contentVersion = :version AND kind = 'RESTART'
            ), 0)
            ORDER BY sequence DESC LIMIT :limit
        )
    """)
    abstract suspend fun teachingReplies(chatId: String, version: Long, kinds: List<String>, limit: Int): Int

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

    @Update
    abstract suspend fun updateTurn(turn: TurnRow)

    @Query("DELETE FROM turns WHERE id = :id AND chatId = :chatId")
    abstract suspend fun deleteTurn(chatId: String, id: String)

    @Query("DELETE FROM turns WHERE chatId = :chatId")
    abstract suspend fun clearTurns(chatId: String)
}

@Database(
    entities = [EntryRow::class, EdgeRow::class, ChatRow::class, TurnRow::class, BootstrapRow::class,
        ExamAttemptRow::class, TreeChallengeRow::class, PersonalProfileRow::class, CollectedCertificateRow::class,
        AiUsageRow::class, ChatAttachmentPart::class],
    version = 11,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8), AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10), AutoMigration(from = 10, to = 11),
    ],
)
internal abstract class LifeSkillDatabase : RoomDatabase() {
    abstract fun skills(): SkillDao
    abstract fun assessments(): TreeAssessmentDao
    abstract fun personal(): PersonalDao
    abstract fun usage(): AiUsageDao
}
