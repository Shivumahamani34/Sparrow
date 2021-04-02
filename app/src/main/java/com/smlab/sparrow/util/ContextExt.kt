package com.smlab.sparrow.util

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.preference.PreferenceManager
import androidx.core.content.ContextCompat
import com.smlab.sparrow.util.appContext

val Context.audioManager get() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

val Context.prefs: SharedPreferences get() = PreferenceManager.getDefaultSharedPreferences(this)

val Context.speakPrefs: SharedPreferences get() =
    appContext.getSharedPreferences("${appContext.packageName}.Speak", Context.MODE_PRIVATE)

fun Context.checkSelfPermissionCompat(permission: String) = ContextCompat.checkSelfPermission(this, permission)

fun Context.startForegroundServiceCompat(intent: Intent) = ContextCompat.startForegroundService(this, intent)

fun Context.isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED