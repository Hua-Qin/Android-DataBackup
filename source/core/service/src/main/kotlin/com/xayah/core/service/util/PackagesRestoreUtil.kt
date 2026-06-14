package com.xayah.core.service.util

import android.app.AppOpsManagerHidden
import android.content.Context
import com.xayah.core.common.util.toLineString
import com.xayah.core.data.repository.CloudRepository
import com.xayah.core.data.repository.PackageRepository
import com.xayah.core.data.util.srcDir
import com.xayah.core.database.dao.TaskDao
import com.xayah.core.datastore.readCleanRestoring
import com.xayah.core.datastore.readSelectionType
import com.xayah.core.model.DataType
import com.xayah.core.model.OperationState
import com.xayah.core.model.SelectionType
import com.xayah.core.model.database.PackageEntity
import com.xayah.core.model.database.TaskDetailPackageEntity
import com.xayah.core.model.util.formatSize
import com.xayah.core.network.client.CloudClient
import com.xayah.core.util.LogUtil
import com.xayah.core.util.PathUtil
import com.xayah.core.util.SymbolUtil
import com.xayah.core.util.adb.AdbService
import com.xayah.core.util.command.Appops
import com.xayah.core.util.command.Tar
import com.xayah.core.util.model.ShellResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

class PackagesRestoreUtil @Inject constructor(
    @ApplicationContext val context: Context,
    private val taskDao: TaskDao,
    private val packageRepository: PackageRepository,
    private val cloudRepository: CloudRepository,
    private val pathUtil: PathUtil,
) {
    companion object {
        private const val TAG = "PackagesRestoreUtil"
    }

    private fun log(onMsg: () -> String): String = run {
        val msg = onMsg()
        LogUtil.log { TAG to msg }
        msg
    }

    private suspend fun PackageEntity.getDataSelected(dataType: DataType) = when (context.readSelectionType().first()) {
        SelectionType.DEFAULT -> {
            when (dataType) {
                DataType.PACKAGE_APK -> apkSelected
                DataType.PACKAGE_USER -> userSelected
                DataType.PACKAGE_USER_DE -> userDeSelected
                DataType.PACKAGE_DATA -> dataSelected
                DataType.PACKAGE_OBB -> obbSelected
                DataType.PACKAGE_MEDIA -> mediaSelected
                else -> false
            }
        }

        SelectionType.APK -> {
            dataType == DataType.PACKAGE_APK
        }

        SelectionType.DATA -> {
            dataType != DataType.PACKAGE_APK
        }

        SelectionType.BOTH -> {
            true
        }
    }

    private suspend fun TaskDetailPackageEntity.updateInfo(
        dataType: DataType,
        state: OperationState? = null,
        bytes: Long? = null,
        log: String? = null,
        content: String? = null,
    ) = run {
        when (dataType) {
            DataType.PACKAGE_APK -> {
                apkInfo.also {
                    if (state != null) it.state = state
                    if (bytes != null) it.bytes = bytes
                    if (log != null) it.log = log
                    if (content != null) it.content = content
                }
            }

            DataType.PACKAGE_USER -> {
                userInfo.also {
                    if (state != null) it.state = state
                    if (bytes != null) it.bytes = bytes
                    if (log != null) it.log = log
                    if (content != null) it.content = content
                }
            }

            DataType.PACKAGE_USER_DE -> {
                userDeInfo.also {
                    if (state != null) it.state = state
                    if (bytes != null) it.bytes = bytes
                    if (log != null) it.log = log
                    if (content != null) it.content = content
                }
            }

            DataType.PACKAGE_DATA -> {
                dataInfo.also {
                    if (state != null) it.state = state
                    if (bytes != null) it.bytes = bytes
                    if (log != null) it.log = log
                    if (content != null) it.content = content
                }
            }

            DataType.PACKAGE_OBB -> {
                obbInfo.also {
                    if (state != null) it.state = state
                    if (bytes != null) it.bytes = bytes
                    if (log != null) it.log = log
                    if (content != null) it.content = content
                }
            }

            DataType.PACKAGE_MEDIA -> {
                mediaInfo.also {
                    if (state != null) it.state = state
                    if (bytes != null) it.bytes = bytes
                    if (log != null) it.log = log
                    if (content != null) it.content = content
                }
            }

            else -> {}
        }
        taskDao.upsert(this)
    }

    private fun TaskDetailPackageEntity.getLog(
        dataType: DataType,
    ) = when (dataType) {
        DataType.PACKAGE_APK -> apkInfo.log
        DataType.PACKAGE_USER -> userInfo.log
        DataType.PACKAGE_USER_DE -> userDeInfo.log
        DataType.PACKAGE_DATA -> dataInfo.log
        DataType.PACKAGE_OBB -> obbInfo.log
        DataType.PACKAGE_MEDIA -> mediaInfo.log
        else -> ""
    }

    suspend fun restoreApk(userId: Int, p: PackageEntity, t: TaskDetailPackageEntity, srcDir: String): ShellResult = run {
        log { "Restoring apk..." }

        val dataType = DataType.PACKAGE_APK
        val packageName = p.packageName
        val ct = p.indexInfo.compressionType
        val src = packageRepository.getArchiveDst(dstDir = srcDir, dataType = dataType, ct = ct)
        var isSuccess = true
        val out = mutableListOf<String>()

        if (p.getDataSelected(dataType).not()) {
            t.updateInfo(dataType = dataType, state = OperationState.SKIP)
        } else {
            // Return if the archive doesn't exist.
            val srcExists = AdbService.exists(src)
            if (srcExists) {
                val sizeBytes = AdbService.calculateSizeLong(src)
                t.updateInfo(dataType = dataType, state = OperationState.PROCESSING, bytes = sizeBytes)
                // Decompress apk archive
                val tmpApkPath = pathUtil.getTmpApkPath(packageName = packageName)
                AdbService.deleteRecursively(tmpApkPath)
                AdbService.mkdirs(tmpApkPath)
                Tar.decompress(src = src, dst = tmpApkPath, extra = ct.decompressPara).also { result ->
                    isSuccess = result.isSuccess
                    out.addAll(result.out)
                }

                // Install apks
                val apksPath = AdbService.listFilePaths(tmpApkPath)
                apksPath.also { apks ->
                    when (apks.size) {
                        0 -> {
                            isSuccess = false
                            out.add(log { "$tmpApkPath is empty." })
                        }

                        1 -> {
                            AdbService.installApk(userId, apks.first()).also { result ->
                                isSuccess = isSuccess && result.isSuccess
                                if (!result.isSuccess) out.add(log { "Failed to install: ${apks.first()}" })
                            }
                        }

                        else -> {
                            // ADB 模式下使用 stream install
                            val createResult = AdbService.installCreate(userId)
                            val sessionId = createResult.outString.trim()
                            if (createResult.isSuccess && sessionId.isNotEmpty()) {
                                out.add(log { "Install session: $sessionId." })
                                var allWritten = true
                                apks.forEach { apkPath ->
                                    val fileName = PathUtil.getFileName(apkPath)
                                    val writeResult = AdbService.installWrite(sessionId, fileName, apkPath)
                                    if (!writeResult.isSuccess) {
                                        allWritten = false
                                        out.add(log { "Failed to write: $apkPath" })
                                    }
                                }
                                if (allWritten) {
                                    val commitResult = AdbService.installCommit(sessionId)
                                    if (!commitResult.isSuccess) {
                                        isSuccess = false
                                        out.add(log { "Failed to commit install session." })
                                    }
                                } else {
                                    isSuccess = false
                                }
                            } else {
                                isSuccess = false
                                out.add(log { "Failed to get install session." })
                            }
                        }
                    }
                }
                AdbService.deleteRecursively(tmpApkPath)

                // Check the installation again.
                val installed = AdbService.getPackageInfo(packageName) != null
                if (!installed) {
                    isSuccess = false
                    log { "Not installed: $packageName." }
                }
            } else {
                isSuccess = false
                out.add(log { "Not exist: $src" })
            }
            t.updateInfo(dataType = dataType, state = if (isSuccess) OperationState.DONE else OperationState.ERROR, log = out.toLineString())
        }

        ShellResult(code = if (isSuccess) 0 else -1, input = listOf(), out = out)
    }

    /**
     * Package data: USER, USER_DE, DATA, OBB, MEDIA
     */
    suspend fun restoreData(userId: Int, p: PackageEntity, t: TaskDetailPackageEntity, dataType: DataType, srcDir: String): ShellResult = run {
        log { "Restoring ${dataType.type}..." }

        val packageName = p.packageName
        val ct = p.indexInfo.compressionType
        val src = packageRepository.getArchiveDst(dstDir = srcDir, dataType = dataType, ct = ct)
        var isSuccess = true
        val out = mutableListOf<String>()

        if (p.getDataSelected(dataType).not()) {
            t.updateInfo(dataType = dataType, state = OperationState.SKIP)
        } else {
            // ADB 模式下不支持用户数据恢复
            isSuccess = false
            out.add(log { "User data restore is not supported in ADB mode." })
            t.updateInfo(dataType = dataType, state = OperationState.ERROR, log = out.toLineString())
        }

        ShellResult(code = if (isSuccess) 0 else -1, input = listOf(), out = out)
    }

    suspend fun restorePermissions(userId: Int, p: PackageEntity) = run {
        log { "Restoring permissions..." }

        val packageName = p.packageName
        val permissions = p.extraInfo.permissions

        if (p.permissionSelected) {
            // ADB 模式下使用 AdbService 授予/撤销权限
            Appops.reset(userId = userId, packageName = packageName)
            log { "Permissions size: ${permissions.size}..." }
            val uid = AdbService.getPackageInfo(packageName)?.uid ?: -1
            permissions.forEach {
                log { "Permission name: ${it.name}, isGranted: ${it.isGranted}, op: ${it.op}, mode: ${it.mode}" }
                runCatching {
                    if (it.isGranted) {
                        AdbService.grantPermission(packageName, it.name, userId)
                    } else {
                        AdbService.revokePermission(packageName, it.name, userId)
                    }
                    if (it.op != AppOpsManagerHidden.OP_NONE) {
                        it.mode?.also { mode ->
                            if (uid != -1) {
                                AdbService.setAppOpsMode(uid, packageName, it.op.toString(), mode.toString())
                            }
                        }
                    }
                }
            }
        } else {
            log { "Skip." }
        }
    }

    suspend fun restoreSsaid(userId: Int, p: PackageEntity) = run {
        log { "Restoring ssaid..." }

        if (p.ssaidSelected) {
            // ADB 模式下无法设置 SSAID
            log { "SSAID restore is not supported in ADB mode." }
        } else {
            log { "Skip." }
        }
    }

    suspend fun download(
        client: CloudClient,
        p: PackageEntity,
        t: TaskDetailPackageEntity,
        dataType: DataType,
        srcDir: String,
        dstDir: String,
        onDownloaded: suspend (p: PackageEntity, t: TaskDetailPackageEntity, dataType: DataType, path: String) -> Unit
    ) = run {
        val ct = p.indexInfo.compressionType
        val src = packageRepository.getArchiveDst(dstDir = srcDir, dataType = dataType, ct = ct)

        if (p.getDataSelected(dataType).not()) {
            t.updateInfo(dataType = dataType, state = OperationState.SKIP)
        } else {
            t.updateInfo(dataType = dataType, state = OperationState.DOWNLOADING)

            if (client.exists(src)) {
                var flag = true
                var progress = 0.0
                with(CoroutineScope(coroutineContext)) {
                    launch {
                        while (flag) {
                            t.updateInfo(dataType = dataType, content = progress.formatSize())
                            delay(500)
                        }
                    }
                }

                cloudRepository.download(client = client,
                    src = src,
                    dstDir = dstDir,
                    onDownloading = { written, _ -> progress = written.toDouble() },
                    onDownloaded = {
                        onDownloaded(p, t, dataType, dstDir)
                    }
                ).apply {
                    flag = false
                    t.updateInfo(
                        dataType = dataType,
                        log = (t.getLog(dataType) + "\n${outString}").trim(),
                        content = progress.formatSize()
                    )
                    if (isSuccess.not()) {
                        t.updateInfo(dataType = dataType, state = OperationState.ERROR)
                    }
                }
            } else {
                if (dataType == DataType.PACKAGE_USER || dataType == DataType.PACKAGE_APK) {
                    t.updateInfo(dataType = dataType, state = OperationState.ERROR, log = log { "Failed to connect to cloud or file not exist: $src" })
                } else {
                    t.updateInfo(dataType = dataType, state = OperationState.SKIP, log = log { "Failed to connect to cloud or file not exist, skip: $src" })
                }
            }
        }
    }
}
