package xendroid.compose

import androidx.activity.OnBackPressedCallback
import xendroid.compose.ui.design.XdToastState

/**
 * A controller's Back with nothing left to close (the library, where the app starts) asks once
 * before leaving: B sits next to A, and one stray press used to close the app. [ask] is the
 * question for the button pressed, shown as a toast; a second press while that toast is on screen
 * calls [leave]. Added to the dispatcher before anything else, so every screen's own Back comes
 * first, and on only while [arm]ed: the phone's back gesture and button leave at once, as before.
 */
class ExitGuard(
    private val toast: XdToastState,
    private val ask: (button: String) -> String,
    private val leave: () -> Unit,
) : OnBackPressedCallback(false) {
    private var button = "B"
    // The toast that asked for a second press, while it is on screen.
    private var asked: Long? = null

    /** A controller's Back from [button] ("A" or "B") is on its way. */
    fun arm(button: String) {
        this.button = button
        isEnabled = true
    }

    fun disarm() {
        isEnabled = false
    }

    override fun handleOnBackPressed() {
        val id = asked
        if (id == null || toast.current?.id != id) {
            toast.show(ask(button))
            asked = toast.current?.id
            return
        }
        asked = null
        isEnabled = false
        leave()
    }
}
