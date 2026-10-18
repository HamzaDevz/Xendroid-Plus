package xendroid.compose.saves

import java.io.File
import xendroid.compose.archive.ArchiveFiles

/** One save package of a profile, by the name the game gave it in its header. */
data class SaveEntry(
    /** The package's folder name (what the game looks it up by). */
    val file: String,
    /** The game's own name for it, from the header; null when there is none or it is unreadable. */
    val displayName: String?,
    val files: Int,
    val bytes: Long,
    val lastModified: Long,
)

/**
 * The saves of one profile for one title, named as the game named them: each package under
 * `content/<XUID>/<TID>/00000001/` and, when the game wrote one, its header file under
 * `Headers/00000001/<package>.header` (an XCONTENT_AGGREGATE_DATA, as the core writes it).
 * Only read, bounded and validated; a header that does not parse leaves the folder name.
 */
object SaveHeaders {
    const val SAVE_TYPE = "00000001"
    /** sizeof(XCONTENT_AGGREGATE_DATA). */
    const val HEADER_SIZE = 0x148
    private const val NAME_OFFSET = 0x8
    private const val NAME_BYTES = 0x100
    private const val FILE_OFFSET = 0x108
    private const val FILE_BYTES = 42
    const val MAX_PACKAGES = 200

    private val xuidPattern = Regex("[0-9A-F]{16}")
    private val titlePattern = Regex("[0-9A-F]{8}")

    /** The display name in a header's bytes: UTF-16 big-endian up to the first NUL, printable; else null. */
    fun displayName(header: ByteArray): String? {
        if (header.size < HEADER_SIZE) return null
        val raw = header.copyOfRange(NAME_OFFSET, NAME_OFFSET + NAME_BYTES)
        val text = String(raw, Charsets.UTF_16BE).substringBefore('\u0000').trim()
        if (text.isEmpty() || text.any { it.isISOControl() || it == '�' }) return null
        return text.take(128)
    }

    /** The package's file name stored in the header (ASCII, at most 42 bytes). */
    fun fileName(header: ByteArray): String? {
        if (header.size < HEADER_SIZE) return null
        val raw = header.copyOfRange(FILE_OFFSET, FILE_OFFSET + FILE_BYTES)
        val end = raw.indexOf(0).let { if (it < 0) raw.size else it }
        return String(raw, 0, end, Charsets.US_ASCII).takeIf { s -> s.isNotEmpty() && s.all { it in ' '..'~' } }
    }

    /** [xuid]'s saves of [titleId] under [contentRoot], newest first. */
    fun list(contentRoot: File, xuid: String, titleId: String): List<SaveEntry> {
        val id = xuid.uppercase()
        val tid = titleId.uppercase()
        require(xuidPattern.matches(id)) { "Invalid XUID" }
        require(titlePattern.matches(tid)) { "Invalid Title ID" }
        val saves = ArchiveFiles.resolve(contentRoot, "$id/$tid/$SAVE_TYPE")
        if (!saves.isDirectory) return emptyList()
        val headers = ArchiveFiles.resolve(contentRoot, "$id/$tid/Headers/$SAVE_TYPE")
        // .xd-* is what a save unpack in the core cut short leaves.
        return saves.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith(".xd-") }.take(MAX_PACKAGES).map { pkg ->
            val files = pkg.walkTopDown().maxDepth(16).filter { it.isFile }.toList()
            val header = File(headers, "${pkg.name}.header").takeIf { it.isFile && it.length() in HEADER_SIZE.toLong()..4096L }
            val name = header?.let { runCatching { displayName(it.readBytes()) }.getOrNull() }
            SaveEntry(pkg.name, name, files.size, files.sumOf { it.length() },
                (files.maxOfOrNull { it.lastModified() } ?: pkg.lastModified()))
        }.sortedByDescending { it.lastModified }
    }
}
