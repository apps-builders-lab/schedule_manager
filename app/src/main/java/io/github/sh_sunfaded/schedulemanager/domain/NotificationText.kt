package io.github.sh_sunfaded.schedulemanager.domain

/** 通知の文言。素朴な1行(装飾・絵文字なし)。典型例は test の golden で正本化する。 */
object NotificationText {
    private val hhmm = java.time.format.DateTimeFormatter.ofPattern("HH:mm")

    fun due(title: String, atMillis: Long): String =
        "${oneLine(title)}の期限です(${time(atMillis)})。"

    fun reminder(title: String, atMillis: Long): String =
        "${oneLine(title)}のリマインダです(${time(atMillis)})。"

    fun of(kind: FireKind, title: String, atMillis: Long): String = when (kind) {
        FireKind.DUE -> due(title, atMillis)
        FireKind.REMINDER -> reminder(title, atMillis)
    }

    private fun time(atMillis: Long): String = JstTime.toLocal(atMillis).format(hhmm)

    /** 改行・連続空白を1つの空白にして1行に保つ。 */
    private fun oneLine(title: String): String = title.trim().replace(Regex("\\s+"), " ")
}
