package com.barontech.paperplane.sync

import android.content.Context
import android.provider.Settings

fun supabaseDeviceId(context: Context): String {
    return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        ?.takeIf { it.isNotBlank() }
        ?: "unknown"
}
