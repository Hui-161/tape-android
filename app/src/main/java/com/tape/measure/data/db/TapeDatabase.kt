package com.tape.measure.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MeasurementEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class TapeDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao
}
