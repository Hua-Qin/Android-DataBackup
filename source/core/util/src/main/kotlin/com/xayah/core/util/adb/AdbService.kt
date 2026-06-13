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
     * 使用 pm list users 命令
     * @return 用户ID列表
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
    fun forceStopPackage(packageName: String, userId: Int = 0): AdbResult {
        return execute("am", "force-stop", "--user", userId.toString(), packageName)
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

    // ========== ADB 模式下目录选择所需的文件操作 ==========

    /**
     * 构建 ls 命令参数
     * 使用 -1F 标志：-1 每行一个条目，-F 添加类型指示符（/ 目录，* 可执行，@ 符号链接）
     * @param path 要列出的目录路径
     * @param listFiles 是否包含文件
     * @param listDirs 是否包含目录
     * @return 命令参数数组
     */
    fun buildLsCommand(path: String, listFiles: Boolean = true, listDirs: Boolean = true): Array<String> {
        val args = mutableListOf("ls", "-1F")
        if (listDirs && !listFiles) args.add("-d")
        if (!listDirs && listFiles) args.add("-p")
        // 确保路径以 / 结尾，避免 ls 将 / 作为单独参数列出根目录
        val normalizedPath = if (path.endsWith("/")) path else "$path/"
        args.add(normalizedPath)
        return args.toTypedArray()
    }

    /**
     * 解析 ls -1F 的输出，区分文件和目录
     * ls -F 在目录名后添加 /，可执行文件后添加 *，符号链接后添加 @
     * @param output ls 命令的输出行
     * @return 解析后的条目列表
     */
    fun parseLsOutput(output: List<String>): List<LsEntry> {
        return output.mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("total")) return@mapNotNull null

            when {
                trimmed.endsWith("/") -> LsEntry(
                    name = trimmed.dropLast(1),
                    isDirectory = true,
                )
                trimmed.endsWith("*") -> LsEntry(
                    name = trimmed.dropLast(1),
                    isDirectory = false,
                )
                trimmed.endsWith("@") -> LsEntry(
                    name = trimmed.dropLast(1),
                    isDirectory = false,
                )
                else -> LsEntry(
                    name = trimmed,
                    isDirectory = false,
                )
            }
        }
    }

    /**
     * 列出指定路径下的子目录/文件路径（ADB 模式替代 rootService.listFilePaths）
     * 使用 ls -1F 命令，通过 -F 标志的类型指示符区分文件和目录
     * @param path 要列出的目录路径
     * @param listFiles 是否包含文件
     * @param listDirs 是否包含目录
     * @return 路径列表
     */
    fun listFilePaths(path: String, listFiles: Boolean = true, listDirs: Boolean = true): List<String> {
        val cmd = buildLsCommand(path, listFiles, listDirs)
        val result = execute(*cmd)
        if (!result.isSuccess) return emptyList()
        val entries = parseLsOutput(result.out)
        return entries
            .filter { (listDirs && it.isDirectory) || (listFiles && !it.isDirectory) }
            .map { "${path}/${it.name}" }
    }

    /**
     * 遍历目录并返回子项信息（用于 PickYouLauncher 的 traverseBackend）
     * 单次 ADB 调用完成遍历，避免对每个条目单独调用 test -d
     * @param path 要遍历的目录路径
     * @return 解析后的条目列表
     */
    fun traverseDirectory(path: String): List<LsEntry> {
        val cmd = buildLsCommand(path)
        val result = execute(*cmd)
        if (!result.isSuccess) return emptyList()
        return parseLsOutput(result.out)
    }

    /**
     * 读取文件系统状态信息（ADB 模式替代 rootService.readStatFs）
     * 使用 df 命令获取可用空间和总空间
     * @param path 路径
     * @return Pair(availableBytes, totalBytes)
     */
    fun readStatFs(path: String): Pair<Long, Long> {
        val result = execute("df", "-P", path)
        if (!result.isSuccess) return Pair(0L, 0L)
        // df -P 输出格式:
        // Filesystem         1024-blocks     Used Available Capacity Mounted on
        // /dev/fuse           244219904 83627584 160432352      35% /storage/emulated
        val dataLine = result.out.lastOrNull { it.startsWith("/") } ?: return Pair(0L, 0L)
        val parts = dataLine.split(Regex("\\s+"))
        if (parts.size < 4) return Pair(0L, 0L)
        val totalKb = parts[1].toLongOrNull() ?: 0L
        val availableKb = parts[3].toLongOrNull() ?: 0L
        return Pair(availableKb * 1024, totalKb * 1024)
    }

    /**
     * 计算路径大小（ADB 模式替代 rootService.calculateSize）
     * @param path 路径
     * @return 字节数
     */
    fun calculateSizeLong(path: String): Long {
        val result = execute("du", "-sb", path)
        if (!result.isSuccess) return 0L
        val line = result.out.firstOrNull() ?: return 0L
        return line.split(Regex("\\s+")).firstOrNull()?.toLongOrNull() ?: 0L
    }

    /**
     * 检查路径是否存在
     */
    fun exists(path: String): Boolean {
        val result = execute("test", "-e", path)
        return result.isSuccess
    }

    /**
     * 创建目录（含父目录）
     */
    fun mkdirs(path: String): Boolean {
        val result = execute("mkdir", "-p", path)
        return result.isSuccess
    }

    // ========== ADB 模式下应用列表初始化所需的操作 ==========

    /**
     * 获取已安装应用的包名列表（ADB 模式替代 rootService.getInstalledPackagesAsUser）
     * 使用 pm list packages 命令
     * @param userId 用户ID
     * @return 包名列表
     */
    fun getInstalledPackageNames(userId: Int = 0): List<String> {
        val args = mutableListOf("pm", "list", "packages", "--user", userId.toString())
        val result = execute(*args.toTypedArray())
        if (!result.isSuccess) return emptyList()
        return result.out.mapNotNull { line ->
            if (line.startsWith("package:")) line.substring("package:".length).trim() else null
        }
    }

    /**
     * 获取应用信息（ADB 模式替代 rootService.getPackageInfoAsUser 的部分功能）
     * 使用 dumpsys package 命令获取关键信息
     * @param packageName 包名
     * @param userId 用户ID
     * @return AdbPackageInfo 或 null
     */
    fun getPackageInfo(packageName: String, userId: Int = 0): AdbPackageInfo? {
        val result = execute("dumpsys", "package", "--user", userId.toString(), packageName)
        if (!result.isSuccess) return null

        var versionName = ""
        var versionCode = 0L
        var flags = 0
        var firstInstallTime = 0L
        var lastUpdateTime = 0L
        var uid = -1
        var enabled = true

        for (line in result.out) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("versionName=") -> versionName = trimmed.substringAfter("=")
                trimmed.startsWith("versionCode=") -> versionCode = trimmed.substringAfter("=").toLongOrNull() ?: 0L
                trimmed.startsWith("flags=") -> flags = trimmed.substringAfter("=").substringBefore(" ").toIntOrNull() ?: 0
                trimmed.startsWith("firstInstallTime=") -> firstInstallTime = trimmed.substringAfter("=").substringBefore(" ").toLongOrNull() ?: 0L
                trimmed.startsWith("lastUpdateTime=") -> lastUpdateTime = trimmed.substringAfter("=").substringBefore(" ").toLongOrNull() ?: 0L
                trimmed.startsWith("userId=") -> uid = trimmed.substringAfter("=").substringBefore(" ").toIntOrNull() ?: -1
                trimmed.startsWith("pkgFlags=[") -> {
                    // 解析 pkgFlags 获取 FLAG_SYSTEM 等
                    val flagsStr = trimmed.substringAfter("[").substringBefore("]")
                    if (flagsStr.contains("SYSTEM")) flags = flags or 0x1 // ApplicationInfo.FLAG_SYSTEM
                }
            }
        }

        // 检查是否禁用
        val enabledResult = execute("pm", "list", "packages", "-d", "--user", userId.toString(), packageName)
        if (enabledResult.isSuccess && enabledResult.out.any { it.contains(packageName) }) {
            enabled = false
        }

        return AdbPackageInfo(
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            flags = flags,
            firstInstallTime = firstInstallTime,
            lastUpdateTime = lastUpdateTime,
            uid = uid,
            enabled = enabled,
        )
    }

    /**
     * 递归遍历文件树（ADB 模式替代 rootService.walkFileTree）
     * 使用 find 命令
     * @param path 根路径
     * @return 路径列表
     */
    fun walkFileTree(path: String): List<String> {
        val result = execute("find", path, "-type", "f")
        if (!result.isSuccess) return emptyList()
        return result.out.filter { it.isNotBlank() }
    }

    /**
     * 读取文件内容为文本（ADB 模式替代 rootService.readText）
     * 使用 cat 命令
     * @param path 文件路径
     * @return 文件内容
     */
    fun readText(path: String): String {
        val result = execute("cat", path)
        if (!result.isSuccess) return ""
        return result.outString
    }

    /**
     * 读取 JSON 文件并解析（ADB 模式替代 rootService.readJson）
     * @param path JSON 文件路径
     * @return JSON 字符串或 null
     */
    fun readJsonText(path: String): String? {
        val result = execute("cat", path)
        if (!result.isSuccess) return null
        return result.outString.ifEmpty { null }
    }

    /**
     * 写入文本到文件（ADB 模式替代 rootService.writeText）
     * @param text 文本内容
     * @param dst 目标路径
     * @return 是否成功
     */
    fun writeText(text: String, dst: String): Boolean {
        // 使用 echo + 重定向写入，注意转义
        val result = execute("sh", "-c", "cat > '$dst' << 'HEREDOC_EOF'\n$text\nHEREDOC_EOF")
        return result.isSuccess
    }

    /**
     * 删除目录（递归）（ADB 模式替代 rootService.deleteRecursively）
     * @param path 路径
     * @return 是否成功
     */
    fun deleteRecursively(path: String): Boolean {
        val result = execute("rm", "-rf", path)
        return result.isSuccess
    }

    /**
     * 重命名文件/目录（ADB 模式替代 rootService.renameTo）
     * @param src 源路径
     * @param dst 目标路径
     * @return 是否成功
     */
    fun renameTo(src: String, dst: String): Boolean {
        val result = execute("mv", src, dst)
        return result.isSuccess
    }

    /**
     * 计算文件 MD5（ADB 模式替代 rootService.calculateMD5）
     * @param path 文件路径
     * @return MD5 字符串或 null
     */
    fun calculateMD5(path: String): String? {
        val result = execute("md5sum", path)
        if (!result.isSuccess) return null
        val line = result.out.firstOrNull() ?: return null
        return line.split(Regex("\\s+")).firstOrNull()
    }

    /**
     * 清理空目录（递归）（ADB 模式替代 rootService.clearEmptyDirectoriesRecursively）
     * @param path 路径
     */
    fun clearEmptyDirectoriesRecursively(path: String) {
        execute("find", path, "-type", "d", "-empty", "-delete")
    }

    /**
     * 获取应用权限列表（ADB 模式替代 rootService.getPermissions）
     * 使用 dumpsys package 获取运行时权限
     * @param packageName 包名
     * @return 权限名列表
     */
    fun getPermissions(packageName: String): List<String> {
        val result = execute("dumpsys", "package", packageName)
        if (!result.isSuccess) return emptyList()
        val permissions = mutableListOf<String>()
        var inRuntimePermissions = false
        for (line in result.out) {
            val trimmed = line.trim()
            if (trimmed.contains("runtime permissions:")) {
                inRuntimePermissions = true
                continue
            }
            if (inRuntimePermissions) {
                if (trimmed.startsWith("android.permission.") || trimmed.startsWith("com.")) {
                    val permName = trimmed.substringBefore(":").trim()
                    if (permName.isNotEmpty()) permissions.add(permName)
                } else if (!trimmed.startsWith(" ") && trimmed.isNotEmpty()) {
                    inRuntimePermissions = false
                }
            }
        }
        return permissions
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

/**
 * ls 输出条目，表示一个文件或目录
 */
data class LsEntry(
    val name: String,
    val isDirectory: Boolean,
)

/**
 * ADB 模式下获取的应用信息
 * 用于替代 rootService.getPackageInfoAsUser 返回的 PackageInfo
 */
data class AdbPackageInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val flags: Int,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val uid: Int,
    val enabled: Boolean,
)
