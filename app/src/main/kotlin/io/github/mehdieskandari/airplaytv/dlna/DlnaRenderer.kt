package io.github.mehdieskandari.airplaytv.dlna

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

enum class TransportState { NO_MEDIA_PRESENT, STOPPED, PLAYING, PAUSED_PLAYBACK, TRANSITIONING }

data class DlnaPlayback(val state: TransportState, val positionMs: Long, val durationMs: Long)

// what the renderer needs from the app
interface DlnaHost {
    fun approve(id: String, name: String): Boolean
    fun play(url: String, title: String, subtitleUrl: String?)
    fun resume()
    fun pause()
    fun stop()
    fun seek(positionMs: Long)
    fun playback(): DlnaPlayback
    fun volume(): Int          // 0..100
    fun setVolume(v: Int)
    fun muted(): Boolean
    fun setMuted(m: Boolean)
}

// a small UPnP/DLNA MediaRenderer so Android phones (and apps like BubbleUPnP, VLC,
// Web Video Caster) can send videos to the TV
class DlnaRenderer(
    private val context: Context,
    private val host: DlnaHost,
    private val friendlyName: String,
    private val uuid: String,
    private val version: String,
) {
    private val pool = Executors.newCachedThreadPool()
    @Volatile private var running = false
    private var server: ServerSocket? = null
    private var ssdp: MulticastSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var localIp: InetAddress? = null
    private var port = 0

    // AVTransport state set by the controller
    @Volatile private var currentUri = ""
    @Volatile private var currentMeta = ""
    @Volatile private var currentTitle = ""
    @Volatile private var currentSubtitle: String? = null
    @Volatile private var startedUri = ""

    // eventing (GENA): controllers subscribe and get LastChange notifications
    private data class Sub(val sid: String, val callback: String, val service: String, val seq: AtomicInteger = AtomicInteger(0))
    private val subs = ConcurrentHashMap<String, Sub>()
    @Volatile private var lastState: TransportState? = null

    fun start(): Boolean {
        val ip = pickLocalIp() ?: run { Log.w(TAG, "no LAN address"); return false }
        localIp = ip
        val ss = runCatching { ServerSocket(PREFERRED_PORT) }.getOrElse { ServerSocket(0) }
        server = ss
        port = ss.localPort
        running = true
        pool.execute { acceptLoop(ss) }
        multicastLock = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createMulticastLock("airplaytv-dlna")?.apply { setReferenceCounted(false); acquire() }
        pool.execute { ssdpLoop() }
        pool.execute { notifyLoop() }
        pool.execute { eventLoop() }
        Log.i(TAG, "DLNA renderer on http://${ip.hostAddress}:$port as \"$friendlyName\"")
        return true
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { sendNotify("ssdp:byebye") }
        runCatching { server?.close() }
        runCatching { ssdp?.close() }
        multicastLock?.let { runCatching { it.release() } }
        pool.shutdownNow()
    }

    // ---------- SSDP discovery ----------

    private val deviceType = "urn:schemas-upnp-org:device:MediaRenderer:1"
    private val serviceTypes = listOf(
        "urn:schemas-upnp-org:service:AVTransport:1",
        "urn:schemas-upnp-org:service:RenderingControl:1",
        "urn:schemas-upnp-org:service:ConnectionManager:1",
    )
    private val location get() = "http://${localIp?.hostAddress}:$port/dlna/description.xml"
    private val serverHeader get() = "Android/${android.os.Build.VERSION.RELEASE} UPnP/1.0 AirPlayTV/$version"

    private fun ssdpLoop() {
        try {
            val sock = MulticastSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(SSDP_PORT))
            }
            ssdp = sock
            val iface = NetworkInterface.getByInetAddress(localIp)
            sock.joinGroup(InetSocketAddress(SSDP_ADDR, SSDP_PORT), iface)
            val buf = ByteArray(2048)
            while (running) {
                val pkt = DatagramPacket(buf, buf.size)
                sock.receive(pkt)
                val msg = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
                if (!msg.startsWith("M-SEARCH", ignoreCase = true)) continue
                val st = header(msg, "ST") ?: continue
                val targets = when {
                    st == "ssdp:all" -> listOf("upnp:rootdevice", "uuid:$uuid", deviceType) + serviceTypes
                    st == "upnp:rootdevice" || st == "uuid:$uuid" || st == deviceType || st in serviceTypes -> listOf(st)
                    st.startsWith("urn:schemas-upnp-org:device:MediaRenderer:") -> listOf(st)
                    else -> emptyList()
                }
                if (targets.isEmpty()) continue
                val reply = DatagramSocket()
                targets.forEach { t ->
                    val usn = if (t == "uuid:$uuid") t else "uuid:$uuid::$t"
                    val body = "HTTP/1.1 200 OK\r\n" +
                        "CACHE-CONTROL: max-age=1800\r\n" +
                        "DATE: ${httpDate()}\r\n" +
                        "EXT:\r\n" +
                        "LOCATION: $location\r\n" +
                        "SERVER: $serverHeader\r\n" +
                        "ST: $t\r\n" +
                        "USN: $usn\r\n\r\n"
                    val bytes = body.toByteArray()
                    runCatching { reply.send(DatagramPacket(bytes, bytes.size, pkt.socketAddress)) }
                }
                reply.close()
            }
        } catch (e: Exception) {
            if (running) Log.w(TAG, "ssdp loop ended", e)
        }
    }

    private fun notifyLoop() {
        while (running) {
            runCatching { sendNotify("ssdp:alive") }
            try { Thread.sleep(NOTIFY_INTERVAL_MS) } catch (_: InterruptedException) { return }
        }
    }

    private fun sendNotify(nts: String) {
        val sock = DatagramSocket()
        val types = listOf("upnp:rootdevice", "uuid:$uuid", deviceType) + serviceTypes
        types.forEach { t ->
            val usn = if (t == "uuid:$uuid") t else "uuid:$uuid::$t"
            val msg = "NOTIFY * HTTP/1.1\r\n" +
                "HOST: $SSDP_ADDR:$SSDP_PORT\r\n" +
                "CACHE-CONTROL: max-age=1800\r\n" +
                "LOCATION: $location\r\n" +
                "NT: $t\r\n" +
                "NTS: $nts\r\n" +
                "SERVER: $serverHeader\r\n" +
                "USN: $usn\r\n\r\n"
            val bytes = msg.toByteArray()
            sock.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(SSDP_ADDR), SSDP_PORT))
        }
        sock.close()
    }

    // ---------- HTTP ----------

    private fun acceptLoop(ss: ServerSocket) {
        while (running) {
            val s = runCatching { ss.accept() }.getOrNull() ?: break
            pool.execute { runCatching { handle(s) }.onFailure { Log.w(TAG, "http error", it) }; runCatching { s.close() } }
        }
    }

    private class Request(val method: String, val path: String, val headers: Map<String, String>, val body: String, val remote: String)

    private fun readRequest(s: Socket): Request? {
        s.soTimeout = 15_000
        val input = BufferedInputStream(s.getInputStream())
        val head = readHead(input) ?: return null
        val lines = head.split("\r\n")
        val parts = lines.first().split(" ")
        if (parts.size < 2) return null
        val headers = HashMap<String, String>()
        lines.drop(1).forEach { l ->
            val i = l.indexOf(':')
            if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
        }
        val len = headers["content-length"]?.toIntOrNull() ?: 0
        val body = ByteArray(len.coerceIn(0, 1 shl 20))
        var read = 0
        while (read < body.size) {
            val n = input.read(body, read, body.size - read)
            if (n < 0) break
            read += n
        }
        return Request(parts[0].uppercase(), parts[1], headers, String(body, 0, read, Charsets.UTF_8),
            (s.inetAddress?.hostAddress ?: ""))
    }

    private fun readHead(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        var state = 0
        while (out.size() < 16_384) {
            val b = input.read()
            if (b < 0) return null
            out.write(b)
            state = when {
                state == 0 && b == '\r'.code -> 1
                state == 1 && b == '\n'.code -> 2
                state == 2 && b == '\r'.code -> 3
                state == 3 && b == '\n'.code -> return String(out.toByteArray(), Charsets.ISO_8859_1).trimEnd()
                b == '\r'.code -> 1
                else -> 0
            }
        }
        return null
    }

    private fun respond(s: Socket, code: Int, body: String, type: String = "text/xml; charset=\"utf-8\"", extra: Map<String, String> = emptyMap()) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val status = when (code) { 200 -> "OK"; 404 -> "Not Found"; 412 -> "Precondition Failed"; else -> "Internal Server Error" }
        val sb = StringBuilder("HTTP/1.1 $code $status\r\n")
        sb.append("Content-Type: $type\r\n")
        sb.append("Content-Length: ${bytes.size}\r\n")
        sb.append("Server: $serverHeader\r\n")
        sb.append("Date: ${httpDate()}\r\n")
        extra.forEach { (k, v) -> sb.append("$k: $v\r\n") }
        sb.append("Connection: close\r\n\r\n")
        val out = s.getOutputStream()
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
        out.write(bytes)
        out.flush()
    }

    private fun handle(s: Socket) {
        val req = readRequest(s) ?: return
        val path = req.path.substringBefore('?')
        when {
            req.method == "GET" && path == "/dlna/description.xml" -> respond(s, 200, descriptionXml())
            req.method == "GET" && path == "/dlna/AVTransport.xml" -> respond(s, 200, DlnaScpd.avTransport)
            req.method == "GET" && path == "/dlna/RenderingControl.xml" -> respond(s, 200, DlnaScpd.renderingControl)
            req.method == "GET" && path == "/dlna/ConnectionManager.xml" -> respond(s, 200, DlnaScpd.connectionManager)
            req.method == "POST" && path.endsWith("/control") -> control(s, req, path.removePrefix("/dlna/").substringBefore('/'))
            req.method == "SUBSCRIBE" && path.endsWith("/event") -> subscribe(s, req, path.removePrefix("/dlna/").substringBefore('/'))
            req.method == "UNSUBSCRIBE" && path.endsWith("/event") -> {
                req.headers["sid"]?.let { subs.remove(it) }
                respond(s, 200, "")
            }
            else -> respond(s, 404, "")
        }
    }

    private fun descriptionXml(): String {
        fun svc(name: String) = """<service><serviceType>urn:schemas-upnp-org:service:$name:1</serviceType>""" +
            """<serviceId>urn:upnp-org:serviceId:$name</serviceId><SCPDURL>/dlna/$name.xml</SCPDURL>""" +
            """<controlURL>/dlna/$name/control</controlURL><eventSubURL>/dlna/$name/event</eventSubURL></service>"""
        return """<?xml version="1.0" encoding="utf-8"?>
<root xmlns="urn:schemas-upnp-org:device-1-0" xmlns:dlna="urn:schemas-dlna-org:device-1-0">
<specVersion><major>1</major><minor>0</minor></specVersion>
<device>
<deviceType>$deviceType</deviceType>
<friendlyName>${xmlEscape(friendlyName)}</friendlyName>
<manufacturer>AirPlay TV</manufacturer>
<manufacturerURL>https://github.com/MehdiEskandari/airplay-tv</manufacturerURL>
<modelDescription>AirPlay and DLNA receiver for Android TV</modelDescription>
<modelName>AirPlay TV</modelName>
<modelNumber>$version</modelNumber>
<UDN>uuid:$uuid</UDN>
<dlna:X_DLNADOC>DMR-1.50</dlna:X_DLNADOC>
<serviceList>${svc("AVTransport")}${svc("RenderingControl")}${svc("ConnectionManager")}</serviceList>
</device>
</root>"""
    }

    // ---------- SOAP control ----------

    private fun control(s: Socket, req: Request, service: String) {
        val action = (req.headers["soapaction"] ?: "").trim('"').substringAfter('#')
        val args = parseArgs(req.body)
        val ns = "urn:schemas-upnp-org:service:$service:1"
        val out: Map<String, String>? = try {
            when (service) {
                "AVTransport" -> avTransport(action, args, req)
                "RenderingControl" -> renderingControl(action, args)
                "ConnectionManager" -> connectionManager(action)
                else -> null
            }
        } catch (e: DlnaError) {
            respond(s, 500, soapFault(e.code, e.message ?: "Action Failed"))
            return
        }
        if (out == null) {
            respond(s, 500, soapFault(401, "Invalid Action"))
            return
        }
        val body = out.entries.joinToString("") { (k, v) -> "<$k>${xmlEscape(v)}</$k>" }
        respond(
            s, 200,
            """<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:${action}Response xmlns:u="$ns">$body</u:${action}Response></s:Body></s:Envelope>"""
        )
    }

    private class DlnaError(val code: Int, msg: String) : Exception(msg)

    private fun avTransport(action: String, a: Map<String, String>, req: Request): Map<String, String>? {
        return when (action) {
            "SetAVTransportURI" -> {
                val sender = senderName(req)
                if (!host.approve("dlna:${req.remote}", sender)) throw DlnaError(501, "Not allowed on this TV")
                currentUri = a["CurrentURI"] ?: ""
                currentMeta = a["CurrentURIMetaData"] ?: ""
                currentTitle = Regex("<dc:title>(.*?)</dc:title>", RegexOption.DOT_MATCHES_ALL)
                    .find(currentMeta)?.groupValues?.get(1)?.let(::xmlUnescape) ?: ""
                currentSubtitle = findSubtitle(currentMeta)
                startedUri = ""
                // many controllers send Play right after; some expect playback to start now
                pool.execute {
                    Thread.sleep(1500)
                    if (startedUri.isEmpty() && currentUri.isNotEmpty()) startCurrent()
                }
                emptyMap()
            }
            "SetNextAVTransportURI" -> emptyMap()
            "Play" -> {
                if (currentUri.isEmpty()) throw DlnaError(701, "No media")
                if (startedUri != currentUri) startCurrent() else host.resume()
                emptyMap()
            }
            "Pause" -> { host.pause(); emptyMap() }
            "Stop" -> { host.stop(); startedUri = ""; emptyMap() }
            "Seek" -> {
                val unit = a["Unit"] ?: ""
                val target = a["Target"] ?: ""
                if (unit == "REL_TIME" || unit == "ABS_TIME") host.seek(parseTime(target))
                emptyMap()
            }
            "Next", "Previous" -> emptyMap()
            "GetTransportInfo" -> {
                val st = transportState()
                mapOf("CurrentTransportState" to st.name, "CurrentTransportStatus" to "OK", "CurrentSpeed" to "1")
            }
            "GetPositionInfo" -> {
                val p = host.playback()
                val active = currentUri.isNotEmpty() && startedUri == currentUri
                mapOf(
                    "Track" to if (currentUri.isEmpty()) "0" else "1",
                    "TrackDuration" to formatTime(if (active) p.durationMs else 0),
                    "TrackMetaData" to currentMeta.ifEmpty { "NOT_IMPLEMENTED" },
                    "TrackURI" to currentUri,
                    "RelTime" to formatTime(if (active) p.positionMs else 0),
                    "AbsTime" to formatTime(if (active) p.positionMs else 0),
                    "RelCount" to "2147483647",
                    "AbsCount" to "2147483647",
                )
            }
            "GetMediaInfo" -> {
                val p = host.playback()
                mapOf(
                    "NrTracks" to if (currentUri.isEmpty()) "0" else "1",
                    "MediaDuration" to formatTime(p.durationMs),
                    "CurrentURI" to currentUri,
                    "CurrentURIMetaData" to currentMeta,
                    "NextURI" to "",
                    "NextURIMetaData" to "",
                    "PlayMedium" to "NETWORK",
                    "RecordMedium" to "NOT_IMPLEMENTED",
                    "WriteStatus" to "NOT_IMPLEMENTED",
                )
            }
            "GetDeviceCapabilities" -> mapOf("PlayMedia" to "NETWORK", "RecMedia" to "NOT_IMPLEMENTED", "RecQualityModes" to "NOT_IMPLEMENTED")
            "GetTransportSettings" -> mapOf("PlayMode" to "NORMAL", "RecQualityMode" to "NOT_IMPLEMENTED")
            "GetCurrentTransportActions" -> {
                val actions = when (transportState()) {
                    TransportState.PLAYING -> "Pause,Stop,Seek"
                    TransportState.PAUSED_PLAYBACK -> "Play,Stop,Seek"
                    TransportState.STOPPED -> "Play"
                    else -> ""
                }
                mapOf("Actions" to actions)
            }
            else -> null
        }
    }

    private fun startCurrent() {
        startedUri = currentUri
        host.play(currentUri, currentTitle, currentSubtitle)
    }

    private fun transportState(): TransportState {
        if (currentUri.isEmpty()) return TransportState.NO_MEDIA_PRESENT
        if (startedUri != currentUri) return TransportState.STOPPED
        return host.playback().state
    }

    private fun renderingControl(action: String, a: Map<String, String>): Map<String, String>? = when (action) {
        "GetVolume" -> mapOf("CurrentVolume" to host.volume().toString())
        "SetVolume" -> { a["DesiredVolume"]?.toIntOrNull()?.let { host.setVolume(it.coerceIn(0, 100)) }; emptyMap() }
        "GetMute" -> mapOf("CurrentMute" to if (host.muted()) "1" else "0")
        "SetMute" -> { host.setMuted(a["DesiredMute"] == "1" || a["DesiredMute"].equals("true", true)); emptyMap() }
        "ListPresets" -> mapOf("CurrentPresetNameList" to "FactoryDefaults")
        "SelectPreset" -> emptyMap()
        else -> null
    }

    private fun connectionManager(action: String): Map<String, String>? = when (action) {
        "GetProtocolInfo" -> mapOf("Source" to "", "Sink" to SINK_PROTOCOLS)
        "GetCurrentConnectionIDs" -> mapOf("ConnectionIDs" to "0")
        "GetCurrentConnectionInfo" -> mapOf(
            "RcsID" to "0", "AVTransportID" to "0", "ProtocolInfo" to "",
            "PeerConnectionManager" to "", "PeerConnectionID" to "-1",
            "Direction" to "Input", "Status" to "OK"
        )
        else -> null
    }

    private fun soapFault(code: Int, desc: String) =
        """<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><s:Fault><faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail><UPnPError xmlns="urn:schemas-upnp-org:control-1-0"><errorCode>$code</errorCode><errorDescription>${xmlEscape(desc)}</errorDescription></UPnPError></detail></s:Fault></s:Body></s:Envelope>"""

    // ---------- GENA eventing ----------

    private fun subscribe(s: Socket, req: Request, service: String) {
        val existing = req.headers["sid"]
        if (existing != null) {
            if (!subs.containsKey(existing)) { respond(s, 412, ""); return }
            respond(s, 200, "", extra = mapOf("SID" to existing, "TIMEOUT" to "Second-1800"))
            return
        }
        val cb = Regex("<([^>]+)>").find(req.headers["callback"] ?: "")?.groupValues?.get(1)
        if (cb == null) { respond(s, 412, ""); return }
        val sid = "uuid:${UUID.randomUUID()}"
        val sub = Sub(sid, cb, service)
        subs[sid] = sub
        respond(s, 200, "", extra = mapOf("SID" to sid, "TIMEOUT" to "Second-1800"))
        pool.execute { Thread.sleep(200); sendEvent(sub) }
    }

    private fun eventLoop() {
        while (running) {
            try { Thread.sleep(1000) } catch (_: InterruptedException) { return }
            val st = runCatching { transportState() }.getOrNull() ?: continue
            if (st != lastState) {
                lastState = st
                subs.values.filter { it.service == "AVTransport" }.forEach { sub -> pool.execute { sendEvent(sub) } }
            }
        }
    }

    private fun sendEvent(sub: Sub) {
        val inner = when (sub.service) {
            "AVTransport" -> {
                val st = transportState()
                """<Event xmlns="urn:schemas-upnp-org:metadata-1-0/AVT/"><InstanceID val="0"><TransportState val="${st.name}"/><CurrentTransportActions val="Play,Pause,Stop,Seek"/><AVTransportURI val="${xmlEscape(currentUri)}"/></InstanceID></Event>"""
            }
            "RenderingControl" ->
                """<Event xmlns="urn:schemas-upnp-org:metadata-1-0/RCS/"><InstanceID val="0"><Volume channel="Master" val="${host.volume()}"/><Mute channel="Master" val="${if (host.muted()) 1 else 0}"/></InstanceID></Event>"""
            else -> return
        }
        val body = """<?xml version="1.0" encoding="utf-8"?><e:propertyset xmlns:e="urn:schemas-upnp-org:event-1-0"><e:property><LastChange>${xmlEscape(inner)}</LastChange></e:property></e:propertyset>"""
        runCatching {
            val url = URL(sub.callback)
            val bytes = body.toByteArray(Charsets.UTF_8)
            Socket().use { sock ->
                sock.connect(InetSocketAddress(url.host, if (url.port > 0) url.port else 80), 3000)
                sock.soTimeout = 3000
                val path = (url.path.ifEmpty { "/" }) + (url.query?.let { "?$it" } ?: "")
                val head = "NOTIFY $path HTTP/1.1\r\n" +
                    "HOST: ${url.host}:${if (url.port > 0) url.port else 80}\r\n" +
                    "CONTENT-TYPE: text/xml; charset=\"utf-8\"\r\n" +
                    "CONTENT-LENGTH: ${bytes.size}\r\n" +
                    "NT: upnp:event\r\n" +
                    "NTS: upnp:propchange\r\n" +
                    "SID: ${sub.sid}\r\n" +
                    "SEQ: ${sub.seq.getAndIncrement()}\r\n" +
                    "CONNECTION: close\r\n\r\n"
                val out = sock.getOutputStream()
                out.write(head.toByteArray(Charsets.ISO_8859_1))
                out.write(bytes)
                out.flush()
                runCatching { sock.getInputStream().read() }
            }
        }.onFailure { subs.remove(sub.sid) }
    }

    // ---------- helpers ----------

    private fun senderName(req: Request): String {
        val ua = req.headers["user-agent"] ?: ""
        val app = ua.split(' ', '/').firstOrNull { it.isNotBlank() && !it.startsWith("Dalvik") && !it.startsWith("Linux") }
        return if (!app.isNullOrBlank()) "$app (${req.remote})" else req.remote
    }

    private fun findSubtitle(meta: String): String? {
        if (meta.isEmpty()) return null
        Regex("<sec:CaptionInfoEx[^>]*>(.*?)</sec:CaptionInfoEx>", RegexOption.DOT_MATCHES_ALL).find(meta)
            ?.let { return xmlUnescape(it.groupValues[1].trim()) }
        Regex("<sec:CaptionInfo[^>]*>(.*?)</sec:CaptionInfo>", RegexOption.DOT_MATCHES_ALL).find(meta)
            ?.let { return xmlUnescape(it.groupValues[1].trim()) }
        Regex("<res[^>]*protocolInfo=\"[^\"]*(text/srt|text/vtt|application/x-subrip|smi/caption)[^\"]*\"[^>]*>(.*?)</res>", RegexOption.DOT_MATCHES_ALL)
            .find(meta)?.let { return xmlUnescape(it.groupValues[2].trim()) }
        return null
    }

    private fun parseArgs(body: String): Map<String, String> {
        val inner = Regex("<[^:>]*:?Body[^>]*>(.*)</[^:>]*:?Body>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1) ?: body
        val actionInner = Regex("<[^>]+>(.*)</[^>]+>", RegexOption.DOT_MATCHES_ALL).find(inner.trim())?.groupValues?.get(1) ?: ""
        val out = HashMap<String, String>()
        Regex("<([A-Za-z0-9_]+)(?:\\s[^>]*)?>(.*?)</\\1>", RegexOption.DOT_MATCHES_ALL).findAll(actionInner).forEach {
            out[it.groupValues[1]] = xmlUnescape(it.groupValues[2])
        }
        Regex("<([A-Za-z0-9_]+)(?:\\s[^>]*)?/>").findAll(actionInner).forEach { out.putIfAbsent(it.groupValues[1], "") }
        return out
    }

    private fun header(msg: String, name: String): String? =
        msg.lineSequence().firstOrNull { it.startsWith("$name:", ignoreCase = true) }?.substringAfter(':')?.trim()

    private fun pickLocalIp(): InetAddress? {
        val candidates = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.name.startsWith("tun") && !it.name.startsWith("ppp") }
            .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
        for (ni in candidates) {
            ni.inetAddresses.toList().firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.let { return it }
        }
        return null
    }

    companion object {
        private const val TAG = "DlnaRenderer"
        private const val SSDP_ADDR = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val PREFERRED_PORT = 49494
        private const val NOTIFY_INTERVAL_MS = 60_000L
        private const val SINK_PROTOCOLS =
            "http-get:*:video/mp4:*,http-get:*:video/x-matroska:*,http-get:*:video/webm:*," +
            "http-get:*:video/mpeg:*,http-get:*:video/x-msvideo:*,http-get:*:video/quicktime:*," +
            "http-get:*:video/MP2T:*,http-get:*:application/x-mpegURL:*,http-get:*:application/vnd.apple.mpegurl:*," +
            "http-get:*:audio/mpeg:*,http-get:*:audio/mp4:*,http-get:*:audio/aac:*,http-get:*:audio/flac:*," +
            "http-get:*:audio/x-flac:*,http-get:*:audio/wav:*,http-get:*:audio/ogg:*,http-get:*:*:*"

        fun formatTime(ms: Long): String {
            val t = (ms / 1000).coerceAtLeast(0)
            return "%d:%02d:%02d".format(t / 3600, (t / 60) % 60, t % 60)
        }

        fun parseTime(s: String): Long {
            val p = s.trim().split(':')
            if (p.size != 3) return 0
            val h = p[0].toLongOrNull() ?: 0
            val m = p[1].toLongOrNull() ?: 0
            val sec = p[2].toDoubleOrNull() ?: 0.0
            return h * 3600_000 + m * 60_000 + (sec * 1000).toLong()
        }

        fun xmlEscape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        fun xmlUnescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&apos;", "'").replace("&amp;", "&")

        private fun httpDate(): String = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("GMT") }.format(Date())
    }
}
