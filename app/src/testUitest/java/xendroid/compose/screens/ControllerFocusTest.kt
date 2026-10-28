package xendroid.compose.screens

import android.view.InputDevice
import android.view.KeyEvent as NativeKeyEvent
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xendroid.compose.AppContainer
import xendroid.compose.ExitGuard
import xendroid.compose.R
import xendroid.compose.shots.Fixture
import xendroid.compose.shots.Phone
import xendroid.compose.shots.SampleLibrary
import xendroid.compose.shots.ShotApp
import xendroid.compose.shots.app
import xendroid.compose.ui.compress.GameCompressViewModel
import xendroid.compose.ui.design.CFrame
import xendroid.compose.ui.design.InputMode
import xendroid.compose.ui.design.LocalInputMode
import xendroid.compose.ui.design.LocalXdToast
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdSearchField
import xendroid.compose.ui.design.XdSheet
import xendroid.compose.ui.design.XdTextInput
import xendroid.compose.ui.design.XdToastState
import xendroid.compose.ui.library.GameLibraryScreen
import xendroid.compose.ui.theme.xendroidTheme

/**
 * A controller drives the focus: in the controller layout, and in the touch layout too (a
 * controller the app does not take for one, or touch chosen in Settings), where the screen leaves
 * touch mode. A text field lets the D-pad and B out, the library's first press lands on a cover,
 * and a sheet's first stop is one of its controls; a finger still focuses a sheet's field.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = ShotApp::class, qualifiers = Phone.PORT)
class ControllerFocusTest {
    @get:Rule val compose = createComposeRule()

    /** Each window (the screen's, a sheet's): its Compose view and input mode, registered by [Keys]. */
    private val windows = mutableListOf<Pair<View, InputModeManager>>()

    @Composable
    private fun Keys() {
        val window = LocalView.current to LocalInputModeManager.current
        DisposableEffect(window) {
            windows += window
            onDispose { windows -= window }
        }
    }

    /**
     * A controller's first press: on a phone the D-pad takes the screen out of touch mode and,
     * with nothing focused, gives the first focus downwards; that press does nothing else.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun controller() {
        compose.runOnIdle {
            windows.toList().forEach { (view, mode) ->
                mode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard)
                if (!view.hasFocus()) view.requestFocus(View.FOCUS_DOWN)
            }
        }
        compose.waitForIdle()
    }

    /** Down and up of a key as a controller sends it: a hat D-pad's keys come from the joystick,
     *  A and B (or the D-pad's centre and Back, as the app turns them) from the gamepad. True if
     *  taken. */
    private fun SemanticsNodeInteraction.press(code: Int, source: Int): Boolean {
        fun key(action: Int) = KeyEvent(NativeKeyEvent(0L, 0L, action, code, 0, 0, 7, 0, 0, source))
        val down = performKeyPress(key(NativeKeyEvent.ACTION_DOWN))
        val up = performKeyPress(key(NativeKeyEvent.ACTION_UP))
        compose.waitForIdle()
        return down || up
    }

    private val field get() = compose.onNode(hasSetTextAction())

    /** What has the focus, for the failure messages. */
    private fun focused(): String = compose.onAllNodes(isFocused()).fetchSemanticsNodes()
        .joinToString(" | ") { it.config.toString() }.ifEmpty { "nothing" }

    /** The touch layout (a controller the app does not take for one, or touch chosen). */
    private fun touchLayout(content: @Composable () -> Unit) {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
        compose.setContent {
            CompositionLocalProvider(LocalInputMode provides InputMode.TOUCH, LocalXdToast provides XdToastState()) {
                xendroidTheme(mode = InputMode.TOUCH) { Keys(); content() }
            }
        }
        compose.waitForIdle()
    }

    /** A search field with a button beside it and one below; a tap put the focus in the field,
     *  then the controller came in. */
    private fun fieldScreen(initial: String = "") {
        touchLayout {
            var query by remember { mutableStateOf(initial) }
            Column {
                Row {
                    XdSearchField(query, { query = it }, "Search", Modifier.width(240.dp))
                    XdButton("Beside", {})
                }
                XdButton("Below", {})
            }
        }
        field.performClick()
        controller()
        field.assertIsFocused()
    }

    @Test fun theDpadLeavesASearchField() {
        fieldScreen()
        field.press(NativeKeyEvent.KEYCODE_DPAD_DOWN, InputDevice.SOURCE_JOYSTICK)
        compose.onNodeWithText("Below").assertIsFocused()
    }

    @Test fun leftAndRightLeaveOnlyAnEmptyField() {
        fieldScreen("halo")
        field.press(NativeKeyEvent.KEYCODE_DPAD_RIGHT, InputDevice.SOURCE_JOYSTICK)
        field.assertIsFocused()
        field.performTextClearance()
        field.press(NativeKeyEvent.KEYCODE_DPAD_RIGHT, InputDevice.SOURCE_JOYSTICK)
        compose.onNodeWithText("Beside").assertIsFocused()
    }

    @Test fun aControllersBLeavesTheFieldOnly() {
        fieldScreen()
        assertTrue("B is taken by the field", field.press(NativeKeyEvent.KEYCODE_BACK, InputDevice.SOURCE_GAMEPAD))
        field.assertIsNotFocused()
        compose.onNodeWithText("Below").assertIsFocused()
    }

    @Test fun thePhonesBackIsLeftAlone() {
        fieldScreen()
        assertFalse("the phone's Back goes on", field.press(NativeKeyEvent.KEYCODE_BACK, InputDevice.SOURCE_KEYBOARD))
        field.assertIsFocused()
    }

    private fun library() {
        val container = AppContainer(Fixture.context)
        val vm = Fixture.library(container)
        val compress = container.gameCompressViewModelFactory().create(GameCompressViewModel::class.java)
        touchLayout {
            GameLibraryScreen(vm, {}, {}, {}, {}, {}, { _, _, _, _ -> }, { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, {}, compress)
        }
        Fixture.settle()
        compose.waitForIdle()
    }

    private val names = SampleLibrary.games.map { it.name }

    /** The games whose cover has the focus. */
    private fun focusedCovers(): List<String> =
        compose.onAllNodes(isFocused()).fetchSemanticsNodes().flatMap { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().filter { it in names }
        }.distinct()

    /** A game's card in the grid (the panel's cover of the chosen game takes no click). */
    private fun card(name: String) = SemanticsMatcher("card of $name") { node ->
        SemanticsActions.OnClick in node.config &&
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().contains(name)
    }

    /** The games of the grid's cards on screen, in reading order. */
    private fun cardsOnScreen(): List<String> =
        compose.onAllNodes(SemanticsMatcher("a card") { node ->
            SemanticsActions.OnClick in node.config &&
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it in names }
        }).fetchSemanticsNodes()
            .sortedWith(compareBy({ it.boundsInRoot.top }, { it.boundsInRoot.left }))
            .map { node -> node.config[SemanticsProperties.ContentDescription].first { it in names } }

    /** Portrait: the search field is the top-left control, where the first focus went. */
    @Test fun theLibrarysFirstPressLandsOnACover() {
        library()
        controller()
        field.assertIsNotFocused()
        assertTrue("a cover has the focus, not ${focused()}", focusedCovers().isNotEmpty())
    }

    /** Landscape: the chosen game (its panel open beside the covers) is the first stop, also
     *  after the pick went back to an earlier card. */
    @Config(qualifiers = Phone.LAND)
    @Test fun theLibrarysFirstPressLandsOnTheChosenGame() {
        library()
        val cards = cardsOnScreen()
        assertTrue("three cards on screen: $cards", cards.size >= 3)
        compose.onNode(card(cards[2])).performClick()
        compose.waitForIdle()
        compose.onNode(card(cards[1])).performClick()
        compose.waitForIdle()
        controller()
        assertEquals("the focus on ${focused()}", listOf(cards[1]), focusedCovers())
    }

    /** A controller's B with nothing left to close asks first; the phone's Back and a screen's own
     *  Back are not held up. */
    @Test fun aControllersBAtTheLibraryAsksBeforeLeaving() {
        var left = 0
        var screen = 0
        val toast = XdToastState()
        val dispatcher = OnBackPressedDispatcher(Runnable { left++ })
        val guard = ExitGuard(toast, { "press $it again" }) { dispatcher.onBackPressed() }
        dispatcher.addCallback(guard)
        fun padBack(button: String = "B") {
            guard.arm(button)
            try { dispatcher.onBackPressed() } finally { guard.disarm() }
        }
        padBack()
        assertEquals("press B again", toast.current?.message)
        assertEquals(0, left)
        padBack()
        assertEquals("the second press leaves", 1, left)
        dispatcher.onBackPressed()
        assertEquals("the phone's Back leaves at once", 2, left)
        padBack("A")
        assertEquals("press A again", toast.current?.message)
        toast.dismiss()
        padBack()
        assertEquals("the question gone, it is asked again", 2, left)
        val screenBack = object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { screen++ } }
        dispatcher.addCallback(screenBack)
        toast.dismiss()
        padBack()
        assertEquals("a screen's own Back comes first", 1, screen)
        assertEquals(null, toast.current)
        assertEquals(2, left)
    }

    /** The touch layout's sheets cancelled every focus entry, a tap's on a field too. (Whether
     *  anything has the focus as a sheet opens is not checked: a phone opens it by touch in touch
     *  mode, where Android gives no first focus, but here a sheet's window gives it before it
     *  takes the touch mode.) */
    @Test fun aTapFocusesASheetsField() {
        compose.app(InputMode.TOUCH) {
            var name by remember { mutableStateOf("") }
            XdSheet(onDismiss = {}, title = "Sheet") {
                XdButton("One", {})
                XdTextInput(name, { name = it }, placeholder = "Name")
            }
        }
        field.performClick()
        compose.waitForIdle()
        field.assertIsFocused()
    }

    @Test fun theDpadGetsIntoATouchSheet() {
        touchLayout {
            XdSheet(onDismiss = {}, title = "Sheet") {
                Keys()
                XdButton("One", {})
                XdButton("Two", {})
            }
        }
        Thread.sleep(250)
        compose.waitForIdle()
        controller()
        compose.onNodeWithText("One").press(NativeKeyEvent.KEYCODE_DPAD_DOWN, InputDevice.SOURCE_JOYSTICK)
        val names = compose.onAllNodes(isFocused()).fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { t -> t.text } }
        assertTrue("a control of the sheet has the focus, not ${focused()}", names.any { it == "One" || it == "Two" })
    }

    @Test fun aControllerSheetOpensOnItsFirstControl() {
        compose.app(InputMode.CONTROLLER) {
            XdSheet(onDismiss = {}, title = "Sheet") {
                XdButton("One", {})
                XdButton("Two", {})
            }
        }
        assertTrue("One has the focus, not ${focused()}",
            compose.onAllNodes(isFocused()).fetchSemanticsNodes().any { n ->
                n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "One" }
            })
    }

    /** In a sheet, a window of its own, A and B come as they are, not as Enter and Back. */
    @Test fun aControllersBLeavesASheetsFieldAndAShowsTheKeyboard() {
        compose.app(InputMode.CONTROLLER) {
            var name by remember { mutableStateOf("") }
            XdSheet(onDismiss = {}, title = "Sheet") {
                XdTextInput(name, { name = it }, placeholder = "Name")
                XdButton("One", {})
            }
        }
        field.assertIsFocused()
        assertTrue("A is taken by the field", field.press(NativeKeyEvent.KEYCODE_BUTTON_A, InputDevice.SOURCE_GAMEPAD))
        field.assertIsFocused()
        assertTrue("B is taken by the field", field.press(NativeKeyEvent.KEYCODE_BUTTON_B, InputDevice.SOURCE_GAMEPAD))
        field.assertIsNotFocused()
        compose.onNodeWithText("One").assertIsFocused()
    }

    /** The Start menu: its first item has the focus, and B closes the menu at once. */
    @Test fun bInTheStartMenuIsLeftForTheMenu() {
        compose.app(InputMode.CONTROLLER) {
            CFrame(art = null, hints = emptyList()) { openGuide -> LaunchedEffect(Unit) { openGuide() } }
        }
        compose.waitForIdle()
        val first = compose.onNodeWithText(Fixture.context.getString(R.string.xd_guide_library))
        first.assertIsFocused()
        assertFalse("B goes on to the menu, focus on ${focused()}", first.press(NativeKeyEvent.KEYCODE_BACK, InputDevice.SOURCE_GAMEPAD))
    }

    @Test fun bInASheetIsLeftForTheSheet() {
        compose.app(InputMode.CONTROLLER) {
            XdSheet(onDismiss = {}, title = "Sheet") {
                XdButton("One", {})
                XdButton("Two", {})
            }
        }
        // Not taken on the way (a focus move to something around the controls): the sheet's own
        // Back closes it.
        assertFalse("B goes on to the sheet, focus on ${focused()}",
            compose.onNodeWithText("One").press(NativeKeyEvent.KEYCODE_BACK, InputDevice.SOURCE_GAMEPAD))
    }
}
