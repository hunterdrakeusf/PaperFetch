package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.PaperRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface PaperDao {

    @Query("SELECT * FROM paper_records ORDER BY downloadedAt DESC, year DESC, doi ASC")
    fun getAllPapersFlow(): Flow<List<PaperRecord>>

    @Query("SELECT * FROM paper_records WHERE downloadStatus = :status ORDER BY downloadedAt DESC, year DESC")
    fun getPapersByStatusFlow(status: String): Flow<List<PaperRecord>>

    @Query("SELECT * FROM paper_records WHERE doi = :doi LIMIT 1")
    suspend fun getPaperByDoi(doi: String): PaperRecord?

    @Query("SELECT * FROM paper_records WHERE downloadStatus = 'PENDING' OR downloadStatus = 'RESOLVING' OR downloadStatus = 'DOWNLOADING'")
    suspend fun getActiveQueue(): List<PaperRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(paper: PaperRecord)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(papers: List<PaperRecord>)

    @Update
    suspend fun update(paper: PaperRecord)

    @Query("DELETE FROM paper_records WHERE doi = :doi")
    suspend fun deleteByDoi(doi: String)

    @Query("DELETE FROM paper_records")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM paper_records")
    fun getTotalCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM paper_records WHERE downloadStatus = 'DOWNLOADED'")
    fun getDownloadedCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM paper_records WHERE downloadStatus = 'PAYWALLED'")
    fun getPaywalledCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM paper_records WHERE downloadStatus = 'FAILED'")
    fun getFailedCountFlow(): Flow<Int>

    @Query("SELECT SUM(fileSizeBytes) FROM paper_records WHERE downloadStatus = 'DOWNLOADED'")
    fun getTotalDownloadedBytesFlow(): Flow<Long?>
}
