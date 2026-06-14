package com.xayah.feature.setup.page.one

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.compose.material3.ExperimentalMaterial3Api

import com.xayah.core.common.util.BuildConfigUtil
import com.xayah.core.ui.viewmodel.BaseViewModel
import com.xayah.core.ui.viewmodel.IndexUiEffect
import com.xayah.core.ui.viewmodel.UiIntent
import com.xayah.core.ui.viewmodel.UiState
import com.xayah.core.util.NotificationUtil
import com.xayah.core.util.adb.AdbService
import com.xayah.core.util.command.BaseUtil
import com.xayah.feature.setup.EnvState
import com.xayah.feature.setup.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class IndexUiState(
    val abiErr: String
) : UiState

sealed class IndexUiIntent : UiIntent {
    data object ValidateRoot : IndexUiIntent()
    data object ValidateAbi : IndexUiIntent()
    data object ValidateAdb : IndexUiIntent()
    data object OnResume : IndexUiIntent()
    data class ValidateNotification(val context: Context) : IndexUiIntent()
}

@ExperimentalMaterial3Api
@HiltViewModel
class IndexViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : BaseViewModel<IndexUiState, IndexUiIntent, IndexUiEffect>(IndexUiState(abiErr = "")) {
    @SuppressLint("StringFormatInvalid")
    override suspend fun onEvent(state: IndexUiState, intent: IndexUiIntent) {
        when (intent) {
            is IndexUiIntent.OnResume -> {
                mutex.withLock {
                    val isNotificationPermissionGranted = NotificationUtil.checkPermission(context)
                    if (isNotificationPermissionGranted) {
                        _notificationState.value = EnvState.Succeed
                    }
                }
            }

            is IndexUiIntent.ValidateRoot -> {
                // ROOT mode is no longer supported, only ADB mode
            }

            is IndexUiIntent.ValidateAdb -> {
                mutex.withLock {
                    if (adbState.value == EnvState.Idle || adbState.value == EnvState.Failed) {
                        _adbState.value = EnvState.Processing
                        // 遵循 Shizuku 官方权限申请流程：
                        // 1. 先检查是否已有权限
                        // 2. 如果 Binder 存活但无权限，请求权限
                        // 3. 请求权限后通过 AdbService.permissionGranted 回调更新状态
                        val available = AdbService.isAvailable()
                        if (available) {
                            _adbState.value = EnvState.Succeed
                        } else if (AdbService.isBinderAlive()) {
                            // Binder 存活但权限未授予，请求权限
                            AdbService.requestPermission()
                            // 等待权限回调，短暂延迟后再次检查
                            kotlinx.coroutines.delay(1000)
                            if (AdbService.isAvailable()) {
                                _adbState.value = EnvState.Succeed
                            } else {
                                _adbState.value = EnvState.Failed
                            }
                        } else {
                            // Shizuku 未启动或未安装
                            _adbState.value = EnvState.Failed
                        }
                    }
                }
            }

            is IndexUiIntent.ValidateAbi -> {
                mutex.withLock {
                    if (abiState.value == EnvState.Idle || abiState.value == EnvState.Failed) {
                        _abiState.value = EnvState.Processing
                        val buildABI = BuildConfigUtil.FLAVOR_abi
                        val deviceABI = Build.SUPPORTED_ABIS.firstOrNull().toString()
                        if (buildABI == deviceABI) {
                            _abiState.value = if (runCatching { BaseUtil.releaseBase(context = context) }.getOrElse { false }) {
                                emitState(state.copy(abiErr = ""))
                                EnvState.Succeed
                            } else {
                                EnvState.Failed
                            }
                        } else {
                            _abiState.value = EnvState.Failed
                            emitState(
                                state.copy(
                                    abiErr = context.getString(
                                        R.string.this_version_only_supports_but_your_device_is_please_install_version,
                                        buildABI,
                                        deviceABI,
                                        deviceABI
                                    )
                                )
                            )
                        }
                    }
                }
            }

            is IndexUiIntent.ValidateNotification -> {
                mutex.withLock {
                    if (notificationState.value != EnvState.Succeed)
                        NotificationUtil.requestPermissions(intent.context)
                }
            }
        }
    }

    private val mutex = Mutex()

    private val _rootState: MutableStateFlow<EnvState> = MutableStateFlow(EnvState.Idle)
    val rootState: StateFlow<EnvState> = _rootState.stateInScope(EnvState.Idle)
    private val _adbState: MutableStateFlow<EnvState> = MutableStateFlow(EnvState.Idle)
    val adbState: StateFlow<EnvState> = _adbState.stateInScope(EnvState.Idle)
    private val _abiState: MutableStateFlow<EnvState> = MutableStateFlow(EnvState.Idle)
    val abiState: StateFlow<EnvState> = _abiState.stateInScope(EnvState.Idle)
    private val _notificationState: MutableStateFlow<EnvState> = MutableStateFlow(EnvState.Idle)
    val notificationState: StateFlow<EnvState> = _notificationState.stateInScope(EnvState.Idle)

    // Root 或 ADB 至少通过一个 + ABI 通过即可继续
    val allRequiredValidated: StateFlow<Boolean> = combine(_rootState, _adbState, _abiState) { root, adb, abi ->
        (root == EnvState.Succeed || adb == EnvState.Succeed) && abi == EnvState.Succeed
    }.flowOnIO().stateInScope(false)

    val allOptionalValidated: StateFlow<Boolean> = _notificationState.map { notification -> notification == EnvState.Succeed }.flowOnIO().stateInScope(false)
}
