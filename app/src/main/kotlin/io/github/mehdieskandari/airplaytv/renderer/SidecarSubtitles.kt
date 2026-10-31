package io.github.mehdieskandari.airplaytv.renderer

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MimeTypes
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// AirPlay only sends the movie URL; apps that load a separate subtitle file never send it.
// Many sites keep it next to the movie with the same name (movie.mp4 -> movie.vtt / movie.srt),
// so look there. Only the movie's own server is contacted.
object SidecarSubtitles {

    data class Found(val uri: Uri, val mimeType: String)

    private const val TAG = "SidecarSubtitles"
    private const val TIMEOUT_MS = 3000
    private const val TOTAL_WAIT_MS = 4000L
    private const val MAX_BYTES = 5 * 1024 * 1024

    // checked in this order; the first one that exists wins
    private val SUFFIXES = listOf(
        ".fa.srt", ".fa.vtt", ".per.srt", ".persian.srt", ".Persian.srt", ".fa_IR.srt",
        ".vtt", ".srt", ".en.srt", ".en.vtt"
    )

    fun find(context: Context, location: String): Found? {
        val candidates = candidateUrls(location)
        if (candidates.isEmpty()) return null
        val pool = Executors.newFixedThreadPool(4)
        try {
            val futures = candidates.map { url -> pool.submit(Callable { fetch(url) }) }
            val deadline = System.currentTimeMillis() + TOTAL_WAIT_MS
            for ((i, f) in futures.withIndex()) {
                val left = deadline - System.currentTimeMillis()
                val text = runCatching { f.get(left.coerceAtLeast(1), TimeUnit.MILLISECONDS) }.getOrNull() ?: continue
                val mime = if (text.trimStart().startsWith("WEBVTT")) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
                val ext = if (mime == MimeTypes.TEXT_VTT) "vtt" else "srt"
                val dir = File(context.cacheDir, "subtitles").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, "sub_${System.currentTimeMillis()}.$ext")
                file.writeText(text, Charsets.UTF_8)
                Log.i(TAG, "Using subtitle #$i (${text.length} chars, $ext)")
                return Found(Uri.fromFile(file), mime)
            }
            return null
        } finally {
            pool.shutdownNow()
        }
    }

    private fun candidateUrls(location: String): List<String> {
        val uri = runCatching { URI(location) }.getOrNull() ?: return emptyList()
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return emptyList()
        val host = uri.host?.lowercase() ?: return emptyList()
        // local proxy = hls relay, not a file on a web server
        if (host == "localhost" || host == "127.0.0.1" || host == "::1") return emptyList()
        val path = uri.rawPath ?: return emptyList()
        val name = path.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        if (dot <= 0) return emptyList()
        val ext = name.substring(dot + 1).lowercase()
        if (ext in setOf("m3u8", "m3u", "mpd", "srt", "vtt")) return emptyList()
        val basePath = path.substring(0, path.length - (name.length - dot))
        val prefix = "$scheme://${uri.rawAuthority}$basePath"
        val query = uri.rawQuery
        return buildList {
            for (s in SUFFIXES) {
                if (query != null) add("$prefix$s?$query")
                add("$prefix$s")
            }
        }
    }

    private fun fetch(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "AppleCoreMedia/1.0.0")
            }
            if (conn.responseCode != 200) return null
            val out = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) return null
                }
            }
            val text = decode(out.toByteArray())
            // must actually be a subtitle file, not an html error page
            if (!text.contains("-->") || text.contains("<html", ignoreCase = true)) null else text
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    // Persian .srt files are often Windows-1256, not UTF-8
    private fun decode(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte())
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        val data = ByteBuffer.wrap(bytes, start, bytes.size - start)
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(data).toString()
        } catch (e: CharacterCodingException) {
            String(bytes, start, bytes.size - start, Charset.forName("windows-1256"))
        }
    }
}
