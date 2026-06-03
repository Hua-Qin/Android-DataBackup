package com.xayah.feature.main.directory

import android.app.Activity
import android.content.Context
import androidx.compose.material3.ExperimentalMaterial3Api
import com.xayah.core.data.repository.DirectoryRepository
import com.xayah.core.datastore.readPermissionMode
import com.xayah.core.model.PermissionMode
import com.xayah.core.model.StorageType
import com.xayah.core.model.database.DirectoryEntity
import com.xayah.core.rootservice.service.RemoteRootService
import com.xayah.core.ui.viewmodel.BaseViewModel
import com.xayah.core.ui.viewmodel.IndexUiEffect
import com.xayah.core.ui.viewmodel.UiIntent
import com.xayah.core.ui.viewmodel.UiState
import com.xayah.core.util.adb.AdbService
import com.xayah.libpickyou.PickYouLauncher
import com.xayah.libpickyou.parcelables.DirChildrenParcelable
import com.xayah.libpickyou.parcelables.FileParcelable
import com.xayah.libpickyou.ui.model.PermissionType
import com.xayah.libpickyou.ui.model.PickerType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class IndexUiState(
    val updating: Boolean,
) : UiState

sealed class IndexUiIntent : UiIntent {
    data object Update : IndexUiIntent()
    data class Select(val entity: DirectoryEntity) : IndexUiIntent()
    data class Add(val context: Activity) : IndexUiIntent()
    data class Delete(val entity: DirectoryEntity) : IndexUiIntent()
}

@ExperimentalMaterial3Api
@HiltViewModel
class IndexViewModel @Inject constructor(
    rootService: RemoteRootService,
    private val directoryRepo: DirectoryRepository,
    @ApplicationContext private val context: Context,
) : BaseViewModel<IndexUiState, IndexUiIntent, IndexUiEffect>(IndexUiState(updating = true)) {
    init {
        rootService.onFailure = {
            val msg = it.message
            if (msg != null)
                emitEffectOnIO(IndexUiEffect.ShowSnackbar(message = msg))
        }
    }

    override suspend fun onEvent(state: IndexUiState, intent: IndexUiIntent) {
        when (intent) {
            is IndexUiIntent.Update -> {
                emitState(uiState.value.copy(updating = true))
                directoryRepo.update()
                emitState(uiState.value.copy(updating = false))
            }

            is IndexUiIntent.Select -> {
                directoryRepo.selectDir(entity = intent.entity)
            }

            is IndexUiIntent.Add -> {
                withMainContext {
                    val activity = intent.context
                    val permissionMode = context.readPermissionMode().first()
                    val isAdb = permissionMode == PermissionMode.ADB

                    PickYouLauncher(
                        // ADB 模式下禁用库的权限检查：使用 Shizuku 访问文件，不需要 MANAGE_EXTERNAL_STORAGE
                        // 避免库内部检查 Environment.isExternalStorageManager() 导致权限弹窗阻塞
                        checkPermission = !isAdb,
                        title = activity.getString(R.string.select_target_directory),
                        pickerType = PickerType.DIRECTORY,
                        permissionType = if (isAdb) PermissionType.NORMAL else PermissionType.ROOT,
                        traverseBackend = if (isAdb) { pathString ->
                            // ADB 模式下使用 AdbService.traverseDirectory 遍历目录
                            // 单次 ls -1F 调用完成遍历，避免逐个 test -d 的 N+1 问题
                            val entries = AdbService.traverseDirectory(pathString)
                            val files = mutableListOf<FileParcelable>()
                            val directories = mutableListOf<FileParcelable>()
                            for (entry in entries) {
                                if (entry.isDirectory) {
                                    directories.add(FileParcelable(entry.name, 0))
                                } else {
                                    files.add(FileParcelable(entry.name, 0))
                                }
                            }
                            DirChildrenParcelable(files = files, directories = directories)
                        } else null,
                        mkdirsBackend = if (isAdb) { parent, child ->
                            // ADB 模式下使用 AdbService 创建目录
                            AdbService.mkdirs("$parent/$child")
                        } else null,
                    ).apply {
                        launch(activity) { pathString ->
                            launchOnIO {
                                directoryRepo.addDir(listOf(pathString))
                                emitIntent(IndexUiIntent.Update)
                            }
                        }
                    }
                }
            }

            is IndexUiIntent.Delete -> {
                directoryRepo.deleteDir(entity = intent.entity)
            }
        }
    }

    private val _internalDirectories: Flow<List<DirectoryEntity>> = directoryRepo.queryActiveDirectoriesFlow(StorageType.INTERNAL).flowOnIO()
    val internalDirectoriesState: StateFlow<List<DirectoryEntity>> = _internalDirectories.stateInScope(listOf())

    private val _externalDirectories: Flow<List<DirectoryEntity>> = directoryRepo.queryActiveDirectoriesFlow(StorageType.EXTERNAL).flowOnIO()
    val externalDirectoriesState: StateFlow<List<DirectoryEntity>> = _externalDirectories.stateInScope(listOf())

    private val _customDirectories: Flow<List<DirectoryEntity>> = directoryRepo.queryActiveDirectoriesFlow(StorageType.CUSTOM).flowOnIO()
    val customDirectoriesState: StateFlow<List<DirectoryEntity>> = _customDirectories.stateInScope(listOf())
}
