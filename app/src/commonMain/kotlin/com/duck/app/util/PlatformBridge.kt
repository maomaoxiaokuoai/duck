package com.duck.app.util

interface PlatformBridge {
    fun showToast(message: String)
    fun openSavedFolder(path: String)
    fun saveSetting(key: String, value: String)
    fun getSetting(key: String, defaultValue: String): String
}
