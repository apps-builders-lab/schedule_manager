package io.github.sh_sunfaded.schedulemanager.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SPEC §5 の task。時刻列はすべて epoch ミリ秒(UTC)で持ち、
 * 表示と入力の解釈は Asia/Tokyo 固定(JstTime)で行う。
 */
@Entity(tableName = "task")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String? = null,
    @ColumnInfo(name = "due_at") val dueAt: Long? = null,
    val status: TaskStatus = TaskStatus.ACTIVE,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "done_at") val doneAt: Long? = null,
)

/**
 * SPEC §5 の reminder。M1 では表だけ作り、UI からは触らない(M2 で使う)。
 * task を削除したら reminder も消える(ON DELETE CASCADE)。
 */
@Entity(
    tableName = "reminder",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("task_id")],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "task_id") val taskId: Long,
    @ColumnInfo(name = "fire_at") val fireAt: Long,
    @ColumnInfo(name = "fired_at") val firedAt: Long? = null,
)
