package com.lifeskill.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "personal_profile")
internal data class PersonalProfileRow(@PrimaryKey val id: Int = 1, val content: String)

// 证书是独立的签发快照，技能树删除时仍应可以读取。
@Entity(tableName = "collected_certificates", indices = [Index("treeId", unique = true)])
internal data class CollectedCertificateRow(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val treeId: String,
    val content: String,
)

@Dao
internal interface PersonalDao {
    @Query("SELECT * FROM personal_profile WHERE id = 1")
    fun watchProfile(): Flow<PersonalProfileRow?>

    @Query("SELECT * FROM personal_profile WHERE id = 1")
    suspend fun profile(): PersonalProfileRow?

    @Upsert
    suspend fun saveProfile(row: PersonalProfileRow)

    @Query("SELECT * FROM collected_certificates ORDER BY sequence DESC")
    fun watchCertificates(): Flow<List<CollectedCertificateRow>>

    @Query("SELECT * FROM collected_certificates")
    suspend fun certificates(): List<CollectedCertificateRow>

    @Query("SELECT * FROM collected_certificates WHERE treeId = :treeId")
    fun watchCertificate(treeId: String): Flow<CollectedCertificateRow?>

    @Query("SELECT * FROM collected_certificates WHERE treeId = :treeId")
    suspend fun certificate(treeId: String): CollectedCertificateRow?

    @Insert
    suspend fun insertCertificate(row: CollectedCertificateRow)

    @Update
    suspend fun updateCertificate(row: CollectedCertificateRow)
}
