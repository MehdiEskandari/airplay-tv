package io.github.mehdieskandari.airplaytv.dlna

// UPnP service descriptions: controllers read these to learn which actions the TV supports
object DlnaScpd {

    private class Arg(val name: String, val out: Boolean, val variable: String)
    private class Action(val name: String, val args: List<Arg>)
    private class Var(val name: String, val type: String = "string", val events: Boolean = false, val allowed: List<String> = emptyList())

    private fun i(name: String, v: String) = Arg(name, false, v)
    private fun o(name: String, v: String) = Arg(name, true, v)
    private val iid = i("InstanceID", "A_ARG_TYPE_InstanceID")

    private fun build(actions: List<Action>, vars: List<Var>): String {
        val a = actions.joinToString("") { act ->
            "<action><name>${act.name}</name><argumentList>" +
                act.args.joinToString("") {
                    "<argument><name>${it.name}</name><direction>${if (it.out) "out" else "in"}</direction>" +
                        "<relatedStateVariable>${it.variable}</relatedStateVariable></argument>"
                } + "</argumentList></action>"
        }
        val v = vars.joinToString("") { sv ->
            "<stateVariable sendEvents=\"${if (sv.events) "yes" else "no"}\"><name>${sv.name}</name>" +
                "<dataType>${sv.type}</dataType>" +
                (if (sv.allowed.isNotEmpty()) "<allowedValueList>" + sv.allowed.joinToString("") { "<allowedValue>$it</allowedValue>" } + "</allowedValueList>" else "") +
                "</stateVariable>"
        }
        return """<?xml version="1.0" encoding="utf-8"?><scpd xmlns="urn:schemas-upnp-org:service-1-0"><specVersion><major>1</major><minor>0</minor></specVersion><actionList>$a</actionList><serviceStateTable>$v</serviceStateTable></scpd>"""
    }

    val avTransport: String = build(
        listOf(
            Action("SetAVTransportURI", listOf(iid, i("CurrentURI", "AVTransportURI"), i("CurrentURIMetaData", "AVTransportURIMetaData"))),
            Action("SetNextAVTransportURI", listOf(iid, i("NextURI", "NextAVTransportURI"), i("NextURIMetaData", "NextAVTransportURIMetaData"))),
            Action("GetMediaInfo", listOf(iid, o("NrTracks", "NumberOfTracks"), o("MediaDuration", "CurrentMediaDuration"),
                o("CurrentURI", "AVTransportURI"), o("CurrentURIMetaData", "AVTransportURIMetaData"),
                o("NextURI", "NextAVTransportURI"), o("NextURIMetaData", "NextAVTransportURIMetaData"),
                o("PlayMedium", "PlaybackStorageMedium"), o("RecordMedium", "RecordStorageMedium"),
                o("WriteStatus", "RecordMediumWriteStatus"))),
            Action("GetTransportInfo", listOf(iid, o("CurrentTransportState", "TransportState"),
                o("CurrentTransportStatus", "TransportStatus"), o("CurrentSpeed", "TransportPlaySpeed"))),
            Action("GetPositionInfo", listOf(iid, o("Track", "CurrentTrack"), o("TrackDuration", "CurrentTrackDuration"),
                o("TrackMetaData", "CurrentTrackMetaData"), o("TrackURI", "CurrentTrackURI"),
                o("RelTime", "RelativeTimePosition"), o("AbsTime", "AbsoluteTimePosition"),
                o("RelCount", "RelativeCounterPosition"), o("AbsCount", "AbsoluteCounterPosition"))),
            Action("GetDeviceCapabilities", listOf(iid, o("PlayMedia", "PossiblePlaybackStorageMedia"),
                o("RecMedia", "PossibleRecordStorageMedia"), o("RecQualityModes", "PossibleRecordQualityModes"))),
            Action("GetTransportSettings", listOf(iid, o("PlayMode", "CurrentPlayMode"), o("RecQualityMode", "CurrentRecordQualityMode"))),
            Action("Stop", listOf(iid)),
            Action("Play", listOf(iid, i("Speed", "TransportPlaySpeed"))),
            Action("Pause", listOf(iid)),
            Action("Seek", listOf(iid, i("Unit", "A_ARG_TYPE_SeekMode"), i("Target", "A_ARG_TYPE_SeekTarget"))),
            Action("Next", listOf(iid)),
            Action("Previous", listOf(iid)),
            Action("GetCurrentTransportActions", listOf(iid, o("Actions", "CurrentTransportActions"))),
        ),
        listOf(
            Var("TransportState", allowed = listOf("STOPPED", "PLAYING", "TRANSITIONING", "PAUSED_PLAYBACK", "NO_MEDIA_PRESENT")),
            Var("TransportStatus", allowed = listOf("OK", "ERROR_OCCURRED")),
            Var("TransportPlaySpeed", allowed = listOf("1")),
            Var("NumberOfTracks", "ui4"), Var("CurrentMediaDuration"), Var("AVTransportURI"), Var("AVTransportURIMetaData"),
            Var("NextAVTransportURI"), Var("NextAVTransportURIMetaData"),
            Var("PlaybackStorageMedium", allowed = listOf("NETWORK", "NONE")),
            Var("RecordStorageMedium", allowed = listOf("NOT_IMPLEMENTED")),
            Var("RecordMediumWriteStatus", allowed = listOf("NOT_IMPLEMENTED")),
            Var("CurrentTrack", "ui4"), Var("CurrentTrackDuration"), Var("CurrentTrackMetaData"), Var("CurrentTrackURI"),
            Var("RelativeTimePosition"), Var("AbsoluteTimePosition"),
            Var("RelativeCounterPosition", "i4"), Var("AbsoluteCounterPosition", "i4"),
            Var("PossiblePlaybackStorageMedia"), Var("PossibleRecordStorageMedia"), Var("PossibleRecordQualityModes"),
            Var("CurrentPlayMode", allowed = listOf("NORMAL")), Var("CurrentRecordQualityMode"),
            Var("CurrentTransportActions"), Var("LastChange", events = true),
            Var("A_ARG_TYPE_InstanceID", "ui4"),
            Var("A_ARG_TYPE_SeekMode", allowed = listOf("REL_TIME", "ABS_TIME", "TRACK_NR")),
            Var("A_ARG_TYPE_SeekTarget"),
        )
    )

    val renderingControl: String = build(
        listOf(
            Action("ListPresets", listOf(iid, o("CurrentPresetNameList", "PresetNameList"))),
            Action("SelectPreset", listOf(iid, i("PresetName", "A_ARG_TYPE_PresetName"))),
            Action("GetMute", listOf(iid, i("Channel", "A_ARG_TYPE_Channel"), o("CurrentMute", "Mute"))),
            Action("SetMute", listOf(iid, i("Channel", "A_ARG_TYPE_Channel"), i("DesiredMute", "Mute"))),
            Action("GetVolume", listOf(iid, i("Channel", "A_ARG_TYPE_Channel"), o("CurrentVolume", "Volume"))),
            Action("SetVolume", listOf(iid, i("Channel", "A_ARG_TYPE_Channel"), i("DesiredVolume", "Volume"))),
        ),
        listOf(
            Var("PresetNameList"), Var("Mute", "boolean"), Var("Volume", "ui2"), Var("LastChange", events = true),
            Var("A_ARG_TYPE_InstanceID", "ui4"),
            Var("A_ARG_TYPE_Channel", allowed = listOf("Master")),
            Var("A_ARG_TYPE_PresetName", allowed = listOf("FactoryDefaults")),
        )
    )

    val connectionManager: String = build(
        listOf(
            Action("GetProtocolInfo", listOf(o("Source", "SourceProtocolInfo"), o("Sink", "SinkProtocolInfo"))),
            Action("GetCurrentConnectionIDs", listOf(o("ConnectionIDs", "CurrentConnectionIDs"))),
            Action("GetCurrentConnectionInfo", listOf(i("ConnectionID", "A_ARG_TYPE_ConnectionID"),
                o("RcsID", "A_ARG_TYPE_RcsID"), o("AVTransportID", "A_ARG_TYPE_AVTransportID"),
                o("ProtocolInfo", "A_ARG_TYPE_ProtocolInfo"), o("PeerConnectionManager", "A_ARG_TYPE_ConnectionManager"),
                o("PeerConnectionID", "A_ARG_TYPE_ConnectionID"), o("Direction", "A_ARG_TYPE_Direction"),
                o("Status", "A_ARG_TYPE_ConnectionStatus"))),
        ),
        listOf(
            Var("SourceProtocolInfo", events = true), Var("SinkProtocolInfo", events = true), Var("CurrentConnectionIDs", events = true),
            Var("A_ARG_TYPE_ConnectionStatus", allowed = listOf("OK", "ContentFormatMismatch", "InsufficientBandwidth", "UnreliableChannel", "Unknown")),
            Var("A_ARG_TYPE_ConnectionManager"),
            Var("A_ARG_TYPE_Direction", allowed = listOf("Input", "Output")),
            Var("A_ARG_TYPE_ProtocolInfo"),
            Var("A_ARG_TYPE_ConnectionID", "i4"), Var("A_ARG_TYPE_AVTransportID", "i4"), Var("A_ARG_TYPE_RcsID", "i4"),
        )
    )
}
