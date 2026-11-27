package io.github.sh_sunfaded.schedulemanager.data

/** SPEC §3: 状態は active / done の2つだけを持つ。DB には wire の文字列で保存する。 */
enum class TaskStatus(val wire: String) {
    ACTIVE("active"),
    DONE("done");

    companion object {
        fun fromWire(value: String): TaskStatus =
            entries.firstOrNull { it.wire == value }
                ?: throw IllegalArgumentException("unknown status: $value")
    }
}
