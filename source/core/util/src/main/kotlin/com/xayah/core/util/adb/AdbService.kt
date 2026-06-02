package com.xayah.core.util.adb

import android.content.Context
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * ADB 服务封装类，通过 Shizuku 提供的 shell 权限执行命令。
 * 仅支持 ADB 权限范围内的安全操作（APK 备份/恢复、应用管理、权限管理等）。
 */
object AdbService {

    /**
     * 检查 Shizuku 是否已激活且可用
     */
    fun isAvailable(): Boolean {
        return try {
            Shizuku.getUid() != -1 && Shizuku.checkSelfPermission() == Shizuku.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 请求 Shizuku 权限
     */
    fun requestPermission(context: Context) {
        try {
            if (Shizuku.checkSelfPermission() != Shizuku.PERMISSION_GRANTED) {
                Shizuku.requestPermission(0)
            }
        } catch (e: Exception) {
            // Shizuku not installed or not running
        }
    }

    /**
     * 通过 Shizuku 执行 shell 命令
     * @param command 要执行的命令
     * @return 命令输出（stdout）
     */
    fun execute(vararg command: String): AdbResult {
        return try {
            val process = Shizuku.newProcess(command, null, null)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            val output = reader.readLines()
            val error = errorReader.readLines()
            reader.close()
            errorReader.close()
            val exitCode = process.waitFor()
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
     * @param packageName 应用包名
     * @return APK 路径列表
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
     * @param packageName 应用包名
     * @param userId 用户 ID
     */
    fun queryInstalled(packageName: String, userId: Int = 0): Boolean {
        val result = execute("pm", "list", "packages", "--user", userId.toString(), packageName)
        return result.isSuccess && result.out.any { it.contains(packageName) }
    }

    /**
     * 获取所有用户列表
     * @return 用户 ID 列表
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
     * @param userId 用户 ID
     * @param src APK 文件路径
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
