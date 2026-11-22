package io.github.mehdieskandari.airplaytv.ui

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import android.content.Intent
import io.github.mehdieskandari.airplaytv.MainActivity
import io.github.mehdieskandari.airplaytv.BuildConfig
import io.github.mehdieskandari.airplaytv.service.StoreLinks
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.mehdieskandari.airplaytv.Prefs
import io.github.mehdieskandari.airplaytv.R
import io.github.mehdieskandari.airplaytv.appSettingsIntent
import io.github.mehdieskandari.airplaytv.viewmodel.MainViewModel
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val serverName by viewModel.pref(Prefs.SERVER_NAME).collectAsState()
    val h265 by viewModel.pref(Prefs.H265).collectAsState()
    val enforceSdr by viewModel.pref(Prefs.ENFORCE_SDR).collectAsState()
    val alacEnabled by viewModel.pref(Prefs.ALAC_ENABLED).collectAsState()
    val aacEnabled by viewModel.pref(Prefs.AAC_ENABLED).collectAsState()
    val resolution by viewModel.pref(Prefs.RESOLUTION).collectAsState()
    val idlePreview by viewModel.pref(Prefs.IDLE_PREVIEW).collectAsState()
    val autoFullscreen by viewModel.pref(Prefs.AUTO_FULLSCREEN).collectAsState()
    val keepScreenOn by viewModel.pref(Prefs.KEEP_SCREEN_ON).collectAsState()
    val advertiseVideo by viewModel.pref(Prefs.ADVERTISE_VIDEO).collectAsState()
    val advertiseMirroring by viewModel.pref(Prefs.ADVERTISE_MIRRORING).collectAsState()
    val advertiseAudio by viewModel.pref(Prefs.ADVERTISE_AUDIO).collectAsState()
    val launchOnConnect by viewModel.pref(Prefs.LAUNCH_ON_CONNECT).collectAsState()
    val maxFps by viewModel.pref(Prefs.MAX_FPS).collectAsState()
    val overscanned by viewModel.pref(Prefs.OVERSCANNED).collectAsState()
    val requirePin by viewModel.pref(Prefs.REQUIRE_PIN).collectAsState()
    val allowNewConn by viewModel.pref(Prefs.ALLOW_NEW_CONN).collectAsState()
    val usbOnly by viewModel.pref(Prefs.USB_ONLY).collectAsState()
    val autoStart by viewModel.pref(Prefs.AUTO_START).collectAsState()
    val bootAutoStart by viewModel.pref(Prefs.BOOT_AUTO_START).collectAsState()
    val runInBackground by viewModel.pref(Prefs.RUN_IN_BACKGROUND).collectAsState()
    val serverPort by viewModel.pref(Prefs.SERVER_PORT).collectAsState()
    val audioLatencyMs by viewModel.pref(Prefs.AUDIO_LATENCY_MS).collectAsState()
    val forceSwAlac by viewModel.pref(Prefs.FORCE_SW_ALAC).collectAsState()
    val forceSwAac by viewModel.pref(Prefs.FORCE_SW_AAC).collectAsState()
    val syncClientVolume by viewModel.pref(Prefs.SYNC_CLIENT_VOLUME).collectAsState()
    val debugEnabled by viewModel.pref(Prefs.DEBUG_ENABLED).collectAsState()
    val developerOptions by viewModel.pref(Prefs.DEVELOPER_OPTIONS).collectAsState()
    val keyAllowFrameDrop by viewModel.pref(Prefs.KEY_ALLOW_FRAME_DROP).collectAsState()
    val realtimeDecoderPriority by viewModel.pref(Prefs.KEY_PRIORITY).collectAsState()
    val lowLatency by viewModel.pref(Prefs.LOW_LATENCY).collectAsState()
    val operatingRate by viewModel.pref(Prefs.OPERATING_RATE).collectAsState()
    val scheduledOutputBufferRelease by viewModel.pref(Prefs.SCHEDULED_OUTPUT_BUFFER_RELEASE).collectAsState()
    val benchmarkLog by viewModel.pref(Prefs.BENCHMARK_LOG).collectAsState()
    val audioAutoBuffer by viewModel.pref(Prefs.AUDIO_AUTO_BUFFER).collectAsState()
    val audioCushionMs by viewModel.pref(Prefs.AUDIO_CUSHION_MS).collectAsState()
    val audioAdaptiveStep by viewModel.pref(Prefs.AUDIO_ADAPTIVE_STEP).collectAsState()
    val oboeBufferFrames by viewModel.pref(Prefs.OBOE_BUFFER_FRAMES).collectAsState()
    val tri = listOf(
        Prefs.AUTO to stringResource(R.string.chip_auto),
        Prefs.ON to stringResource(R.string.chip_on),
        Prefs.OFF to stringResource(R.string.chip_off),
    )

    val tvLayout = isTv()
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(surface = Color.Transparent)) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
        modifier = Modifier
            .then(if (tvLayout) Modifier.fillMaxWidth(0.74f) else Modifier.fillMaxWidth())
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        LanguageSettings(viewModel)
        SectionHeader(stringResource(R.string.section_appearance))
        val appearance by viewModel.pref(Prefs.APPEARANCE).collectAsState()
        SettingChips(
            title = stringResource(R.string.setting_theme),
            description = stringResource(R.string.setting_theme_desc),
            value = appearance,
            options = listOf(
                Prefs.THEME_TELI_DARK to stringResource(R.string.theme_teli_dark),
                Prefs.THEME_TELI_LIGHT to stringResource(R.string.theme_teli_light),
                Prefs.THEME_SOLID_DARK to stringResource(R.string.theme_solid_dark),
            ),
            onValueChange = { viewModel.set(Prefs.APPEARANCE, it) }
        )
        SectionHeader(stringResource(R.string.section_server))

        SettingTextField(
            label = stringResource(R.string.setting_server_name),
            value = serverName,
            onCommit = { viewModel.set(Prefs.SERVER_NAME, it) }
        )

        SettingTextField(
            label = stringResource(R.string.setting_server_port),
            value = serverPort.toString(),
            onCommit = { viewModel.set(Prefs.SERVER_PORT, it.toInt()) },
            range = Prefs.SERVER_PORT.range
        )

        SettingSwitch(
            title = stringResource(R.string.setting_boot_auto_start),
            description = stringResource(R.string.setting_boot_auto_start_desc),
            checked = bootAutoStart,
            onCheckedChange = { viewModel.set(Prefs.BOOT_AUTO_START, it) }
        )

        SettingSwitch(
            title = stringResource(R.string.setting_run_in_background),
            description = stringResource(R.string.setting_run_in_background_desc),
            checked = runInBackground,
            onCheckedChange = { viewModel.set(Prefs.RUN_IN_BACKGROUND, it) }
        )

        SectionHeader(stringResource(R.string.section_connection))

        SettingSegmented(
            title = stringResource(R.string.setting_discovery),
            summary = stringResource(if (usbOnly) R.string.setting_discovery_usb_desc else R.string.setting_discovery_all_desc),
            value = usbOnly,
            options = listOf(
                false to stringResource(R.string.chip_all_networks),
                true to stringResource(R.string.chip_usb_only),
            ),
            onValueChange = { viewModel.set(Prefs.USB_ONLY, it) }
        )

        SettingSwitch(
            title = stringResource(R.string.setting_require_pin),
            description = stringResource(R.string.setting_require_pin_desc),
            checked = requirePin,
            onCheckedChange = { viewModel.set(Prefs.REQUIRE_PIN, it) }
        )

        SettingSwitch(
            title = stringResource(R.string.setting_allow_new_conn),
            description = stringResource(R.string.setting_allow_new_conn_desc),
            checked = allowNewConn,
            onCheckedChange = { viewModel.set(Prefs.ALLOW_NEW_CONN, it) }
        )

        val ctx = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        var hasOverlayPermission by remember { mutableStateOf(canAutoLaunch(ctx)) }
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) hasOverlayPermission = canAutoLaunch(ctx)
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        val needsOverlayPermission = launchOnConnect && !hasOverlayPermission
        val autoLaunch = launchOnConnect && hasOverlayPermission
        ListItem(
            modifier = Modifier
                .dpadFocus(RectangleShape)
                .toggleable(
                    value = autoLaunch,
                    role = Role.Switch,
                    onValueChange = {
                        viewModel.set(Prefs.LAUNCH_ON_CONNECT, it)
                        if (it && !canAutoLaunch(ctx)) _openOverlaySettings(ctx)
                    }
                ),
            headlineContent = { Text(stringResource(R.string.setting_launch_on_connect)) },
            supportingContent = {
                Text(stringResource(
                    if (needsOverlayPermission) R.string.setting_launch_on_connect_no_permission
                    else R.string.setting_launch_on_connect_desc
                ))
            },
            trailingContent = {
                Switch(checked = autoLaunch, onCheckedChange = null)
            }
        )

        SectionHeader(stringResource(R.string.section_display))

        SettingSwitch(
            title = stringResource(R.string.setting_auto_fullscreen),
            description = stringResource(R.string.setting_auto_fullscreen_desc),
            checked = autoFullscreen,
            onCheckedChange = { viewModel.set(Prefs.AUTO_FULLSCREEN, it) }
        )

        SettingResolution(
            value = resolution,
            onValueChange = { viewModel.set(Prefs.RESOLUTION, it) }
        )

        SettingChipField(
            title = stringResource(R.string.setting_max_fps),
            description = stringResource(R.string.setting_max_fps_desc),
            value = maxFps.toString(),
            presets = listOf("24" to "24", "30" to "30", "60" to "60", "120" to "120"),
            placeholder = stringResource(R.string.setting_max_fps_placeholder),
            keyboardType = KeyboardType.Number,
            onValueChange = { it.toIntOrNull()?.let { v -> viewModel.set(Prefs.MAX_FPS, v) } }
        )

        SettingSwitch(
            title = stringResource(R.string.setting_overscanned),
            description = stringResource(R.string.setting_overscanned_desc),
            checked = overscanned,
            onCheckedChange = { viewModel.set(Prefs.OVERSCANNED, it) }
        )

        DeviceSettings(viewModel)

        UpdateSettings(viewModel)

        SubtitleSettings(viewModel)

        SectionHeader(stringResource(R.string.section_decode))

        SettingChips(
            title = stringResource(R.string.setting_h265),
            description = stringResource(R.string.setting_h265_desc),
            value = h265,
            options = tri,
            onValueChange = { viewModel.set(Prefs.H265, it) }
        )

        SettingSwitch(
            title = stringResource(R.string.setting_sw_alac),
            description = stringResource(R.string.setting_sw_alac_desc),
            checked = forceSwAlac,
            onCheckedChange = { viewModel.set(Prefs.FORCE_SW_ALAC, it) }
        )

        SettingSwitch(
            title = stringResource(R.string.setting_sw_aac),
            description = stringResource(R.string.setting_sw_aac_desc),
            checked = forceSwAac,
            onCheckedChange = { viewModel.set(Prefs.FORCE_SW_AAC, it) }
        )

        SettingSwitch(
            title = stringResource(R.string.setting_sync_client_volume),
            description = stringResource(R.string.setting_sync_client_volume_desc),
            checked = syncClientVolume,
            onCheckedChange = { viewModel.set(Prefs.SYNC_CLIENT_VOLUME, it) }
        )

        SectionHeader(stringResource(R.string.section_developer))

        SettingSwitch(
            title = stringResource(R.string.setting_developer_options),
            description = stringResource(R.string.setting_developer_options_desc),
            checked = developerOptions,
            onCheckedChange = { viewModel.set(Prefs.DEVELOPER_OPTIONS, it) }
        )

        if (developerOptions) {
            SettingSwitch(
                title = stringResource(R.string.setting_auto_start),
                description = stringResource(R.string.setting_auto_start_desc),
                checked = autoStart,
                onCheckedChange = { viewModel.set(Prefs.AUTO_START, it) }
            )

            SettingSwitch(
                title = stringResource(R.string.setting_keep_screen_on),
                description = stringResource(R.string.setting_keep_screen_on_desc),
                checked = keepScreenOn,
                onCheckedChange = { viewModel.set(Prefs.KEEP_SCREEN_ON, it) }
            )

            SettingSwitch(
                title = stringResource(R.string.setting_idle_preview),
                description = stringResource(R.string.setting_idle_preview_desc),
                checked = idlePreview,
                onCheckedChange = { viewModel.set(Prefs.IDLE_PREVIEW, it) }
            )

            SettingSwitch(
                title = stringResource(R.string.setting_advertise_video),
                description = stringResource(R.string.setting_advertise_video_desc),
                checked = advertiseVideo,
                onCheckedChange = { viewModel.set(Prefs.ADVERTISE_VIDEO, it) }
            )

            SettingSwitch(
                title = stringResource(R.string.setting_advertise_mirroring),
                description = stringResource(R.string.setting_advertise_mirroring_desc),
                checked = advertiseMirroring,
                onCheckedChange = { viewModel.set(Prefs.ADVERTISE_MIRRORING, it) }
            )

            SettingSwitch(
                title = stringResource(R.string.setting_advertise_audio),
                description = stringResource(R.string.setting_advertise_audio_desc),
                checked = advertiseAudio,
                onCheckedChange = { viewModel.set(Prefs.ADVERTISE_AUDIO, it) }
            )

            SettingSwitch(
                title = stringResource(R.string.setting_alac),
                description = stringResource(R.string.setting_alac_desc),
                checked = alacEnabled,
                onCheckedChange = { viewModel.set(Prefs.ALAC_ENABLED, it) }
            )

            SettingSwitch(
                title = stringResource(R.string.setting_aac),
                description = stringResource(R.string.setting_aac_desc),
                checked = aacEnabled,
                onCheckedChange = { viewModel.set(Prefs.AAC_ENABLED, it) }
            )

            SettingSwitch(
                title = stringResource(R.string.setting_key_allow_frame_drop),
                description = stringResource(R.string.setting_key_allow_frame_drop_desc),
                checked = keyAllowFrameDrop,
                onCheckedChange = { viewModel.set(Prefs.KEY_ALLOW_FRAME_DROP, it) }
            )

            SettingSwitch(
                title = stringResource(R.string.setting_enforce_sdr),
                description = stringResource(R.string.setting_enforce_sdr_desc),
                checked = enforceSdr,
                onCheckedChange = { viewModel.set(Prefs.ENFORCE_SDR, it) }
            )

            SettingSwitch(
                title = stringResource(R.string.setting_realtime_decoder_priority),
                description = stringResource(R.string.setting_realtime_decoder_priority_desc),
                checked = realtimeDecoderPriority,
                onCheckedChange = { viewModel.set(Prefs.KEY_PRIORITY, it) }
            )

            SettingChips(
                title = stringResource(R.string.setting_operating_rate),
                description = stringResource(R.string.setting_operating_rate_desc),
                value = operatingRate,
                options = tri,
                onValueChange = { viewModel.set(Prefs.OPERATING_RATE, it) }
            )

            SettingSwitch(
                title = stringResource(R.string.setting_low_latency),
                description = stringResource(R.string.setting_low_latency_desc),
                checked = lowLatency,
                onCheckedChange = { viewModel.set(Prefs.LOW_LATENCY, it) }
            )

            SettingSwitch(
                title = stringResource(R.string.setting_scheduled_output_buffer_release),
                description = stringResource(R.string.setting_scheduled_output_buffer_release_desc),
                checked = scheduledOutputBufferRelease,
                onCheckedChange = { viewModel.set(Prefs.SCHEDULED_OUTPUT_BUFFER_RELEASE, it) }
            )

            SettingSwitch(
                title = stringResource(R.string.setting_audio_delay),
                description = stringResource(R.string.setting_audio_delay_desc),
                checked = audioLatencyMs >= 0,
                onCheckedChange = { viewModel.set(Prefs.AUDIO_LATENCY_MS, if (it) 250 else -1) }
            )

            if (audioLatencyMs >= 0) {
                var sliderVal by remember(audioLatencyMs) { mutableFloatStateOf(audioLatencyMs.toFloat()) }
                ListItem(
                    headlineContent = {
                        Slider(
                            value = sliderVal,
                            onValueChange = { sliderVal = it },
                            onValueChangeFinished = { viewModel.set(Prefs.AUDIO_LATENCY_MS, sliderVal.roundToInt()) },
                            valueRange = 0f..1000f,
                            steps = 19,
                            modifier = Modifier.dpadFocus().dpadAdjust(
                                onLeft = { viewModel.set(Prefs.AUDIO_LATENCY_MS, (audioLatencyMs - 50).coerceIn(0, 1000)) },
                                onRight = { viewModel.set(Prefs.AUDIO_LATENCY_MS, (audioLatencyMs + 50).coerceIn(0, 1000)) }
                            )
                        )
                    },
                    trailingContent = { Text(stringResource(R.string.audio_delay_value, sliderVal.roundToInt())) }
                )
            }

            SettingSwitch(
                title = stringResource(R.string.setting_audio_auto_buffer),
                description = stringResource(R.string.setting_audio_auto_buffer_desc),
                checked = audioAutoBuffer,
                onCheckedChange = { viewModel.set(Prefs.AUDIO_AUTO_BUFFER, it) }
            )

            if (audioAutoBuffer) {
                val maxStep = Prefs.AUDIO_ADAPTIVE_STEP.range.last
                var stepVal by remember(audioAdaptiveStep) { mutableFloatStateOf(audioAdaptiveStep.toFloat()) }
                ListItem(
                    headlineContent = {
                        Slider(
                            value = stepVal,
                            onValueChange = { stepVal = it },
                            onValueChangeFinished = { viewModel.set(Prefs.AUDIO_ADAPTIVE_STEP, stepVal.roundToInt()) },
                            valueRange = 0f..maxStep.toFloat(),
                            steps = maxStep - 1,
                            modifier = Modifier.dpadFocus().dpadAdjust(
                                onLeft = { viewModel.set(Prefs.AUDIO_ADAPTIVE_STEP, (audioAdaptiveStep - 1).coerceIn(0, maxStep)) },
                                onRight = { viewModel.set(Prefs.AUDIO_ADAPTIVE_STEP, (audioAdaptiveStep + 1).coerceIn(0, maxStep)) }
                            )
                        )
                    },
                    trailingContent = {
                        val step = stepVal.roundToInt().coerceIn(0, maxStep)
                        Text(stringResource(R.string.audio_adaptive_value,
                            Prefs.ADAPTIVE_PERCENTILES[step],
                            stringArrayResource(R.array.audio_adaptive_step_names)[step]))
                    }
                )
            } else {
                SettingTextField(
                    label = stringResource(R.string.setting_audio_cushion_ms),
                    value = audioCushionMs.toString(),
                    onCommit = { viewModel.set(Prefs.AUDIO_CUSHION_MS, it.toInt()) },
                    description = stringResource(R.string.setting_audio_cushion_ms_desc),
                    range = Prefs.AUDIO_CUSHION_MS.range
                )
            }

            SettingTextField(
                label = stringResource(R.string.setting_oboe_buffer_frames),
                value = oboeBufferFrames.toString(),
                onCommit = { viewModel.set(Prefs.OBOE_BUFFER_FRAMES, it.toInt()) },
                description = stringResource(R.string.setting_oboe_buffer_frames_desc),
                range = Prefs.OBOE_BUFFER_FRAMES.range
            )


            SettingSwitch(
                title = stringResource(R.string.setting_debug_overlay),
                description = stringResource(R.string.setting_debug_overlay_desc),
                checked = debugEnabled,
                onCheckedChange = { viewModel.set(Prefs.DEBUG_ENABLED, it) }
            )

            SettingSwitch(
                title = stringResource(R.string.setting_benchmark_log),
                description = stringResource(R.string.setting_benchmark_log_desc),
                checked = benchmarkLog,
                onCheckedChange = { viewModel.set(Prefs.BENCHMARK_LOG, it) }
            )
        }
        SectionHeader(stringResource(R.string.section_creator))
        CreatorCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
        Spacer(Modifier.height(24.dp))
    }
    }
    }
}

private fun canAutoLaunch(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Settings.canDrawOverlays(ctx)

// overlay settings screen may not exist on some builds
private fun _openOverlaySettings(ctx: Context) {
    val overlay = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
    try {
        ctx.startActivity(overlay.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        ctx.startActivity(ctx.appSettingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = io.github.mehdieskandari.airplaytv.ui.theme.TvColors.SecondaryLabel,
        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 28.dp, bottom = 8.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingResolution(
    value: String,
    onValueChange: (String) -> Unit
) {
    val presets = listOf(
        Prefs.AUTO to stringResource(R.string.chip_auto),
        "1280x720" to "1280x720",
        "1920x1080" to "1920x1080",
        "3840x2160" to "3840x2160"
    )
    val devicePresets = listOf(
        Prefs.PORTRAIT to stringResource(R.string.chip_device_portrait),
        Prefs.LANDSCAPE to stringResource(R.string.chip_device_landscape)
    )
    val isPreset = (presets + devicePresets).any { it.first == value }
    var editing by remember { mutableStateOf(false) }
    val parts = if (!isPreset && value.contains("x")) value.split("x", limit = 2) else listOf("", "")
    var width by remember(value) { mutableStateOf(if (isPreset) "" else parts[0]) }
    var height by remember(value) { mutableStateOf(if (isPreset) "" else parts.getOrElse(1) { "" }) }

    ListItem(
        headlineContent = { Text(stringResource(R.string.setting_resolution)) },
        supportingContent = {
            Column {
                Text(stringResource(R.string.setting_resolution_desc))
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    @Composable
                    fun presetChip(key: String, label: String) = FilterChip(
                        selected = value == key && !editing,
                        onClick = {
                            editing = false
                            width = ""; height = ""
                            onValueChange(key)
                        },
                        label = { Text(label) },
                        modifier = Modifier.dpadFocus()
                    )
                    presets.forEach { (key, label) -> presetChip(key, label) }
                    FilterChip(
                        selected = !isPreset || editing,
                        onClick = { editing = true },
                        label = { Text(stringResource(R.string.chip_custom)) },
                        modifier = Modifier.dpadFocus()
                    )
                    devicePresets.forEach { (key, label) -> presetChip(key, label) }
                }
                if (editing || !isPreset) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = width,
                            onValueChange = { width = it.filter { c -> c.isDigit() }.take(5) },
                            singleLine = true,
                            label = { Text(stringResource(R.string.setting_resolution_width)) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = height,
                            onValueChange = { height = it.filter { c -> c.isDigit() }.take(5) },
                            singleLine = true,
                            label = { Text(stringResource(R.string.setting_resolution_height)) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = doneAndHide {
                                val w = width.toIntOrNull()
                                val h = height.toIntOrNull()
                                if (w != null && w > 0 && h != null && h > 0) {
                                    onValueChange("${w}x${h}")
                                    editing = false
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun doneAndHide(commit: () -> Unit): KeyboardActions {
    val keyboard = LocalSoftwareKeyboardController.current
    return KeyboardActions(onDone = { commit(); keyboard?.hide() })
}

@Composable
private fun SettingTextField(
    label: String,
    value: String,
    onCommit: (String) -> Unit,
    description: String? = null,
    range: IntRange? = null
) {
    // tv: a row that opens an edit dialog, so focusing it doesn't pop up the keyboard
    if (isTv()) {
        SettingTextRow(label, value, onCommit, description, range)
        return
    }
    var text by remember(value) { mutableStateOf(value) }
    val focusManager = LocalFocusManager.current
    val valid = range == null || text.toIntOrNull()?.let { it in range } == true
    fun save() {
        if (valid && text != value) onCommit(text)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = if (range == null) it else it.filter { c -> c.isDigit() }.take(6) },
        label = { Text(label) },
        supportingText = description?.let { { Text(it) } },
        singleLine = true,
        isError = !valid,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (range == null) KeyboardType.Text else KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        keyboardActions = doneAndHide(::save),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
            .onFocusChanged { if (!it.isFocused) save() }
            // on a TV remote, up/down leave the field instead of moving the cursor
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionDown -> { save(); focusManager.moveFocus(FocusDirection.Down) }
                    Key.DirectionUp -> { save(); focusManager.moveFocus(FocusDirection.Up) }
                    else -> false
                }
            }
    )
}

@Composable
private fun SettingChipField(
    title: String,
    description: String,
    value: String,
    presets: List<Pair<String, String>>,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    val isPreset = presets.any { it.first == value }
    var editing by remember { mutableStateOf(false) }
    var text by remember(value) { mutableStateOf(if (isPreset) "" else value) }

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text(description)
                Spacer(Modifier.height(8.dp))
                ChipRow(if (editing) "" else value, presets, { editing = false; text = ""; onValueChange(it) }) {
                    FilterChip(
                        selected = !isPreset || editing,
                        onClick = { editing = true },
                        label = { Text(stringResource(R.string.chip_custom)) },
                        modifier = Modifier.dpadFocus()
                    )
                }
                if (editing || !isPreset) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        label = { Text(placeholder) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = keyboardType,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = doneAndHide {
                            if (text.isNotBlank()) {
                                onValueChange(text)
                                editing = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (key, label) ->
            FilterChip(
                selected = value == key,
                onClick = { onSelect(key) },
                label = { Text(label) },
                modifier = Modifier.dpadFocus()
            )
        }
        trailing()
    }
}

@Composable
private fun SettingChips(
    title: String,
    description: String,
    value: String,
    options: List<Pair<String, String>>,
    onValueChange: (String) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text(description)
                Spacer(Modifier.height(8.dp))
                ChipRow(value, options, onValueChange)
            }
        }
    )
}

@Composable
private fun <T> SettingSegmented(
    title: String,
    summary: String,
    value: T,
    options: List<Pair<T, String>>,
    onValueChange: (T) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { i, (key, label) ->
                        val shape = SegmentedButtonDefaults.itemShape(i, options.size)
                        SegmentedButton(
                            selected = value == key,
                            onClick = { onValueChange(key) },
                            shape = shape,
                            modifier = Modifier.dpadFocus(shape),
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(summary)
            }
        }
    )
}

// tvOS settings row: turns white with dark text when focused
@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val tv = io.github.mehdieskandari.airplaytv.ui.theme.TvColors
    val shape = RoundedCornerShape(14.dp)
    ListItem(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .onFocusChanged { focused = it.isFocused }
            .dpadFocus(shape, lift = 1.02f)
            .clip(shape)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        colors = ListItemDefaults.colors(
            containerColor = if (focused) tv.Focus else tv.Surface,
            headlineColor = if (focused) tv.OnFocus else tv.Label,
            supportingColor = if (focused) tv.OnFocus.copy(alpha = 0.8f) else tv.SecondaryLabel,
        ),
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(description) },
        trailingContent = {
            Switch(
                checked = checked, onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedTrackColor = tv.Blue, checkedThumbColor = Color.White)
            )
        }
    )
}

// subtitle look and timing; every option is a chip so it works with a TV remote
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubtitleSettings(viewModel: MainViewModel) {
    val style = rememberSubtitleStyle(viewModel)
    val delayMs by viewModel.pref(Prefs.SUB_DELAY_MS).collectAsState()

    SectionHeader(stringResource(R.string.section_subtitles))

    // live preview on a dark "movie" background
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(170.dp)
            .background(
                io.github.mehdieskandari.airplaytv.ui.theme.TvColors.SurfaceHigh,
                RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        SubtitleText(
            text = stringResource(R.string.subtitle_sample),
            style = style.copy(marginDp = 0),
            modifier = Modifier.padding(bottom = (style.marginDp / 4).coerceIn(8, 60).dp)
        )
    }

    SettingSwitch(
        title = stringResource(R.string.subtitle_show),
        description = stringResource(R.string.subtitle_show_desc),
        checked = style.enabled,
        onCheckedChange = { viewModel.set(Prefs.SUB_ENABLED, it) }
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.subtitle_font)) },
        supportingContent = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Prefs.SUB_FONTS.forEach { key ->
                    FilterChip(
                        selected = style.font == key,
                        onClick = { viewModel.set(Prefs.SUB_FONT, key) },
                        label = {
                            Text(
                                if (androidx.core.os.ConfigurationCompat.getLocales(LocalConfiguration.current)[0]?.language == "en") {
                                    if (key == "system") stringResource(R.string.font_system) else key.replaceFirstChar { it.uppercase() }
                                } else SubtitleFonts.labels[key] ?: key,
                                fontFamily = SubtitleFonts.family(key),
                                fontSize = 18.sp
                            )
                        },
                        modifier = Modifier.dpadFocus()
                    )
                }
            }
        }
    )

    SettingSwitch(
        title = stringResource(R.string.subtitle_bold),
        description = stringResource(R.string.subtitle_bold_desc),
        checked = style.bold,
        onCheckedChange = { viewModel.set(Prefs.SUB_BOLD, it) }
    )

    SettingChips(
        title = stringResource(R.string.subtitle_size),
        description = stringResource(R.string.subtitle_size_desc),
        value = style.sizeSp.toString(),
        options = listOf(20, 24, 28, 30, 32, 36, 42, 48, 56, 64).map { it.toString() to it.toString() },
        onValueChange = { viewModel.set(Prefs.SUB_SIZE, it.toInt()) }
    )

    val colors = listOf(
        "#FFFFFF" to stringResource(R.string.color_white),
        "#FFEB3B" to stringResource(R.string.color_yellow),
        "#FFF59D" to stringResource(R.string.color_light_yellow),
        "#80DEEA" to stringResource(R.string.color_cyan),
        "#A5D6A7" to stringResource(R.string.color_green),
        "#FFB74D" to stringResource(R.string.color_orange),
        "#F48FB1" to stringResource(R.string.color_pink),
        "#E0E0E0" to stringResource(R.string.color_gray),
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.subtitle_color)) },
        supportingContent = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                colors.forEach { (hex, label) ->
                    FilterChip(
                        selected = style.color == parseHexColor(hex),
                        onClick = { viewModel.set(Prefs.SUB_COLOR, hex) },
                        leadingIcon = {
                            Box(
                                Modifier
                                    .size(16.dp)
                                    .background(parseHexColor(hex), CircleShape)
                            )
                        },
                        label = { Text(label) },
                        modifier = Modifier.dpadFocus()
                    )
                }
            }
        }
    )

    SettingChips(
        title = stringResource(R.string.subtitle_edge),
        description = stringResource(R.string.subtitle_edge_desc),
        value = style.edge,
        options = listOf("outline" to stringResource(R.string.edge_outline), "shadow" to stringResource(R.string.edge_shadow), "none" to stringResource(R.string.option_none)),
        onValueChange = { viewModel.set(Prefs.SUB_EDGE, it) }
    )

    SettingChips(
        title = stringResource(R.string.subtitle_bg),
        description = stringResource(R.string.subtitle_bg_desc),
        value = style.bgPercent.toString(),
        options = listOf(0, 25, 35, 50, 70, 90).map { it to if (it == 0) stringResource(R.string.bg_none) else stringResource(R.string.volume_value, it) }
            .map { (v, l) -> v.toString() to l },
        onValueChange = { viewModel.set(Prefs.SUB_BG, it.toInt()) }
    )

    SettingChips(
        title = stringResource(R.string.subtitle_position),
        description = stringResource(R.string.subtitle_position_desc),
        value = style.marginDp.toString(),
        options = listOf(10 to stringResource(R.string.position_lowest), 40 to stringResource(R.string.position_low), 80 to stringResource(R.string.position_medium), 140 to stringResource(R.string.position_high), 220 to stringResource(R.string.position_highest))
            .map { (v, l) -> v.toString() to l },
        onValueChange = { viewModel.set(Prefs.SUB_MARGIN, it.toInt()) }
    )

    // timing: applies instantly while a video plays
    ListItem(
        headlineContent = { Text(stringResource(R.string.subtitle_timing)) },
        supportingContent = {
            Column(Modifier.padding(top = 8.dp)) {
                Text(
                    when {
                        delayMs == 0 -> stringResource(R.string.subtitle_no_delay)
                        delayMs > 0 -> stringResource(R.string.subtitle_later, delayMs / 1000f)
                        else -> stringResource(R.string.subtitle_earlier, -delayMs / 1000f)
                    }
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(-1000 to "−1s", -500 to "−0.5s", 500 to "+0.5s", 1000 to "+1s").forEach { (step, label) ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                viewModel.set(Prefs.SUB_DELAY_MS, (delayMs + step).coerceIn(Prefs.SUB_DELAY_MS.range))
                            },
                            label = { Text(label) },
                            modifier = Modifier.dpadFocus()
                        )
                    }
                    FilterChip(
                        selected = delayMs == 0,
                        onClick = { viewModel.set(Prefs.SUB_DELAY_MS, 0) },
                        label = { Text(stringResource(R.string.subtitle_reset_time)) },
                        modifier = Modifier.dpadFocus()
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.subtitle_timing_hint),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )

    SettingSwitch(
        title = stringResource(R.string.subtitle_reset),
        description = stringResource(R.string.subtitle_reset_desc),
        checked = false,
        onCheckedChange = {
            viewModel.set(Prefs.SUB_FONT, Prefs.SUB_FONT.def)
            viewModel.set(Prefs.SUB_BOLD, Prefs.SUB_BOLD.def)
            viewModel.set(Prefs.SUB_SIZE, Prefs.SUB_SIZE.def)
            viewModel.set(Prefs.SUB_COLOR, Prefs.SUB_COLOR.def)
            viewModel.set(Prefs.SUB_EDGE, Prefs.SUB_EDGE.def)
            viewModel.set(Prefs.SUB_BG, Prefs.SUB_BG.def)
            viewModel.set(Prefs.SUB_MARGIN, Prefs.SUB_MARGIN.def)
            viewModel.set(Prefs.SUB_DELAY_MS, Prefs.SUB_DELAY_MS.def)
        }
    )
}

// trusted devices: approve once on the TV, then they connect automatically
@Composable
private fun DeviceSettings(viewModel: MainViewModel) {
    val ctx = LocalContext.current
    val approve by viewModel.pref(Prefs.APPROVE_NEW_DEVICES).collectAsState()
    val devices by viewModel.trustedDevices.collectAsState()
    var canLaunch by remember { mutableStateOf(canAutoLaunch(ctx)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) canLaunch = canAutoLaunch(ctx) }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    SectionHeader(stringResource(R.string.section_devices))

    val dlnaEnabled by viewModel.pref(Prefs.DLNA_ENABLED).collectAsState()
    SettingSwitch(
        title = stringResource(R.string.dlna_enabled),
        description = stringResource(R.string.dlna_enabled_desc),
        checked = dlnaEnabled,
        onCheckedChange = { viewModel.set(Prefs.DLNA_ENABLED, it) }
    )

    SettingSwitch(
        title = stringResource(R.string.approve_devices),
        description = stringResource(R.string.approve_devices_desc),
        checked = approve,
        onCheckedChange = { viewModel.set(Prefs.APPROVE_NEW_DEVICES, it) }
    )

    if (approve && !canLaunch) {
        ListItem(
            modifier = Modifier
                .dpadFocus(RectangleShape)
                .clickable { _openOverlaySettings(ctx) },
            headlineContent = { Text(stringResource(R.string.overlay_allow)) },
            supportingContent = {
                Text(stringResource(R.string.overlay_desc))
            }
        )
    }

    if (approve) {
        if (devices.isEmpty()) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.no_trusted_devices)) },
                supportingContent = { Text(stringResource(R.string.trusted_devices_hint)) }
            )
        } else {
            devices.sortedByDescending { it.addedAt }.forEach { d ->
                ListItem(
                    headlineContent = { Text(d.name) },
                    supportingContent = { if (d.model.isNotEmpty()) Text(d.model) },
                    trailingContent = {
                        OutlinedButton(
                            onClick = { viewModel.forgetDevice(d.id) },
                            modifier = Modifier.dpadFocus()
                        ) { Text(stringResource(R.string.device_remove)) }
                    }
                )
            }
            ListItem(
                headlineContent = {
                    OutlinedButton(
                        onClick = { viewModel.forgetAllDevices() },
                        modifier = Modifier.dpadFocus()
                    ) { Text(stringResource(R.string.devices_forget)) }
                }
            )
        }
    }
}

@Composable
private fun LanguageSettings(viewModel: MainViewModel) {
    val language by viewModel.pref(Prefs.LANGUAGE).collectAsState()
    var open by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? MainActivity
    SectionHeader(stringResource(R.string.section_language))
    ListItem(
        headlineContent = { Text(when (language) { "en" -> "English"; "mul" -> "فارسی + English"; else -> "فارسی" }) },
        supportingContent = { Text(stringResource(R.string.language_description)) },
        leadingContent = { Icon(Icons.Default.Language, null) },
        modifier = Modifier.dpadFocus(RectangleShape).clickable { open = true }
    )
    if (open) Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LanguagePicker(initial = language, onConfirm = { activity?.chooseLanguage(it) })
    }
}

@Composable
private fun UpdateSettings(viewModel: MainViewModel) {
    val context = LocalContext.current
    val autoCheck by viewModel.pref(Prefs.AUTO_UPDATE_CHECK).collectAsState()
    val status by viewModel.updateStatus.collectAsState()
    val update by viewModel.update.collectAsState()
    SectionHeader(stringResource(R.string.section_updates))
    if (StoreLinks.isStoreBuild) {
        val store = stringResource(if (BuildConfig.STORE == "myket") R.string.store_myket else R.string.store_bazaar)
        ListItem(
            headlineContent = { Text(stringResource(R.string.version_current, viewModel.currentVersion)) },
            supportingContent = { Text(stringResource(R.string.store_updates_desc, store)) }
        )
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { StoreLinks.open(context, StoreLinks.Action.UPDATE) }, modifier = Modifier.dpadFocus()) {
                Text(stringResource(R.string.update_check))
            }
            OutlinedButton(onClick = { StoreLinks.open(context, StoreLinks.Action.DETAILS) }, modifier = Modifier.dpadFocus()) {
                Text(stringResource(R.string.store_open, store))
            }
            OutlinedButton(onClick = { StoreLinks.open(context, StoreLinks.Action.REVIEW) }, modifier = Modifier.dpadFocus()) {
                Text(stringResource(R.string.store_review))
            }
        }
    } else {
        SettingSwitch(
            title = stringResource(R.string.updates_auto),
            description = stringResource(R.string.updates_auto_desc),
            checked = autoCheck,
            onCheckedChange = { viewModel.set(Prefs.AUTO_UPDATE_CHECK, it) }
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.version_current, viewModel.currentVersion)) },
            supportingContent = { if (status.isNotEmpty()) Text(status) }
        )
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (update != null) Button(onClick = { viewModel.installUpdate() }, modifier = Modifier.dpadFocus()) {
                Text(stringResource(R.string.update_open))
            }
            OutlinedButton(onClick = { viewModel.checkForUpdate() }, modifier = Modifier.dpadFocus()) {
                Text(stringResource(R.string.update_check))
            }
        }
    }
}

@Composable
private fun SettingTextRow(
    label: String,
    value: String,
    onCommit: (String) -> Unit,
    description: String?,
    range: IntRange?
) {
    var editing by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val tv = io.github.mehdieskandari.airplaytv.ui.theme.TvColors
    val shape = RoundedCornerShape(14.dp)
    ListItem(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .onFocusChanged { focused = it.isFocused }
            .dpadFocus(shape, lift = 1.02f)
            .clip(shape)
            .clickable { editing = true },
        colors = ListItemDefaults.colors(
            containerColor = if (focused) tv.Focus else tv.Surface,
            headlineColor = if (focused) tv.OnFocus else tv.Label,
            supportingColor = if (focused) tv.OnFocus.copy(alpha = 0.8f) else tv.SecondaryLabel,
            trailingIconColor = if (focused) tv.OnFocus else tv.SecondaryLabel,
        ),
        headlineContent = { Text(label, style = MaterialTheme.typography.titleMedium) },
        supportingContent = description?.let { { Text(it) } },
        trailingContent = { Text(value, style = MaterialTheme.typography.titleMedium, color = if (focused) tv.OnFocus else tv.SecondaryLabel) }
    )
    if (editing) {
        var text by remember { mutableStateOf(value) }
        val valid = range == null || text.toIntOrNull()?.let { it in range } == true
        val fieldFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(label) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = if (range == null) it else it.filter { c -> c.isDigit() }.take(6) },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (range == null) KeyboardType.Text else KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        if (valid) { if (text != value) onCommit(text); editing = false }
                    }),
                    modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus)
                )
            },
            confirmButton = {
                Button(
                    onClick = { if (valid) { if (text != value) onCommit(text); editing = false } },
                    enabled = valid,
                    modifier = Modifier.dpadFocus(CircleShape)
                ) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }, modifier = Modifier.dpadFocus(CircleShape)) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}
