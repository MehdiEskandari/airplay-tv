package io.github.mehdieskandari.airplaytv.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
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
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppRelease(val version: String, val apkUrl: String, val notes: String, val pageUrl: String)

// finds new versions on GitHub Releases and installs them (the app isn't on Google Play)
class UpdateManager(private val context: Context, private val scope: CoroutineScope) {

    private val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

    private val _available = MutableStateFlow<AppRelease?>(null)
    val available: StateFlow<AppRelease?> = _available.asStateFlow()

    // 0..1 while downloading, null otherwise
    private val _progress = MutableStateFlow<Float?>(null)
    val progress: StateFlow<Float?> = _progress.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    private var periodic: Job? = null

    val currentVersion: String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"

    fun startPeriodicChecks() {
        if (periodic?.isActive == true) return
        periodic = scope.launch(Dispatchers.IO) {
            delay(20_000) // let the server start first
            while (isActive) {
                if (prefs[Prefs.AUTO_UPDATE_CHECK]) check(manual = false)
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    fun checkNow() = scope.launch(Dispatchers.IO) { check(manual = true) }

    private fun check(manual: Boolean) {
        _status.value = "Checking… · در حال بررسی…"
        val rel = runCatching { fetchLatest() }.onFailure { Log.w(TAG, "update check failed", it) }.getOrNull()
        if (rel == null) {
            _status.value = "Couldn't reach GitHub · اتصال به GitHub ممکن نشد"
            return
        }
        if (isNewer(rel.version, currentVersion)) {
            // a version the user postponed only reappears on manual checks
            if (manual || prefs.getString(KEY_SKIPPED, "") != rel.version) _available.value = rel
            _status.value = "New version ${rel.version} · نسخه جدید ${rel.version}"
        } else {
            _available.value = null
            _status.value = "Up to date ($currentVersion) · به‌روز است"
        }
    }

    fun postpone() {
        _available.value?.let { prefs.edit().putString(KEY_SKIPPED, it.version).apply() }
        _available.value = null
    }

    fun downloadAndInstall() {
        val rel = _available.value ?: return
        if (_progress.value != null) return
        scope.launch(Dispatchers.IO) {
            try {
                _progress.value = 0f
                val file = download(rel.apkUrl)
                _status.value = "Installing… · در حال نصب…"
                install(file)
            } catch (e: Exception) {
                Log.w(TAG, "update failed", e)
                _status.value = "Update failed: ${e.message} · به‌روزرسانی ناموفق بود"
            } finally {
                _progress.value = null
            }
        }
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
            val assets = json.optJSONArray("assets") ?: return null
            var apk: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apk = a.optString("browser_download_url"); break
                }
            }
            return AppRelease(
                version = json.optString("tag_name").removePrefix("v"),
                apkUrl = apk ?: return null,
                notes = json.optString("body"),
                pageUrl = json.optString("html_url")
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun download(url: String): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "update.apk")
        var conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15_000; conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", "AirPlayTV/$currentVersion")
        // github redirects to another host; follow manually if needed
        var hops = 0
        while (conn.responseCode in 300..399 && hops++ < 5) {
            val next = conn.getHeaderField("Location")
            conn.disconnect()
            conn = URL(next).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000; conn.readTimeout = 30_000
        }
        if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            out.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n)
                    done += n
                    if (total > 0) _progress.value = done.toFloat() / total
                }
            }
        }
        conn.disconnect()
        return out
    }

    private fun install(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(context.packageName) }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("update.apk", 0, file.length()).use { o ->
                file.inputStream().use { it.copyTo(o) }
                session.fsync(o)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(
                context, id, Intent(context, UpdateInstallReceiver::class.java), flags
            )
            session.commit(pi.intentSender)
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

// android asks the user to confirm the install (and, the first time, to allow installs from this app)
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Log.i("UpdateManager", "update installed")
            else -> Log.w("UpdateManager", "install status: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
        }
    }
}
