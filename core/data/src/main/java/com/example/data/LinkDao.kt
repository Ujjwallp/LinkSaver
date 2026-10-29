package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkDao {
    @Query("SELECT * FROM links ORDER BY timestamp DESC")
    fun getAllLinks(): Flow<List<LinkEntity>>

    @Query("SELECT * FROM links ORDER BY timestamp DESC")
    suspend fun getAllLinksSnapshot(): List<LinkEntity>

    @Query("SELECT * FROM links WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavoriteLinks(): Flow<List<LinkEntity>>

    @Query("SELECT * FROM links WHERE title LIKE :query OR url LIKE :query OR description LIKE :query OR note LIKE :query OR category LIKE :query ORDER BY timestamp DESC")
    fun searchLinks(query: String): Flow<List<LinkEntity>>

    @Query("SELECT * FROM links WHERE url = :url LIMIT 1")
    suspend fun getLinkByUrl(url: String): LinkEntity?

    @Query("SELECT * FROM links WHERE id = :id LIMIT 1")
    suspend fun getLinkById(id: Int): LinkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: LinkEntity): Long

    @Update
    suspend fun updateLink(link: LinkEntity)

    @Delete
    suspend fun deleteLink(link: LinkEntity)

    @Query("DELETE FROM links WHERE id = :id")
    suspend fun deleteLinkById(id: Int)

    @Query("DELETE FROM links WHERE userEmail = :email")
    suspend fun deleteLinksByUserEmail(email: String)

    @Query("DELETE FROM links")
    suspend fun deleteAllLinks()

    @Query("UPDATE links SET openCount = openCount + 1 WHERE id = :id")
    suspend fun incrementOpenCount(id: Int)

    @Query("UPDATE links SET openCount = :count WHERE id = :id")
    suspend fun setOpenCount(id: Int, count: Int)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinks(links: List<LinkEntity>)
}
