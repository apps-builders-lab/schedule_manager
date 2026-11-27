package io.github.sh_sunfaded.schedulemanager.data

import io.github.sh_sunfaded.schedulemanager.domain.JstTime
import kotlinx.coroutines.flow.Flow

/** task の追加・編集・完了・削除。時刻の正規化と状態遷移はここに集める。 */
class TaskRepository(
    private val dao: TaskDao,
    private val reminderDao: ReminderDao,
    private val clock: () -> Long = System::currentTimeMillis,
    /** 期限・リマインダ・状態が変わるたびに呼ぶ。発火予約を追随させる(SPEC §4)。 */
    private val onChanged: suspend () -> Unit = {},
) {
    fun observeAll(): Flow<List<TaskEntity>> = dao.observeAll()

    fun observeReminders(): Flow<List<ReminderEntity>> = reminderDao.observeAll()

    /** title は前後の空白を除いて必須。note は空なら null。due_at は分に切り捨てる。 */
    suspend fun add(title: String, note: String?, dueAt: Long?): Long {
        val now = clock()
        val id = dao.insert(
            TaskEntity(
                title = normalizeTitle(title),
                note = normalizeNote(note),
                dueAt = dueAt?.let(JstTime::truncateToMinute),
                createdAt = now,
                updatedAt = now,
            ),
        )
        onChanged()
        return id
    }

    suspend fun edit(id: Long, title: String, note: String?, dueAt: Long?) {
        val current = dao.getById(id) ?: return
        dao.update(
            current.copy(
                title = normalizeTitle(title),
                note = normalizeNote(note),
                dueAt = dueAt?.let(JstTime::truncateToMinute),
                updatedAt = clock(),
            ),
        )
        onChanged()
    }

    /** done=true で完了(done_at を記録)、false で active に戻す(done_at を消す)。 */
    suspend fun setDone(id: Long, done: Boolean) {
        val current = dao.getById(id) ?: return
        val now = clock()
        dao.update(
            current.copy(
                status = if (done) TaskStatus.DONE else TaskStatus.ACTIVE,
                doneAt = if (done) now else null,
                updatedAt = now,
            ),
        )
        onChanged()
    }

    /** reminder は ON DELETE CASCADE で一緒に消える(SPEC §5)。 */
    suspend fun delete(id: Long) {
        dao.deleteById(id)
        onChanged()
    }

    /**
     * リマインダを付ける。分に切り捨て、現在より後の時刻だけ受ける(過去は発火しないため)。
     * 付けられたら true、task が無い・過去なら false。
     */
    suspend fun addReminder(taskId: Long, fireAt: Long): Boolean {
        val at = JstTime.truncateToMinute(fireAt)
        if (dao.getById(taskId) == null || at <= clock()) return false
        reminderDao.insert(ReminderEntity(taskId = taskId, fireAt = at))
        onChanged()
        return true
    }

    suspend fun deleteReminder(id: Long) {
        reminderDao.deleteById(id)
        onChanged()
    }

    private fun normalizeTitle(title: String): String {
        val trimmed = title.trim()
        require(trimmed.isNotEmpty()) { "title is required" }
        return trimmed
    }

    private fun normalizeNote(note: String?): String? =
        note?.trim()?.takeIf { it.isNotEmpty() }
}
