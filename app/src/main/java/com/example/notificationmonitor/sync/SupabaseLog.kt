package com.example.notificationmonitor.sync

import android.util.Log

internal object SupabaseLog {
    private const val TAG = "Supabase"

    fun configIssue(step: String, detail: String) {
        Log.w(TAG, "Config setup [$step]: $detail")
    }

    fun signInBlocked(detail: String) {
        Log.w(TAG, "Sign-in blocked: $detail")
    }

    fun authHttpFailed(statusCode: Int, grantType: String) {
        Log.w(TAG, "Auth failed grant=$grantType status=$statusCode")
    }

    fun sessionRefreshBlocked(detail: String) {
        Log.w(TAG, "Session refresh blocked: $detail")
    }

    fun sessionRefreshHttpFailed(statusCode: Int) {
        Log.w(TAG, "Session refresh failed status=$statusCode")
    }

    fun validationBlocked(detail: String) {
        Log.w(TAG, "Push blocked: $detail")
    }

    fun projectCredentialsChanged() {
        Log.i(TAG, "Project URL or publishable key changed; clearing saved sign-in session")
    }

    fun unexpectedAuthResponse(detail: String) {
        Log.e(TAG, "Unexpected auth response: $detail")
    }
}
