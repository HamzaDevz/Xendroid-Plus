package xendroid.compose.saves

import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveHeadersTest {
    /** An XCONTENT_AGGREGATE_DATA as the core writes it (big-endian, UTF-16BE name). */
    private fun header(name: String, file: String, size: Int = SaveHeaders.HEADER_SIZE): ByteArray {
        val b = ByteBuffer.allocate(size)
        b.putInt(1).putInt(1)                                   // device, content type (save)
        val chars = name.toByteArray(Charsets.UTF_16BE)
        b.position(0x8); b.put(chars, 0, minOf(chars.size, 0x100))
        b.position(0x108); b.put(file.toByteArray(Charsets.US_ASCII))
        return b.array()
    }

    @Test fun readsTheNameAndFileTheGameGave() {
        val h = header("Campanha · Lendário", "SAVE0001")
        assertEquals("Campanha · Lendário", SaveHeaders.displayName(h))
        assertEquals("SAVE0001", SaveHeaders.fileName(h))
    }

    @Test fun refusesShortOrGarbledHeaders() {
        assertNull(SaveHeaders.displayName(ByteArray(0x40)))
        assertNull(SaveHeaders.displayName(header("", "X")))
        // Control characters are not a name.
        assertNull(SaveHeaders.displayName(header("bad\u0007name", "X")))
        // A name filling the whole field (no terminator) is still read, bounded.
        assertEquals(128, SaveHeaders.displayName(header("A".repeat(200), "X"))?.length)
    }

    @Test fun listsAProfilesSavesNewestFirstWithTheirNames() {
        val root = Files.createTempDirectory("content").toFile()
        val xuid = "E03000000A1B2C3D"
        val saves = File(root, "$xuid/4D5307E6/00000001").apply { mkdirs() }
        val headers = File(root, "$xuid/4D5307E6/Headers/00000001").apply { mkdirs() }
        File(saves, "PROFILE").apply { mkdirs(); File(this, "data").writeBytes(ByteArray(512)) }.also { it.setLastModified(1_000_000L) }
        File(File(saves, "PROFILE"), "data").setLastModified(1_000_000L)
        File(saves, "CAMPAIGN").apply { mkdirs(); File(this, "a").writeBytes(ByteArray(2048)); File(this, "b").writeBytes(ByteArray(100)) }
        File(File(saves, "CAMPAIGN"), "a").setLastModified(5_000_000L)
        File(File(saves, "CAMPAIGN"), "b").setLastModified(4_000_000L)
        File(headers, "CAMPAIGN.header").writeBytes(header("Campanha · Lendário", "CAMPAIGN"))
        File(saves, ".xd-unpack-CAMPAIGN").apply { mkdirs(); File(this, "a").writeBytes(ByteArray(16)) }
        val list = SaveHeaders.list(root, xuid.lowercase(), "4d5307e6")
        assertEquals(listOf("CAMPAIGN", "PROFILE"), list.map { it.file })
        assertEquals("Campanha · Lendário", list[0].displayName)
        assertNull(list[1].displayName)
        assertEquals(2, list[0].files)
        assertEquals(2148L, list[0].bytes)
        assertEquals(5_000_000L, list[0].lastModified)
        assertTrue(SaveHeaders.list(root, xuid, "4D530000").isEmpty())
        root.deleteRecursively()
    }

    @Test(expected = IllegalArgumentException::class) fun refusesAPathInsteadOfAXuid() {
        SaveHeaders.list(Files.createTempDirectory("content").toFile(), "../../etc", "4D5307E6")
    }
}
