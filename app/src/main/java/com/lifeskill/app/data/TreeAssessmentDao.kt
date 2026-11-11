package com.lifeskill.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "exam_attempts",
    foreignKeys = [ForeignKey(
        entity = EntryRow::class, parentColumns = ["id"], childColumns = ["treeId"], onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("treeId", "sequence"), Index("id", unique = true)],
)
internal data class ExamAttemptRow(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val id: String,
    val treeId: String,
    val score: Int?,
    val content: String,
)

@Entity(
    tableName = "tree_challenges",
    primaryKeys = ["treeId", "kind"],
    foreignKeys = [ForeignKey(
        entity = EntryRow::class, parentColumns = ["id"], childColumns = ["treeId"], onDelete = ForeignKey.CASCADE,
    )],
)
internal data class TreeChallengeRow(val treeId: String, val kind: String, val passed: Boolean, val content: String)

internal data class BestExamScore(val treeId: String, val score: Int)
internal data class CompletedChallenge(val treeId: String, val kind: String)

@Dao
internal interface TreeAssessmentDao {
    @Query("SELECT treeId, MAX(score) AS score FROM exam_attempts WHERE score IS NOT NULL GROUP BY treeId")
    fun watchBestScores(): Flow<List<BestExamScore>>

    @Query("SELECT treeId, kind FROM tree_challenges WHERE passed = 1")
    fun watchCompletedChallenges(): Flow<List<CompletedChallenge>>

    @Query("SELECT MAX(score) FROM exam_attempts WHERE treeId = :treeId")
    suspend fun bestScore(treeId: String): Int?

    @Query("SELECT * FROM exam_attempts WHERE treeId = :treeId ORDER BY sequence DESC")
    fun watchAttempts(treeId: String): Flow<List<ExamAttemptRow>>

    @Query("SELECT * FROM exam_attempts WHERE treeId = :treeId ORDER BY sequence DESC LIMIT 1")
    suspend fun latestAttempt(treeId: String): ExamAttemptRow?

    @Query("SELECT * FROM exam_attempts WHERE treeId = :treeId AND id = :attemptId")
    suspend fun attempt(treeId: String, attemptId: String): ExamAttemptRow?

    @Insert
    suspend fun insert(row: ExamAttemptRow)

    @Update
    suspend fun update(row: ExamAttemptRow)

    @Query("SELECT * FROM tree_challenges WHERE treeId = :treeId AND kind = :kind")
    fun watchChallenge(treeId: String, kind: String): Flow<TreeChallengeRow?>

    @Query("SELECT * FROM tree_challenges WHERE treeId = :treeId AND kind = :kind")
    suspend fun challenge(treeId: String, kind: String): TreeChallengeRow?

    @Upsert
    suspend fun save(row: TreeChallengeRow)
}
