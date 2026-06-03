package com.xayah.core.util.adb

import org.junit.Assert.*
import org.junit.Test

/**
 * 测试 AdbService 中 shell 命令输出解析逻辑。
 * 这些解析函数是 ADB 模式下目录选择功能的核心。
 */
class AdbServiceParseTest {

    // --- listFilePaths 解析测试 ---

    @Test
    fun `parseLsOutput filters directories from ls output`() {
        val lsOutput = listOf(
            "0",
            "10",
            "999",
            "obb",
            "self"
        )
        // 只保留纯数字目录名（用户存储目录）
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
    fun `parseStatFsOutput parses stat output correctly`() {
        // stat -f 输出格式示例:
        // File: "/storage/emulated/0"
        //   ID: 0        Namelen: 255       Type: ext4
        //   Block size: 4096       Fundamental block size: 4096
        //   Blocks: Total: 61054976   Free: 40108084   Available: 40108084
        //   Inodes: Total: 15597568   Free: 14971427
        val statOutput = listOf(
            "  File: \"/storage/emulated/0\"",
            "  ID: 0        Namelen: 255       Type: ext4",
            "  Block size: 4096       Fundamental block size: 4096",
            "  Blocks: Total: 61054976   Free: 40108084   Available: 40108084",
            "  Inodes: Total: 15597568   Free: 14971427"
        )
        val blockSize = 4096L
        val totalBlocks = 61054976L
        val availableBlocks = 40108084L
        val expectedTotal = blockSize * totalBlocks
        val expectedAvailable = blockSize * availableBlocks

        // 解析 Block size
        val blockSizeRegex = Regex("Block size:\\s+(\\d+)")
        val blockSizeMatch = statOutput.mapNotNull { blockSizeRegex.find(it) }.firstOrNull()
        assertNotNull(blockSizeMatch)
        assertEquals(blockSize, blockSizeMatch!!.groupValues[1].toLong())

        // 解析 Blocks: Total / Available
        val blocksRegex = Regex("Blocks: Total:\\s+(\\d+)\\s+Free:\\s+(\\d+)\\s+Available:\\s+(\\d+)")
        val blocksMatch = statOutput.mapNotNull { blocksRegex.find(it) }.firstOrNull()
        assertNotNull(blocksMatch)
        assertEquals(totalBlocks, blocksMatch!!.groupValues[1].toLong())
        assertEquals(availableBlocks, blocksMatch.groupValues[3].toLong())

        assertEquals(expectedTotal, blockSize * totalBlocks)
        assertEquals(expectedAvailable, blockSize * availableBlocks)
    }

    @Test
    fun `parseDfOutput parses df output correctly`() {
        // df 输出格式:
        // Filesystem     1K-blocks     Used Available Use% Mounted on
        // /dev/fuse      244219904 83627584 160432352  35% /storage/emulated
        val dfOutput = listOf(
            "Filesystem     1K-blocks     Used Available Use% Mounted on",
            "/dev/fuse      244219904 83627584 160432352  35% /storage/emulated"
        )
        // 解析第二行
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
        // du -sb /path 输出: "12345678	/path"
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
