package io.github.sh_sunfaded.schedulemanager.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionTest {
    @Test
    fun requestsOnApi33OrLaterWhenNotGranted() {
        assertTrue(needsNotificationPermissionRequest(33, false))
        assertTrue(needsNotificationPermissionRequest(35, false))
    }

    @Test
    fun doesNotRequestWhenAlreadyGranted() {
        assertFalse(needsNotificationPermissionRequest(34, true))
    }

    @Test
    fun doesNotRequestBeforeApi33() {
        assertFalse(needsNotificationPermissionRequest(32, false))
        assertFalse(needsNotificationPermissionRequest(31, false))
    }
}
