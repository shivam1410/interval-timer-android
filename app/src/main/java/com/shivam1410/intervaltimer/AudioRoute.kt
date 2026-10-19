package com.shivam1410.intervaltimer

import android.media.AudioDeviceInfo

private val EARPHONES = setOf(
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_HEARING_AID,
)

/** Output device types (AudioManager.getDevices) that are on your ears; a Bluetooth/LE speaker isn't. */
fun hasEarphones(outputTypes: Set<Int>): Boolean = outputTypes.any { it in EARPHONES }
