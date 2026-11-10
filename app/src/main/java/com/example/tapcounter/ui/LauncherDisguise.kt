package com.example.tapcounter.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.example.tapcounter.counter.LauncherAliases

/**
 * Changes the app icon and name shown by the phone's launcher by enabling exactly one activity-alias.
 * Android only changes the LAUNCHER entry this way: Settings > Apps keeps showing the app's own
 * name ("Calculator"). Some launchers take a few seconds, or a restart, to show the new icon.
 */
object LauncherDisguise {
    private fun component(context: Context, index: Int) =
        ComponentName(context.packageName, "com.example.tapcounter.${LauncherAliases.componentNames[index]}")

    fun current(context: Context): Int {
        val pm = context.packageManager
        return LauncherAliases.currentIndex(LauncherAliases.componentNames.indices.map { pm.getComponentEnabledSetting(component(context, it)) })
    }

    fun apply(context: Context, index: Int) {
        if (index !in LauncherAliases.componentNames.indices) return
        val pm = context.packageManager
        // Enable the new one FIRST so the app never has zero launcher entries.
        pm.setComponentEnabledSetting(component(context, index), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        for (i in LauncherAliases.componentNames.indices) {
            if (i != index) {
                pm.setComponentEnabledSetting(component(context, i), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
