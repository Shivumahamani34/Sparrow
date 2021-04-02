package com.smlab.sparrow.ui.speak

import android.preference.PreferenceManager
import com.smlab.sparrow.R
import com.smlab.sparrow.util.ContextHelper
import com.smlab.sparrow.util.appContext

object TalkiePreferenceHelper {
    val prefs = PreferenceManager.getDefaultSharedPreferences(ContextHelper.applicationContext)


    fun setRoomId(
            roomId: String) {
        prefs.edit().putString(appContext.getString(R.string.room_id_random), roomId).apply()
    }

    fun getRoomId(): String {
        prefs.getString(appContext.getString(R.string.room_id_random), "")?.let {
            return it
        }
        return ""
    }

    fun setTalkieEnabled(enabled: Boolean) {
        prefs.edit()
                .putBoolean(ContextHelper.applicationContext.getString(R.string.talkie_enable), enabled)
                .apply()
//        if (enabled) {
//            // Voice dial command disabled
//            BaristaAPI.Vendor().setVoiceAssistantDisabled(true)
//        } else {
//            // Voice dial command enabled
//            BaristaAPI.Vendor().setVoiceAssistantDisabled(false)
//        }
    }

    fun getTalkieEnabled(): Boolean {
        return prefs.getBoolean(ContextHelper.applicationContext.getString(R.string.talkie_enable), false)
    }
}