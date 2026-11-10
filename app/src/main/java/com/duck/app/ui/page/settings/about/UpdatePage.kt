package com.duck.app.ui.page.settings.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.duck.app.App
import com.duck.app.R
import com.duck.app.ui.common.intState
import com.duck.app.ui.component.BackButton
import com.duck.app.ui.component.PreferenceInfo
import com.duck.app.ui.component.PreferenceSingleChoiceItem
import com.duck.app.ui.component.PreferenceSubtitle
import com.duck.app.ui.component.PreferenceSwitchWithContainer
import com.duck.app.util.AUTO_UPDATE
import com.duck.app.util.PRE_RELEASE
import com.duck.app.util.PreferenceUtil
import com.duck.app.util.PreferenceUtil.updateBoolean
import com.duck.app.util.PreferenceUtil.updateInt
import com.duck.app.util.UpdateUtil
import com.duck.app.util.STABLE
import com.duck.app.util.ToastUtil
import com.duck.app.util.UPDATE_CHANNEL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatePage(onNavigateBack: () -> Unit) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState(),
            canScroll = { true },
        )
    var autoUpdate by remember { mutableStateOf(PreferenceUtil.isAutoUpdateEnabled()) }
    var updateChannel by UPDATE_CHANNEL.intState
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showUnavailableDialog by remember { mutableStateOf(App.isFDroidBuild()) }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(modifier = Modifier, text = stringResource(id = R.string.auto_update))
                },
                navigationIcon = { BackButton { onNavigateBack() } },
                scrollBehavior = scrollBehavior,
            )
        },
        content = { paddings ->
            LazyColumn(modifier = Modifier.padding(paddings)) {
                item {
                    PreferenceSwitchWithContainer(
                        title = stringResource(id = R.string.enable_auto_update),
                        icon = null,
                        isChecked = autoUpdate,
                    ) {
                        autoUpdate = !autoUpdate
                        AUTO_UPDATE.updateBoolean(autoUpdate)
                    }
                }
                item {
                    PreferenceSubtitle(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        text = stringResource(id = R.string.update_channel),
                    )
                }
                item {
                    PreferenceSingleChoiceItem(
                        text = stringResource(id = R.string.stable_channel),
                        selected = updateChannel == STABLE,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        updateChannel = STABLE
                        UPDATE_CHANNEL.updateInt(updateChannel)
                    }
                }

                item {
                    PreferenceSingleChoiceItem(
                        text = stringResource(id = R.string.pre_release_channel),
                        selected = updateChannel == PRE_RELEASE,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        updateChannel = PRE_RELEASE
                        UPDATE_CHANNEL.updateInt(updateChannel)
                    }
                }
                item {
                    var isUpdatingYtDlp by remember { mutableStateOf(false) }
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ProgressIndicatorButton(
                            modifier =
                                Modifier.padding(horizontal = 24.dp)
                                    .padding(top = 6.dp)
                                    .padding(bottom = 12.dp),
                            text = stringResource(id = R.string.ytdlp_update_action),
                            icon = Icons.Outlined.Update,
                            isLoading = isUpdatingYtDlp,
                        ) {
                            if (!isUpdatingYtDlp)
                                scope.launch {
                                    isUpdatingYtDlp = true
                                    runCatching {
                                            withContext(Dispatchers.IO) {
                                                UpdateUtil.updateYtDlp()
                                            }
                                            ToastUtil.makeToastSuspend(
                                                context.getString(R.string.ytdlp_update_done)
                                            )
                                        }
                                        .onFailure {
                                            it.printStackTrace()
                                            ToastUtil.makeToastSuspend(
                                                context.getString(R.string.app_update_failed)
                                            )
                                        }
                                    isUpdatingYtDlp = false
                                }
                        }
                    }
                    androidx.compose.material3.HorizontalDivider()
                }
                item {
                    PreferenceInfo(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        text = stringResource(id = R.string.update_channel_desc),
                    )
                }
            }
        },
    )
    if (showUnavailableDialog) {
        AutoUpdateUnavailableDialog {
            showUnavailableDialog = false
            onNavigateBack()
        }
    }
}

@Composable
fun ProgressIndicatorButton(
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        modifier = modifier,
        onClick = onClick,
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        if (isLoading)
            Box(modifier = Modifier.size(18.dp)) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp).align(Alignment.Center),
                    strokeWidth = 3.dp,
                )
            }
        else Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(text = text, modifier = Modifier.padding(start = 8.dp))
    }
}
