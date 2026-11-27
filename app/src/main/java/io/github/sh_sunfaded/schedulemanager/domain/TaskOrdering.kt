package io.github.sh_sunfaded.schedulemanager.domain

import io.github.sh_sunfaded.schedulemanager.data.TaskEntity
import io.github.sh_sunfaded.schedulemanager.data.TaskStatus

/** 一覧の2区画。active は上に、done は下に折り畳んで出す(SPEC §3)。 */
data class TaskSections(
    val active: List<TaskEntity>,
    val done: List<TaskEntity>,
)

/**
 * 未完了の並び: 期限の近い順(due_at 昇順)。期限なしは末尾。
 * 同順位は created_at 昇順、さらに id 昇順で決定的にする。
 */
val activeOrder: Comparator<TaskEntity> =
    compareBy<TaskEntity, Long?>(nullsLast()) { it.dueAt }
        .thenBy { it.createdAt }
        .thenBy { it.id }

/** 完了の並び: 完了が新しい順(done_at 降順)。done_at 無しは末尾、同順位は id 降順。 */
val doneOrder: Comparator<TaskEntity> =
    compareByDescending<TaskEntity, Long?>(nullsFirst()) { it.doneAt }
        .thenByDescending { it.id }

/** 副作用なしの純関数。入力の並びには依存しない。 */
fun buildSections(tasks: List<TaskEntity>): TaskSections {
    val (done, active) = tasks.partition { it.status == TaskStatus.DONE }
    return TaskSections(
        active = active.sortedWith(activeOrder),
        done = done.sortedWith(doneOrder),
    )
}
