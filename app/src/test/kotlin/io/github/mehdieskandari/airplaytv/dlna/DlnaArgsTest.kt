package io.github.mehdieskandari.airplaytv.dlna

import org.junit.Assert.*
import org.junit.Test

class DlnaArgsTest {
    private fun soap(arguments: String) = """
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
          <s:Body><u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
            $arguments
          </u:SetAVTransportURI></s:Body>
        </s:Envelope>
    """.trimIndent()

    @Test fun emptyMetadataIsRetainedAlongsideTheVideoUrl() {
        val args = DlnaRenderer.parseArgs(soap("""
            <InstanceID>0</InstanceID>
            <CurrentURI>http://example.test/video.mp4?a=1&amp;b=2</CurrentURI>
            <CurrentURIMetaData />
        """))
        assertEquals("0", args["InstanceID"])
        assertEquals("http://example.test/video.mp4?a=1&b=2", args["CurrentURI"])
        assertTrue(args.containsKey("CurrentURIMetaData"))
        assertEquals("", args["CurrentURIMetaData"])
    }

    @Test fun selfClosingDuplicateDoesNotEraseANonEmptyArgument() {
        val args = DlnaRenderer.parseArgs(soap("<CurrentURI>http://example.test/video.mp4</CurrentURI><CurrentURI/>"))
        assertEquals("http://example.test/video.mp4", args["CurrentURI"])
    }

    @Test fun escapedSubtitleMetadataIsDecoded() {
        val args = DlnaRenderer.parseArgs(soap("<CurrentURIMetaData>&lt;title&gt;Movie &amp; music&lt;/title&gt;</CurrentURIMetaData>"))
        assertEquals("<title>Movie & music</title>", args["CurrentURIMetaData"])
    }
}
