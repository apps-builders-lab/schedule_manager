package io.github.sh_sunfaded.schedulemanager.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("DELETE FROM task WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM task WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    /** 並びは DB に持たせない。並び替えは domain の純関数 buildSections が唯一の正本。 */
    @Query("SELECT * FROM task ORDER BY id")
    suspend fun getAll(): List<TaskEntity>

    @Query("SELECT * FROM task ORDER BY id")
    fun observeAll(): Flow<List<TaskEntity>>
}

@Dao
interface ReminderDao {
    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Query("SELECT * FROM reminder WHERE task_id = :taskId ORDER BY fire_at, id")
    suspend fun getByTaskId(taskId: Long): List<ReminderEntity>

    @Query("SELECT * FROM reminder ORDER BY task_id, fire_at, id")
    suspend fun getAll(): List<ReminderEntity>

    @Query("SELECT * FROM reminder ORDER BY task_id, fire_at, id")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("DELETE FROM reminder WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 発火済みを fired_at で記録する(SPEC §5。別の log は持たない)。 */
    @Query("UPDATE reminder SET fired_at = :firedAt WHERE id = :id")
    suspend fun markFired(id: Long, firedAt: Long)
}
