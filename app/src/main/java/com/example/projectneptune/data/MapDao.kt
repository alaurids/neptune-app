package com.example.projectneptune.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MapDao {
    @Query("SELECT * FROM layer_20_features")
    suspend fun getAllLayer20Features(): List<Layer20Feature>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllLayer20Features(features: List<Layer20Feature>)

    @Query("DELETE FROM layer_20_features")
    suspend fun clearLayer20Features()

    @Query("DELETE FROM layer_20_features WHERE objectId NOT IN (:ids)")
    suspend fun deleteFeaturesNotInList(ids: List<Int>)

    @Query("SELECT * FROM stations")
    suspend fun getAllStations(): List<Station>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllStations(stations: List<Station>)

    @Query("DELETE FROM stations")
    suspend fun clearStations()

    @Query("SELECT * FROM tide_data WHERE stationId = :stationId")
    suspend fun getTideDataForStation(stationId: String): List<TideData>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTideData(tideData: List<TideData>)

    @Query("DELETE FROM tide_data WHERE stationId = :stationId")
    suspend fun deleteTideDataForStation(stationId: String)

    @Query("DELETE FROM tide_data")
    suspend fun clearTideData()

    @Query("SELECT * FROM static_boundaries")
    suspend fun getAllStaticBoundaries(): List<StaticBoundary>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStaticBoundary(boundary: StaticBoundary)

    @Query("SELECT * FROM catch_limits WHERE zoneId = :zoneId")
    suspend fun getCatchLimit(zoneId: String): CatchLimit?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCatchLimit(catchLimit: CatchLimit)

    @Query("SELECT COUNT(*) FROM catch_limits")
    suspend fun getCatchLimitCount(): Int

    @Query("SELECT DISTINCT closestStationId FROM layer_20_features WHERE closestStationId IS NOT NULL")
    suspend fun getUniqueClosestStationIds(): List<String>

    @Query("SELECT value FROM app_metadata WHERE `key` = :key")
    suspend fun getMetadata(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: AppMetadata)
}
