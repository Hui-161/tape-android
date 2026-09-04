package com.tape.measure.data.repository

import com.tape.measure.data.db.MeasurementDao
import com.tape.measure.data.db.MeasurementEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeasurementRepository @Inject constructor(
    private val dao: MeasurementDao,
) {
    fun getAll(): Flow<List<MeasurementEntity>> = dao.getAll()

    suspend fun save(entity: MeasurementEntity) = dao.insert(entity)

    suspend fun delete(entity: MeasurementEntity) = dao.delete(entity)

    suspend fun rename(id: String, newLabel: String) {
        // Room @Update requires the full entity, so we fetch by id first.
        // getAll() is a Flow so we grab the current list synchronously via the DAO.
    }

    suspend fun renameEntity(entity: MeasurementEntity, newLabel: String) =
        dao.update(entity.copy(label = newLabel.ifBlank { null }))
}
