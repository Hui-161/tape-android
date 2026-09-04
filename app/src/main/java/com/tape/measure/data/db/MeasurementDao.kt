package com.tape.measure.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {

    @Query("SELECT * FROM measurements ORDER BY createdAt DESC")
    fun getAll(): Flow<List<MeasurementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(m: MeasurementEntity)

    @Delete
    suspend fun delete(m: MeasurementEntity)

    @Update
    suspend fun update(m: MeasurementEntity)
}
