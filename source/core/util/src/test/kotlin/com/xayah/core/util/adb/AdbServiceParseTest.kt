package com.xayah.core.util.adb

import org.junit.Assert.*
import org.junit.Test

/**
 * 测试 AdbService 中 shell 命令输出解析逻辑。
 * 这些解析函数是 ADB 模式下目录选择功能的核心。
 */
class AdbServiceParseTest {

    // ========== buildLsCommand 测试 ==========

    @Test
    fun `buildLsCommand constructs correct ls -1F command for default listing`() {
        val cmd = AdbService.buildLsCommand("/storage/emulated/0")
        assertArrayEquals(arrayOf("ls", "-1F", "/storage/emulated/0/"), cmd)
    }

    @Test
    fun `buildLsCommand does not append slash if already present`() {
        val cmd = AdbService.buildLsCommand("/storage/emulated/0/")
        assertArrayEquals(arrayOf("ls", "-1F", "/storage/emulated/0/"), cmd)
    }

    @Test
    fun `buildLsCommand for dirs only uses -d flag`() {
        val cmd = AdbService.buildLsCommand("/storage/emulated/0", listFiles = false, listDirs = true)
        // -d lists directory entries themselves, -F adds type indicators
        assertTrue(cmd.contains("-d"))
    }

    @Test
    fun `buildLsCommand for files only uses appropriate flags`() {
        val cmd = AdbService.buildLsCommand("/storage/emulated/0", listFiles = true, listDirs = false)
        // Should use -p flag to identify non-directories
        assertTrue(cmd.contains("-p"))
    }

    // ========== parseLsOutput 测试 ==========

    @Test
    fun `parseLsOutput correctly identifies directories with trailing slash`() {
        val output = listOf(
            "Alarms/",
            "Android/",
            "DCIM/",
            "Documents/"
        )
        val entries = AdbService.parseLsOutput(output)
        assertEquals(4, entries.size)
        entries.forEach {
            assertTrue("${it.name} should be a directory", it.isDirectory)
        }
        assertEquals("Alarms", entries[0].name)
        assertEquals("Android", entries[1].name)
    }

    @Test
    fun `parseLsOutput correctly identifies files without trailing slash`() {
        val output = listOf(
            "somefile.txt",
            "another_file.apk"
        )
        val entries = AdbService.parseLsOutput(output)
        assertEquals(2, entries.size)
        entries.forEach {
            assertFalse("${it.name} should not be a directory", it.isDirectory)
        }
    }

    @Test
    fun `parseLsOutput handles mixed files and directories`() {
        val output = listOf(
            "Alarms/",
            "Android/",
            "somefile.txt",
            "DCIM/",
            "another_file.apk"
        )
        val entries = AdbService.parseLsOutput(output)
        assertEquals(5, entries.size)

        val dirs = entries.filter { it.isDirectory }
        val files = entries.filter { !it.isDirectory }
        assertEquals(3, dirs.size)
        assertEquals(2, files.size)

        assertEquals(listOf("Alarms", "Android", "DCIM"), dirs.map { it.name })
        assertEquals(listOf("somefile.txt", "another_file.apk"), files.map { it.name })
    }

    @Test
    fun `parseLsOutput filters out empty lines and total line`() {
        val output = listOf(
            "",
            "total 42",
            "Alarms/",
            "",
            "Android/"
        )
        val entries = AdbService.parseLsOutput(output)
        assertEquals(2, entries.size)
        assertEquals("Alarms", entries[0].name)
        assertEquals("Android", entries[1].name)
    }

    @Test
    fun `parseLsOutput handles empty output`() {
        val entries = AdbService.parseLsOutput(emptyList())
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `parseLsOutput handles executables with asterisk`() {
        // ls -F appends * to executables
        val output = listOf(
            "my_script*",
            "Alarms/",
            "readme.txt"
        )
        val entries = AdbService.parseLsOutput(output)
        assertEquals(3, entries.size)
        assertFalse(entries[0].isDirectory) // * means executable, not directory
        assertEquals("my_script", entries[0].name)
        assertTrue(entries[1].isDirectory)
        assertFalse(entries[2].isDirectory)
    }

    @Test
    fun `parseLsOutput handles symlinks with at sign`() {
        // ls -F appends @ to symlinks
        val output = listOf(
            "sdcard@",
            "Alarms/"
        )
        val entries = AdbService.parseLsOutput(output)
        assertEquals(2, entries.size)
        assertFalse(entries[0].isDirectory) // @ means symlink, treat as non-directory
        assertEquals("sdcard", entries[0].name)
        assertTrue(entries[1].isDirectory)
    }

    // ========== 旧测试保留 ==========

    @Test
    fun `parseLsOutput filters directories from ls output for numeric dirs`() {
        val lsOutput = listOf(
            "0",
            "10",
            "999",
            "obb",
            "self"
        )
        val result = lsOutput.filter { it.toIntOrNull() != null }
        assertEquals(listOf("0", "10", "999"), result)
    }

    @Test
    fun `parseLsOutput returns empty for no numeric dirs`() {
        val lsOutput = listOf("obb", "self", "sdcard")
        val result = lsOutput.filter { it.toIntOrNull() != null }
        assertTrue(result.isEmpty())
    }

    // --- readStatFs 解析测试 ---

    @Test
    fun `parseDfOutput parses df output correctly`() {
        val dfOutput = listOf(
            "Filesystem     1K-blocks     Used Available Use% Mounted on",
            "/dev/fuse      244219904 83627584 160432352  35% /storage/emulated"
        )
        val dataLine = dfOutput.lastOrNull { it.startsWith("/dev") } ?: ""
        val parts = dataLine.split(Regex("\\s+"))
        assertTrue(parts.size >= 4)
        val totalKb = parts[1].toLong()
        val availableKb = parts[3].toLong()
        val totalBytes = totalKb * 1024
        val availableBytes = availableKb * 1024
        assertEquals(244219904L * 1024, totalBytes)
        assertEquals(160432352L * 1024, availableBytes)
    }

    // --- calculateSize 解析测试 ---

    @Test
    fun `parseDuOutput parses du -sb output correctly`() {
        val duOutput = "12345678\t/storage/emulated/0/AndroidDataBackup"
        val size = duOutput.split(Regex("\\s+")).firstOrNull()?.toLongOrNull() ?: 0L
        assertEquals(12345678L, size)
    }

    @Test
    fun `parseDuOutput handles empty output`() {
        val duOutput = ""
        val size = duOutput.split(Regex("\\s+")).firstOrNull()?.toLongOrNull() ?: 0L
        assertEquals(0L, size)
    }

    // --- AdbResult 测试 ---

    @Test
    fun `AdbResult outString joins lines correctly`() {
        val result = AdbResult(
            isSuccess = true,
            out = listOf("line1", "line2", "line3"),
            err = emptyList(),
            exitCode = 0
        )
        assertEquals("line1\nline2\nline3", result.outString)
    }

    @Test
    fun `AdbResult errString joins lines correctly`() {
        val result = AdbResult(
            isSuccess = false,
            out = emptyList(),
            err = listOf("error1", "error2"),
            exitCode = 1
        )
        assertEquals("error1\nerror2", result.errString)
    }
}
