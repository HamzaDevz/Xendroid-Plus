package xendroid.compose.ui.companion

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import xendroid.compose.R
import xendroid.compose.companion.CompanionPadLink.State
import xendroid.compose.companion.CompanionPadLink.Why
import xendroid.compose.companion.CompanionProtocol
import xendroid.compose.gamepad.GamepadConfigDto
import xendroid.compose.gamepad.GamepadController
import xendroid.compose.gamepad.GamepadOverlay
import xendroid.compose.gamepad.Kc
import xendroid.compose.gamepad.OnScreenControl
import xendroid.compose.gamepad.RumbleIntensity
import xendroid.compose.ui.design.NoteTone
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdArea
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdButtonSize
import xendroid.compose.ui.design.XdCard
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdNote
import xendroid.compose.ui.design.XdSegmented
import xendroid.compose.ui.design.XdSingleScreen
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.design.controllerLeaves
import xendroid.compose.ui.rumbleLabel

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** "Use this phone as a controller": a form to join, then the touch pad, full screen. */
@Composable
fun PhoneControllerScreen(vm: PhoneControllerViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val playing = state as? State.Playing
    if (playing != null) {
        PhonePad(vm, playing)
        return
    }
    PhoneControllerForm(
        state = state,
        address = vm.address.value, onAddress = { vm.address.value = it.take(21) },
        code = vm.code.value, onCode = { text -> vm.code.value = text.filter(Char::isDigit).take(6) },
        name = vm.name.value, onName = { vm.name.value = it.take(32) },
        intensity = vm.intensity.value, onIntensity = vm::setIntensity,
        onConnect = vm::connect, onCancel = vm::leave,
        onBack = { vm.leave(); onBack() },
    )
}

/** The form: the game's address and code, the name shown there and this phone's vibration. */
@Composable
fun PhoneControllerForm(
    state: State,
    address: String, onAddress: (String) -> Unit,
    code: String, onCode: (String) -> Unit,
    name: String, onName: (String) -> Unit,
    intensity: RumbleIntensity, onIntensity: (RumbleIntensity) -> Unit,
    onConnect: () -> Unit, onCancel: () -> Unit, onBack: () -> Unit,
) {
    val c = Xd.colors
    val connecting = state is State.Connecting
    XdSingleScreen(
        title = stringResource(R.string.lib_menu_phone_controller),
        area = XdArea.CONTROLS,
        subtitle = stringResource(R.string.xd_pc_sub),
        onBack = onBack,
        headIcon = XdIcons.phone,
    ) {
        Column(Modifier.widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            XdNote(stringResource(R.string.xd_pc_intro), tone = NoteTone.INFO, icon = XdIcons.info)
            XdCard(Modifier.fillMaxWidth()) {
                Field(stringResource(R.string.pc_address)) {
                    Input(address, onAddress, "192.168.1.20:41234", !connecting, KeyboardType.Uri, mono = true)
                }
                Field(stringResource(R.string.pc_code)) {
                    Input(code, onCode, "000000", !connecting, KeyboardType.NumberPassword, mono = true, big = true)
                }
                Field(stringResource(R.string.pc_name)) {
                    Input(name, onName, "", !connecting, KeyboardType.Text, mono = false)
                }
                Field(stringResource(R.string.xd_pc_vibration)) {
                    XdSegmented(RumbleIntensity.entries.map { it to rumbleLabel(it) }, intensity, onIntensity, enabled = !connecting)
                }
                (state as? State.Idle)?.why?.let { XdNote(whyText(it), tone = NoteTone.ERROR, icon = XdIcons.warn) }
                if (connecting) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = c.acc, strokeWidth = 2.5.dp)
                        Text(stringResource(R.string.pc_connecting), style = XdText.label, color = c.fg, modifier = Modifier.weight(1f))
                        XdButton(stringResource(R.string.common_cancel), onCancel, kind = XdButtonKind.GHOST, size = XdButtonSize.SM)
                    }
                } else {
                    XdButton(stringResource(R.string.pc_connect), onConnect, Modifier.fillMaxWidth(), kind = XdButtonKind.PRIMARY, size = XdButtonSize.LG)
                }
            }
            XdNote(stringResource(R.string.pc_footer), icon = XdIcons.lock)
        }
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = XdText.labelSm, color = Xd.colors.fg2)
        content()
    }
}

@Composable
private fun Input(value: String, onValue: (String) -> Unit, placeholder: String, enabled: Boolean, type: KeyboardType, mono: Boolean, big: Boolean = false) {
    val c = Xd.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val style = when {
        big -> XdText.mono.copy(fontSize = 26.sp, letterSpacing = 0.3.em)
        mono -> XdText.mono.copy(fontSize = 15.sp)
        else -> XdText.body
    }
    Box(
        Modifier.fillMaxWidth().height(if (big) 58.dp else 44.dp).clip(shape).background(c.s3)
            .then(if (focused) Modifier.border(1.5.dp, c.acc, shape) else Modifier)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, style = style, color = c.fg3, maxLines = 1)
        BasicTextField(
            value = value, onValueChange = onValue, singleLine = true, enabled = enabled,
            textStyle = style.copy(color = if (enabled) c.fg else c.fg3), cursorBrush = SolidColor(c.acc),
            keyboardOptions = KeyboardOptions(keyboardType = type),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.controllerLeaves { value.isEmpty() },
        )
    }
}

/** Full-screen landscape pad; the game's buttons stay released while this phone is away. */
@Composable
private fun PhonePad(vm: PhoneControllerViewModel, playing: State.Playing) {
    val context = LocalContext.current
    val view = LocalView.current
    val controller = remember { GamepadController(context.applicationContext) }
    val config by controller.config.collectAsState(initial = GamepadConfigDto())
    val controls = remember(config) { controller.controlsFor(config, landscape = true) }

    BackHandler { vm.leave() }
    LaunchedEffect(Unit) {
        while (true) {
            vm.refresh()
            delay(1_000)
        }
    }
    DisposableEffect(view) {
        val activity = view.context.findActivity()
        val previousOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val insets = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            insets?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = previousOrientation
        }
    }
    PhonePadView(playing, controls, config.globals.opacity, onKey = { key, pressed, value ->
        if (pressed && value == Kc.VALUE_UNUSED && config.globals.hapticsEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        vm.key(key, pressed, value)
    }, onLeave = vm::leave)
}

/** The pad while playing: the touch controls over black, and who this phone plays as. */
@Composable
fun PhonePadView(playing: State.Playing, controls: List<OnScreenControl>, opacity: Float, onKey: (Int, Boolean, Int) -> Unit, onLeave: () -> Unit) {
    val c = Xd.colors
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        GamepadOverlay(
            controls = controls,
            // Nothing behind the pad here: keep it clearly visible.
            opacity = opacity.coerceAtLeast(0.6f),
            onKeyEvent = onKey,
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 10.dp).clip(RoundedCornerShape(50))
                .background(Color(0xE6161C19)).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
                .padding(start = 16.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("P${playing.slot + 1}", style = XdText.monoNum.copy(fontSize = 14.sp), color = c.acc)
            playing.latencyMs?.let { Text("$it ms", style = XdText.mono.copy(fontSize = 12.5.sp), color = c.fg2) }
            XdButton(stringResource(R.string.pc_leave), onLeave, kind = XdButtonKind.SECONDARY, size = XdButtonSize.SM, icon = XdIcons.exit)
        }
    }
}

/** U02: why this phone is not connected, in the shown language; the system's own words stay as they are. */
@Composable
private fun whyText(why: Why): String = when (why.kind) {
    Why.Kind.BAD_ADDRESS -> stringResource(R.string.pc_bad_address)
    Why.Kind.BAD_CODE -> stringResource(R.string.pc_bad_code)
    Why.Kind.REJECTED -> when (why.code) {
        CompanionProtocol.REJECT_PROOF -> stringResource(R.string.pc_rejected_code)
        CompanionProtocol.REJECT_FULL -> stringResource(R.string.pc_rejected_full)
        CompanionProtocol.REJECT_VERSION -> stringResource(R.string.pc_rejected_version)
        CompanionProtocol.REJECT_CLOSED -> stringResource(R.string.pc_rejected_closed)
        else -> stringResource(R.string.pc_rejected_other, why.code)
    }
    Why.Kind.UNREACHABLE -> stringResource(R.string.pc_unreachable, why.target)
    Why.Kind.NOT_XENDROID -> stringResource(R.string.pc_not_xendroid, why.target, why.detail)
    Why.Kind.FAILED -> stringResource(R.string.pc_failed, why.detail)
    Why.Kind.DISCONNECTED -> stringResource(R.string.pc_disconnected, why.detail)
}
