package com.duck.app.util

import java.io.File
import java.awt.Desktop

class DesktopPlatformBridge : PlatformBridge {
    override fun showToast(message: String) {
        println("TOAST: $message")
    }

    override fun openSavedFolder(path: String) {
        try {
            val dir = File(System.getProperty("user.home"), "Downloads")
            Desktop.getDesktop().open(dir)
        } catch(e: Exception) { e.printStackTrace() }
    }

    // A simple mock for settings on desktop for now
    private val settings = mutableMapOf<String, String>()
    override fun saveSetting(key: String, value: String) {
        settings[key] = value
    }

    override fun getSetting(key: String, defaultValue: String): String {
        return settings[key] ?: defaultValue
    }
}
