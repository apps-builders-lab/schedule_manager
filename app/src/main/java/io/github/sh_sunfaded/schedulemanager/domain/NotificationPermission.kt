package io.github.sh_sunfaded.schedulemanager.domain

/** POST_NOTIFICATIONS は API 33 以上でだけ実行時に要求する。拒否されても機能は落とさない。 */
fun needsNotificationPermissionRequest(sdkInt: Int, granted: Boolean): Boolean =
    sdkInt >= 33 && !granted
