package com.duck.app

import android.os.Bundle
import android.content.Intent
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.duck.app.ui.DownloaderScreen
import com.duck.app.ui.DownloaderViewModel
import com.duck.app.ui.theme.ExpressiveTheme
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        var keepSplash = true
        lifecycleScope.launch {
            delay(1000L) // Wait 1 second for animation to finish
            keepSplash = false
        }
        splashScreen.setKeepOnScreenCondition { keepSplash }
        super.onCreate(savedInstanceState)

        // The app ships Chinese copy as its primary language: pin the locale so a
        // fresh install opens in Chinese regardless of the device setting. Users can
        // still switch languages later from 个性化 -> Language.
        if (!hasChosenLanguage()) {
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                androidx.core.os.LocaleListCompat.forLanguageTags("zh-CN")
            )
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(this@MainActivity))
                val py = Python.getInstance()
                val customPath = java.io.File(filesDir, "python_packages").absolutePath
                py.getModule("sys").get("path")?.callAttr("insert", 0, customPath)
            }
        }
        
        org.schabi.newpipe.extractor.NewPipe.init(com.duck.app.api.OkHttpDownloader())

        val imageLoader = coil.ImageLoader.Builder(this)
            .components {
                add(coil.decode.VideoFrameDecoder.Factory())
            }
            .build()
        coil.Coil.setImageLoader(imageLoader)

        enableEdgeToEdge()
        setContent {
            val viewModel: DownloaderViewModel = viewModel()
            
            // Handle shared intent on start
            LaunchedEffect(intent) {
                handleIntent(intent, viewModel)
            }


            val darkTheme = when (viewModel.themeMode.intValue) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            ExpressiveTheme(
                darkTheme = darkTheme,
                theme = viewModel.selectedTheme.value
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!viewModel.isOnboardingCompleted.value) {
                        com.duck.app.ui.OnboardingScreen(
                            onComplete = { viewModel.setOnboardingCompleted() }
                        )
                    } else {
                        DownloaderScreen(viewModel)
                    }
                }
            }
        }
    }

    private fun handleIntent(intent: Intent?, viewModel: DownloaderViewModel) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText ->
                val urlRegex = "(https?://[^\\s]+)".toRegex()
                val match = urlRegex.find(sharedText)
                val extractedUrl = match?.value ?: sharedText
                viewModel.handleSharedUrl(extractedUrl)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    /** True once the user has picked a language; before that we default to Chinese. */
    private fun hasChosenLanguage(): Boolean =
        getSharedPreferences("duck_prefs", MODE_PRIVATE)
            .getBoolean("language_chosen", false)
}
