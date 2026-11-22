package io.github.mehdieskandari.airplaytv.service

import android.content.Context
import android.util.Log
import io.github.mehdieskandari.airplaytv.AppLanguage
import io.github.mehdieskandari.airplaytv.R
import io.github.mehdieskandari.airplaytv.Prefs
import io.github.mehdieskandari.airplaytv.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppRelease(val version: String, val notes: String, val pageUrl: String)

// Direct builds check GitHub; store builds delegate updates to their own store client.
class UpdateManager(private val context: Context, private val scope: CoroutineScope) {

    private val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

    private val _available = MutableStateFlow<AppRelease?>(null)
    val available: StateFlow<AppRelease?> = _available.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    private var periodic: Job? = null

    val currentVersion: String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"

    fun startPeriodicChecks() {
        if (StoreLinks.isStoreBuild || periodic?.isActive == true) return
        periodic = scope.launch(Dispatchers.IO) {
            delay(20_000) // let the server start first
            while (isActive) {
                if (prefs[Prefs.AUTO_UPDATE_CHECK]) check(manual = false)
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    fun checkNow() {
        if (StoreLinks.isStoreBuild) StoreLinks.open(context, StoreLinks.Action.UPDATE)
        else scope.launch(Dispatchers.IO) { check(manual = true) }
    }

    private fun text(id: Int, vararg args: Any) = AppLanguage.context(context).getString(id, *args)

    private fun check(manual: Boolean) {
        _status.value = text(R.string.update_checking)
        val rel = runCatching { fetchLatest() }.onFailure { Log.w(TAG, "update check failed", it) }.getOrNull()
        if (rel == null) {
            _status.value = text(R.string.update_unreachable)
            return
        }
        if (isNewer(rel.version, currentVersion)) {
            // a version the user postponed only reappears on manual checks
            if (manual || prefs.getString(KEY_SKIPPED, "") != rel.version) _available.value = rel
            _status.value = text(R.string.update_available, rel.version)
        } else {
            _available.value = null
            _status.value = text(R.string.update_latest, currentVersion)
        }
    }

    fun postpone() {
        _available.value?.let { prefs.edit().putString(KEY_SKIPPED, it.version).apply() }
        _available.value = null
    }

    fun openRelease() {
        if (StoreLinks.isStoreBuild) StoreLinks.open(context, StoreLinks.Action.UPDATE)
        else _available.value?.let { StoreLinks.openWeb(context, it.pageUrl) }
    }

    private fun fetchLatest(): AppRelease? {
        val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "AirPlayTV/$currentVersion")
        }
        try {
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            return AppRelease(
                version = json.optString("tag_name").removePrefix("v"),
                notes = json.optString("body"),
                pageUrl = json.optString("html_url")
            )
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "UpdateManager"
        private const val LATEST_URL = "https://api.github.com/repos/MehdiEskandari/airplay-tv/releases/latest"
        private const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
        private const val KEY_SKIPPED = "update_skipped_version"

        // "1.10.0" > "1.9.2"
        fun isNewer(remote: String, local: String): Boolean {
            val r = remote.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val l = local.split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(r.size, l.size)) {
                val a = r.getOrElse(i) { 0 }; val b = l.getOrElse(i) { 0 }
                if (a != b) return a > b
            }
            return false
        }
    }
}
