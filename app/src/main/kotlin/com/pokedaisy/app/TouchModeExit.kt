package com.pokedaisy.app

/** Takes [activity]'s content out of touch mode, so a Compose focus request made now takes (FocusJump: a D-pad press
 * after a tap). ui-preview has its own no-op. */
fun leaveTouchMode(activity: androidx.activity.ComponentActivity) {
    activity.window.decorView.findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)?.requestFocusFromTouch()
}
