package com.shivam1410.intervaltimer

import android.media.AudioDeviceInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRouteTest {
    @Test fun earphonesOfAnyKindCount() {
        assertTrue(hasEarphones(setOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)))
        assertTrue(hasEarphones(setOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)))
        assertTrue(hasEarphones(setOf(AudioDeviceInfo.TYPE_BLE_HEADSET)))
        assertTrue(hasEarphones(setOf(AudioDeviceInfo.TYPE_USB_HEADSET)))
    }

    @Test fun phoneSpeakerAndCarAreNotEarphones() {
        assertFalse(hasEarphones(setOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)))
        assertFalse(hasEarphones(setOf(AudioDeviceInfo.TYPE_BLE_SPEAKER)))
        assertFalse(hasEarphones(emptySet()))
    }
}
