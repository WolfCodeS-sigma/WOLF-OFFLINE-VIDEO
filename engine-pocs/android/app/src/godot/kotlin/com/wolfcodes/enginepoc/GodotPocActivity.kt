package com.wolfcodes.enginepoc

import android.content.pm.ActivityInfo
import android.os.Bundle
import org.godotengine.godot.GodotActivity

/** Isolated host for a Godot project stored in this flavor's APK assets. */
class GodotPocActivity : GodotActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        super.onCreate(savedInstanceState)
    }
}
