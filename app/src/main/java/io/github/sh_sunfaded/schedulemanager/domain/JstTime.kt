package io.github.sh_sunfaded.schedulemanager.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** タイムゾーンは Asia/Tokyo 固定(CLAUDE.md / SPEC §6)。端末の設定には従わない。 */
object JstTime {
    val zone: ZoneId = ZoneId.of("Asia/Tokyo")

    private val displayFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** 「分まで」(SPEC §3)。秒以下は切り捨てる。 */
    fun truncateToMinute(epochMillis: Long): Long =
        Instant.ofEpochMilli(epochMillis).truncatedTo(ChronoUnit.MINUTES).toEpochMilli()

    fun toLocal(epochMillis: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone)

    /** 入力は分までの LocalDateTime(JST)。 */
    fun toEpochMillis(local: LocalDateTime): Long =
        truncateToMinute(local.atZone(zone).toInstant().toEpochMilli())

    fun format(epochMillis: Long): String = toLocal(epochMillis).format(displayFormat)
}
