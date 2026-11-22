package io.github.mehdieskandari.airplaytv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.mehdieskandari.airplaytv.service.AirPlayService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON" &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (!prefs[Prefs.BOOT_AUTO_START] || !context.hasLocalNetwork()) return

        val serviceIntent = Intent(context, AirPlayService::class.java)
            .setAction(AirPlayService.ACTION_START_SERVER)
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: IllegalStateException) {
            // Android may restrict this background launch even after an app update.
            // MainActivity can start the receiver when the user next opens the app.
            Log.w("BootReceiver", "Background start deferred until the app is opened", e)
        } catch (e: SecurityException) {
            Log.w("BootReceiver", "Background start deferred until permissions are available", e)
        }
    }
}
