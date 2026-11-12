package com.duck.app.di

import org.koin.core.module.Module
import org.koin.dsl.module
import com.duck.app.util.PlatformBridge
import com.duck.app.util.AndroidPlatformBridge

actual fun platformModule(): Module = module {
    single<PlatformBridge> { AndroidPlatformBridge(get()) }
}
