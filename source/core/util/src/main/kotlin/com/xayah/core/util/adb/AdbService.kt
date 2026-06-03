package com.xayah.core.util.adb

import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * ADB 服务封装类，通过 Shizuku 提供的 shell 权限执行命令。
 * 仅支持 ADB 权限范围内的安全操作（APK 备份/恢复、应用管理、权限管理等）。
 *
 * 遵循 Shizuku 官方开发文档：
 * - 使用 ShizukuProvider 获取 Binder（需在 AndroidManifest.xml 中声明）
 * - 使用 Shizuku.checkSelfPermission() / requestPermission() 管理权限
 * - 使用 addBinderReceivedListener / addBinderDeadListener 管理 Binder 生命周期
 * - 通过 IShizukuService.newProcess() 执行 shell 命令（newProcess 将在 API 14 移除，后续需迁移至 UserService）
 */
object AdbService {

    private const val SHIZUKU_REQUEST_CODE = 101

    private val _binderAlive = MutableStateFlow(false)
    val binderAlive: StateFlow<Boolean> = _binderAlive.asStateFlow()

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        _binderAlive.value = true
        updatePermissionState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _binderAlive.value = false
        _permissionGranted.value = false
    }

    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            _permissionGranted.value = grantResult == PackageManager.PERMISSION_GRANTED
        }

    /**
     * 初始化 Shizuku 监听器，应在 Application.onCreate() 中调用
     */
    fun init() {
        try {
            Shizuku.addBinderReceivedListener(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
            // 如果 Binder 已经存在，立即更新状态
            if (Shizuku.pingBinder()) {
                _binderAlive.value = true
                updatePermissionState()
            }
        } catch (e: Exception) {
            // Shizuku 未安装或不可用
        }
    }

    /**
     * 销毁 Shizuku 监听器，应在 Application.onTerminate() 中调用
     */
    fun destroy() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
        } catch (e: Exception) {
            // 忽略
        }
    }

    private fun updatePermissionState() {
        try {
            _permissionGranted.value =
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            _permissionGranted.value = false
        }
    }

    /**
     * 检查 Shizuku 是否已激活且可用
     */
    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检查 Shizuku Binder 是否存活（权限可能未授予）
     */
    fun isBinderAlive(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 请求 Shizuku 权限
     * 遵循官方文档的权限申请流程：
     * 1. 检查 checkSelfPermission
     * 2. 检查 shouldShowRequestPermissionRationale
     * 3. 调用 requestPermission
     */
    fun requestPermission() {
        try {
            if (Shizuku.isPreV11()) {
                // Pre-v11 不支持
                return
            }
            when {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                    _permissionGranted.value = true
                }
                Shizuku.shouldShowRequestPermissionRationale() -> {
                    // 用户选择了"拒绝且不再询问"，需要引导用户手动授权
                    // 此处仅更新状态，UI 层应处理引导逻辑
                    _permissionGranted.value = false
                }
                else -> {
                    Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
                }
            }
        } catch (e: IllegalStateException) {
            // Binder 未就绪，无法请求权限
        } catch (e: Exception) {
            // Shizuku 未安装或不可用
        }
    }

    /**
     * 获取 Shizuku 服务是否以 ROOT 运行（uid=0），ADB 则为 uid=2000
     */
    fun isRootMode(): Boolean {
        return try {
            Shizuku.getUid() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取 IShizukuService 实例
     * 通过 Shizuku.getBinder() 获取 Binder 并转换为 AIDL 接口
     */
    private fun getService(): IShizukuService {
        val binder = Shizuku.getBinder()
            ?: throw IllegalStateException("Shizuku binder not available")
        return IShizukuService.Stub.asInterface(binder)
    }

    /**
     * 通过 Shizuku 执行 shell 命令
     * 使用 IShizukuService.newProcess() 执行命令
     *
     * 注意：newProcess 在 API 13 中已标记为 private，计划在 API 14 移除。
     * 后续需迁移至 UserService 方式执行命令。
     *
     * @param command 要执行的命令参数
     * @return 命令执行结果
     */
    fun execute(vararg command: String): AdbResult {
        return try {
            val remoteProcess: IRemoteProcess = getService().newProcess(command, null, null)
            val inputStream = ParcelFileDescriptor.AutoCloseInputStream(remoteProcess.inputStream)
            val errorStream = ParcelFileDescriptor.AutoCloseInputStream(remoteProcess.errorStream)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val errorReader = BufferedReader(InputStreamReader(errorStream))
            val output = reader.readLines()
            val error = errorReader.readLines()
            reader.close()
            errorReader.close()
            val exitCode = remoteProcess.waitFor()
            AdbResult(
                isSuccess = exitCode == 0,
                out = output,
                err = error,
                exitCode = exitCode
            )
        } catch (e: Exception) {
            AdbResult(
                isSuccess = false,
                out = emptyList(),
                err = listOf(e.message ?: "Unknown error"),
                exitCode = -1
            )
        }
    }

    /**
     * 获取应用的 APK 路径
     */
    fun getPackageSourceDir(packageName: String): List<String> {
        val result = execute("pm", "path", packageName)
        if (!result.isSuccess) return emptyList()
        return result.out.mapNotNull { line ->
            if (line.startsWith("package:")) line.substring("package:".length) else null
        }
    }

    /**
     * 检查应用是否已安装
     */
    fun queryInstalled(packageName: String, userId: Int = 0): Boolean {
        val result = execute("pm", "list", "packages", "--user", userId.toString(), packageName)
        return result.isSuccess && result.out.any { it.contains(packageName) }
    }

    /**
     * 获取所有用户列表
     */
    fun getUsers(): List<Int> {
        val result = execute("pm", "list", "users")
        if (!result.isSuccess) return listOf(0)
        return result.out.mapNotNull { line ->
            val regex = Regex("\\{(\\d+):")
            regex.find(line)?.groupValues?.get(1)?.toIntOrNull()
        }.ifEmpty { listOf(0) }
    }

    /**
     * 安装 APK
     */
    fun installApk(userId: Int, src: String): AdbResult {
        return execute("pm", "install", "--user", userId.toString(), "-r", "-t", src)
    }

    /**
     * 创建多 APK 安装会话
     */
    fun installCreate(userId: Int): AdbResult {
        return execute("pm", "install-create", "--user", userId.toString(), "-t")
    }

    /**
     * 写入 APK 到安装会话
     */
    fun installWrite(session: String, srcName: String, src: String): AdbResult {
        return execute("pm", "install-write", session, srcName, src)
    }

    /**
     * 提交安装会话
     */
    fun installCommit(session: String): AdbResult {
        return execute("pm", "install-commit", session)
    }

    /**
     * 授予应用运行时权限
     */
    fun grantPermission(packageName: String, permissionName: String, userId: Int = 0): AdbResult {
        return execute("pm", "grant", "--user", userId.toString(), packageName, permissionName)
    }

    /**
     * 撤销应用运行时权限
     */
    fun revokePermission(packageName: String, permissionName: String, userId: Int = 0): AdbResult {
        return execute("pm", "revoke", "--user", userId.toString(), packageName, permissionName)
    }

    /**
     * 强制停止应用
     */
    fun forceStopPackage(packageName: String): AdbResult {
        return execute("am", "force-stop", packageName)
    }

    /**
     * 启用应用
     */
    fun enablePackage(packageName: String, userId: Int = 0): AdbResult {
        return execute("pm", "enable", "--user", userId.toString(), packageName)
    }

    /**
     * 禁用应用
     */
    fun disablePackage(packageName: String, userId: Int = 0): AdbResult {
        return execute("pm", "disable", "--user", userId.toString(), packageName)
    }

    /**
     * 设置 AppOps 模式
     */
    fun setAppOpsMode(uid: Int, packageName: String, op: String, mode: String): AdbResult {
        return execute("appops", "set", "--uid", uid.toString(), packageName, op, mode)
    }

    /**
     * 重置应用 AppOps
     */
    fun resetAppOps(packageName: String, userId: Int = 0): AdbResult {
        return execute("appops", "reset", "--user", userId.toString(), packageName)
    }

    /**
     * 获取系统设置
     */
    fun getSettings(namespace: String, key: String): AdbResult {
        return execute("settings", "get", namespace, key)
    }

    /**
     * 设置系统设置
     */
    fun putSettings(namespace: String, key: String, value: String): AdbResult {
        return execute("settings", "put", namespace, key, value)
    }

    /**
     * 列出指定用户已安装的应用包名
     */
    fun listPackages(userId: Int = 0, includeSystemApps: Boolean = false): List<String> {
        val args = mutableListOf("pm", "list", "packages", "--user", userId.toString())
        if (!includeSystemApps) args.add("--no-system")
        val result = execute(*args.toTypedArray())
        if (!result.isSuccess) return emptyList()
        return result.out.mapNotNull { line ->
            if (line.startsWith("package:")) line.substring("package:".length).trim() else null
        }
    }

    /**
     * 复制文件（ADB 权限范围内）
     * 注意：ADB 无法访问其他应用的私有数据目录
     */
    fun copyFile(src: String, dst: String): AdbResult {
        return execute("cp", "-r", src, dst)
    }

    /**
     * 计算文件/目录大小
     */
    fun calculateSize(path: String): AdbResult {
        return execute("du", "-sb", path)
    }
}

data class AdbResult(
    val isSuccess: Boolean,
    val out: List<String>,
    val err: List<String>,
    val exitCode: Int,
) {
    val outString: String get() = out.joinToString("\n")
    val errString: String get() = err.joinToString("\n")
}
