package com.duck.app.ui.page

import androidx.compose.runtime.Composable

/**
 * Placeholder for the former in-app self-updater.
 *
 * Duck does not ship its own update channel: releases are distributed by whoever builds the project,
 * so there is nothing to poll here. The Composable is kept so that call sites do not need to change.
 */
@Composable
fun AppUpdater() {
    // intentionally empty
}
