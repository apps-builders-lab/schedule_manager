package io.github.sh_sunfaded.schedulemanager.domain

import io.github.sh_sunfaded.schedulemanager.data.ReminderEntity
import io.github.sh_sunfaded.schedulemanager.data.TaskEntity
import io.github.sh_sunfaded.schedulemanager.data.TaskStatus

enum class FireKind { DUE, REMINDER }

/** 発火1件。reminderId は REMINDER のときだけ入る。at は分に揃った epoch ミリ秒。 */
data class Fire(
    val taskId: Long,
    val reminderId: Long?,
    val kind: FireKind,
    val at: Long,
)

/**
 * 発火の候補(SPEC §4)。active の task の due_at と、その task の未発火の reminder。
 * done の task、存在しない task の reminder、発火済み(fired_at あり)の reminder は含めない。
 * 候補は時刻の前後を問わない(過去の除外は呼び出し側の関数が行う)。
 */
fun fireCandidates(tasks: List<TaskEntity>, reminders: List<ReminderEntity>): List<Fire> {
    val activeIds = tasks.filter { it.status == TaskStatus.ACTIVE }.map { it.id }.toSet()
    val dues = tasks
        .filter { it.id in activeIds && it.dueAt != null }
        .map { Fire(it.id, null, FireKind.DUE, it.dueAt!!) }
    val rems = reminders
        .filter { it.taskId in activeIds && it.firedAt == null }
        .map { Fire(it.taskId, it.id, FireKind.REMINDER, it.fireAt) }
    return dues + rems
}

/**
 * 次に予約すべき発火時刻。now より後(厳密に大きい)の候補のうち最も近いもの。
 * 期限・発火時刻を過ぎたもの(now 以前)は発火しない(復帰時にも遡って出さない)。無ければ null。
 */
fun nextFireTime(tasks: List<TaskEntity>, reminders: List<ReminderEntity>, now: Long): Long? =
    fireCandidates(tasks, reminders).map { it.at }.filter { it > now }.minOrNull()

/** at ちょうどに発火すべき全件。期限を先、リマインダを後(id 昇順)で返す。 */
fun firesAt(tasks: List<TaskEntity>, reminders: List<ReminderEntity>, at: Long): List<Fire> =
    fireCandidates(tasks, reminders)
        .filter { it.at == at }
        .sortedWith(compareBy<Fire> { it.kind }.thenBy { it.reminderId ?: 0L }.thenBy { it.taskId })
