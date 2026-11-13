package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoRecordDao {
    @Query("SELECT * FROM photo_records ORDER BY timestamp DESC")
    fun getAllPhotos(): Flow<List<PhotoRecordEntity>>

    @Query("SELECT * FROM photo_records ORDER BY timestamp DESC")
    suspend fun getAllPhotosList(): List<PhotoRecordEntity>

    @Query("SELECT * FROM photo_records WHERE id = :id")
    suspend fun getPhotoById(id: Long): PhotoRecordEntity?

    @Query("SELECT * FROM photo_records WHERE chainageLabel LIKE '%' || :query || '%' OR activity LIKE '%' || :query || '%' OR subActivity LIKE '%' || :query || '%' OR fileName LIKE '%' || :query || '%' OR highwayId LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchPhotos(query: String): Flow<List<PhotoRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: PhotoRecordEntity): Long

    @Update
    suspend fun updatePhoto(photo: PhotoRecordEntity)

    @Delete
    suspend fun deletePhoto(photo: PhotoRecordEntity)

    @Query("DELETE FROM photo_records WHERE id = :id")
    suspend fun deletePhotoById(id: Long)
}

@Dao
interface RouteDao {
    @Query("SELECT * FROM routes ORDER BY importedAt DESC")
    fun getAllRoutes(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE isActive = 1 LIMIT 1")
    fun getActiveRoute(): Flow<RouteEntity?>

    @Query("SELECT * FROM routes WHERE id = :id LIMIT 1")
    suspend fun getRouteById(id: String): RouteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: RouteEntity)

    @Query("UPDATE routes SET isActive = 0")
    suspend fun deactivateAllRoutes()

    @Query("UPDATE routes SET isActive = 1 WHERE id = :id")
    suspend fun activateRoute(id: String)

    @Query("DELETE FROM routes WHERE id = :id")
    suspend fun deleteRouteById(id: String)
}
