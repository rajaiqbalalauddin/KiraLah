package com.buyless.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Checks and settings shortcuts for the two system permissions Buyless depends on. Kept in one place
 * so Setup and Settings show the exact same status.
 */
object SystemAccess {

    fun hasNotificationAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun notificationAccessIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun isIgnoringBatteryOptimisation(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Direct exemption prompt. Acceptable here because the core feature is a background listener. */
    @SuppressLint("BatteryLife")
    fun batteryExemptionIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * App info page. On Android 13+ a sideloaded APK must enable "Allow restricted settings" here
     * before Notification access can be switched on, so Setup links to it.
     */
    fun appInfoIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
