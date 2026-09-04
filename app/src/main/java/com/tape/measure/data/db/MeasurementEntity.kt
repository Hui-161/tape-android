package com.tape.measure.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "measurements")
data class MeasurementEntity(
    @PrimaryKey val id: String,
    val distanceMeters: Float,
    val unit: String,
    val label: String?,
    val confidence: Float,
    val createdAt: Long,
)
