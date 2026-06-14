package com.xayah.core.util.command

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import com.xayah.core.common.util.trim
import com.xayah.core.util.LogUtil
import com.xayah.core.util.LogUtil.TAG_SHELL_CODE
import com.xayah.core.util.LogUtil.TAG_SHELL_IN
import com.xayah.core.util.LogUtil.TAG_SHELL_OUT
import com.xayah.core.util.LogUtil.log
import com.xayah.core.util.SymbolUtil.QUOTE
import com.xayah.core.util.SymbolUtil.USD
import com.xayah.core.util.adb.AdbService
import com.xayah.core.util.model.ShellResult
import com.xayah.core.util.withIOContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.lingala.zip4j.ZipFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object BaseUtil {
    suspend fun initializeEnvironment(context: Context) = run {
        // Set up LogUtil.
        LogUtil.initialize(context, com.xayah.core.util.logDir())
    }

    suspend fun execute(vararg args: String, log: Boolean = true): ShellResult = withIOContext {
        val shellResult = ShellResult(code = -1, input = args.toList().trim(), out = listOf())

        if (log) {
            log { TAG_SHELL_IN to shellResult.inputString }
        }

        val cmd = shellResult.inputString
        val result = AdbService.executeRaw(cmd)
        shellResult.code = if (result.isSuccess) 0 else -1
        shellResult.out = result.out

        if (log) {
            if (shellResult.outString.trim().isNotEmpty())
                log { TAG_SHELL_OUT to shellResult.outString }
            log { TAG_SHELL_CODE to shellResult.code.toString() }
        }

        shellResult
    }

    suspend fun killPackage(context: Context, userId: Int, packageName: String) {
        AdbService.forceStopPackage(packageName, userId)
    }

    suspend fun mkdirs(dst: String) = withIOContext {
        runCatching {
            val file = File(dst)
            if (file.exists().not()) file.mkdirs() else true
        }
    }

    suspend fun writeIcon(icon: Drawable, dst: String) = withIOContext {
        runCatching {
            val byteArrayOutputStream = ByteArrayOutputStream()
            icon.toBitmap().compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()
            byteArrayOutputStream.flush()
            byteArrayOutputStream.close()
            File(dst).writeBytes(byteArray)
        }
    }

    fun readIconFromPackageName(context: Context, pkgName: String): Drawable? = runCatching { context.packageManager.getApplicationIcon(pkgName) }.getOrNull()

    suspend fun readIcon(context: Context, src: String): Drawable? = withIOContext {
        runCatching {
            val bytes = File(src).readBytes()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size).toDrawable(context.resources)
        }.getOrNull()
    }

    @SuppressLint("SetWorldWritable", "SetWorldReadable")
    fun File.setAllPermissions(): Boolean = run {
        setExecutable(true, false).also {
            if (it.not()) return@run false
        }
        setWritable(true, false).also {
            if (it.not()) return@run false
        }
        setReadable(true, false).also {
            if (it.not()) return@run false
        }
    }

    /**
     * Unzip and return file headers.
     */
    private suspend fun unzip(src: String, dst: String): List<String> = withIOContext {
        runCatching {
            val zip = ZipFile(src)
            zip.extractAll(dst)
            zip.fileHeaders.map { it.fileName }
        }.getOrElse { listOf() }
    }

    private suspend fun releaseAssets(context: Context, src: String, child: String) {
        withIOContext {
            runCatching {
                val assets = File(context.filesDir(), child)
                if (!assets.exists()) {
                    val outStream = FileOutputStream(assets)
                    val inputStream = context.resources.assets.open(src)
                    inputStream.copyTo(outStream)
                    assets.setExecutable(true)
                    assets.setReadable(true)
                    assets.setWritable(true)
                    outStream.flush()
                    inputStream.close()
                    outStream.close()
                }
            }
        }
    }

    suspend fun releaseBase(context: Context): Boolean = withIOContext {
        val bin = File(com.xayah.core.util.binDir())
        val binArchive = File(com.xayah.core.util.binArchivePath())

        // Remove old bin files
        bin.deleteRecursively()
        binArchive.deleteRecursively()

        // Release binaries
        releaseAssets(context = context, src = com.xayah.core.util.BinArchiveName, child = com.xayah.core.util.BinArchiveName)
        unzip(src = com.xayah.core.util.binArchivePath(), dst = com.xayah.core.util.binDir())

        // All binaries need full permissions
        bin.listFiles()?.forEach { file ->
            if (file.setAllPermissions().not()) return@withIOContext false
        }

        // Remove binary archive
        binArchive.deleteRecursively()

        return@withIOContext true
    }

    suspend fun readLink(pid: String) = run {
        execute(
            "readlink",
            "/proc/$pid/ns/mnt",
            log = false,
        ).outString
    }

    suspend fun readVariable(variable: String) = run {
        execute(
            "echo",
            "${QUOTE}$USD$variable${QUOTE}",
            log = false,
        ).outString
    }
}
