package com.mangaviewer.app.data.repo

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.File
import java.io.InputStream

/**
 * 下载页面的落地存储。
 *
 * - 默认写到应用私有目录 `filesDir/downloads/<galleryId>/0001.jpg`；
 * - 用户在设置里用系统目录选择器选过目录后，新下载写到 SAF 授权的目录树里
 *   （每部漫画一个 `<galleryId>` 子目录，文件名仍是 0001.jpg…）。
 *
 * 读取时会把「内部目录 + 自定义目录」合并，所以切换目录不会让已下载的内容消失。
 * 自定义目录被删除或权限被回收时，写入会自动回退到应用私有目录，避免整部漫画下载失败。
 */
internal class DownloadStorage(
    private val context: Context,
    /** 返回当前自定义目录的 SAF tree URI；空字符串表示使用默认目录。 */
    private val treeProvider: () -> String,
    /** 历史选择过的目录（同样用于读取，避免恢复默认后旧下载读不到）。 */
    private val historyProvider: () -> List<String> = { emptyList() },
) {

    private val resolver get() = context.contentResolver

    val internalRoot: File
        get() = File(context.filesDir, "downloads").also { it.mkdirs() }

    private fun parseTree(raw: String): Uri? =
        raw.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }

    private fun treeUri(): Uri? = parseTree(treeProvider())

    /** 当前目录 + 历史目录，去重；读取时全部参与。 */
    private fun readTrees(): List<Uri> = buildList {
        treeUri()?.let { add(it) }
        historyProvider().forEach { raw -> parseTree(raw)?.let { add(it) } }
    }.distinct()

    /** 自定义目录是否配置过（不代表还有读写权限）。 */
    fun hasCustomTree(): Boolean = treeUri() != null

    /** 给设置页显示的可读路径，例如 `Download/NhViwer`。未配置时返回 null。 */
    fun customTreeText(): String? = treeUri()?.let { tree ->
        val docId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull().orEmpty()
        val rel = docId.substringAfter(':', docId)
        if (rel.isBlank() || rel == "/") "存储根目录" else rel
    }

    /** 默认目录的绝对路径（给设置页展示）。 */
    fun internalText(): String = internalRoot.absolutePath

    // ---------------- 查询 ----------------

    private data class Doc(val uri: Uri, val name: String, val mime: String?, val size: Long)

    private fun rootDocUri(tree: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private fun docs(parent: Uri): List<Doc> = runCatching {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            parent,
            DocumentsContract.getDocumentId(parent),
        )
        val out = mutableListOf<Doc>()
        resolver.query(
            children,
            arrayOf(
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_MIME_TYPE,
                Document.COLUMN_SIZE,
            ),
            null,
            null,
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                out += Doc(
                    uri = DocumentsContract.buildDocumentUriUsingTree(parent, id),
                    name = c.getString(1).orEmpty(),
                    mime = c.getString(2),
                    size = if (c.isNull(3)) 0L else c.getLong(3),
                )
            }
        }
        out
    }.getOrDefault(emptyList())

    private fun findGalleryDir(tree: Uri, galleryId: Long): Uri? =
        docs(rootDocUri(tree))
            .firstOrNull { it.mime == Document.MIME_TYPE_DIR && it.name == galleryId.toString() }
            ?.uri

    private fun ensureGalleryDir(tree: Uri, galleryId: Long): Uri? =
        findGalleryDir(tree, galleryId) ?: runCatching {
            DocumentsContract.createDocument(
                resolver,
                rootDocUri(tree),
                Document.MIME_TYPE_DIR,
                galleryId.toString(),
            )
        }.getOrNull()

    // ---------------- 读取 ----------------

    private fun indexOf(name: String): Int? =
        PAGE_NAME.find(name)?.groupValues?.get(1)?.toIntOrNull()

    /** 某部漫画本地已有的页面，按页号排序；内部路径 / content:// URI 混排。 */
    fun localSources(galleryId: Long): List<String> {
        val merged = sortedMapOf<Int, String>()
        internalGalleryDir(galleryId).listFiles()?.forEach { f ->
            val index = indexOf(f.name) ?: return@forEach
            if (f.length() > 0L) merged[index] = f.absolutePath
        }
        readTrees().forEach { tree ->
            findGalleryDir(tree, galleryId)?.let { dir ->
                docs(dir).forEach { d ->
                    val index = indexOf(d.name) ?: return@forEach
                    if (d.size > 0L) merged.putIfAbsent(index, d.uri.toString())
                }
            }
        }
        return merged.values.toList()
    }

    fun exists(galleryId: Long, index: Int): Boolean {
        val file = File(internalGalleryDir(galleryId), pageName(index))
        if (file.length() > 0L) return true
        return readTrees().any { tree ->
            val dir = findGalleryDir(tree, galleryId) ?: return@any false
            docs(dir).any { it.name == pageName(index) && it.size > 0L }
        }
    }

    fun countExisting(galleryId: Long, total: Int): Int {
        val present = HashSet<Int>()
        internalGalleryDir(galleryId).listFiles()?.forEach { f ->
            indexOf(f.name)?.let { if (f.length() > 0L) present += it }
        }
        readTrees().forEach { tree ->
            findGalleryDir(tree, galleryId)?.let { dir ->
                docs(dir).forEach { d -> indexOf(d.name)?.let { if (d.size > 0L) present += it } }
            }
        }
        return present.count { it in 1..total }
    }

    fun galleryBytes(galleryId: Long): Long {
        var sum = internalGalleryDir(galleryId).listFiles()?.sumOf { it.length() } ?: 0L
        readTrees().forEach { tree ->
            findGalleryDir(tree, galleryId)?.let { dir -> sum += docs(dir).sumOf { it.size } }
        }
        return sum
    }

    // ---------------- 写入 / 删除 ----------------

    fun writePage(galleryId: Long, index: Int, input: InputStream): Boolean {
        val tree = treeUri()
        if (tree != null) {
            val ok = runCatching {
                val dir = ensureGalleryDir(tree, galleryId) ?: error("无法创建漫画目录")
                writeSaf(dir, pageName(index), input)
            }.getOrDefault(false)
            if (ok) return true
            // 目录被删 / 权限被回收：回退到应用私有目录。
        }
        return writeInternal(galleryId, index, input)
    }

    private fun writeSaf(dir: Uri, name: String, input: InputStream): Boolean {
        val existing = docs(dir).firstOrNull { it.name == name && it.mime != Document.MIME_TYPE_DIR }?.uri
        val target = existing
            ?: DocumentsContract.createDocument(resolver, dir, "image/jpeg", name)
            ?: return false
        resolver.openOutputStream(target, "wt")?.use { out -> input.copyTo(out) } ?: return false
        return true
    }

    private fun writeInternal(galleryId: Long, index: Int, input: InputStream): Boolean {
        val dir = internalGalleryDir(galleryId).also { it.mkdirs() }
        val target = File(dir, pageName(index))
        val temp = File(dir, pageName(index) + ".part")
        return runCatching {
            temp.outputStream().use { out -> input.copyTo(out) }
            if (target.exists()) target.delete()
            temp.renameTo(target)
        }.getOrDefault(false)
    }

    fun deleteGallery(galleryId: Long) {
        internalGalleryDir(galleryId).deleteRecursively()
        readTrees().forEach { tree ->
            findGalleryDir(tree, galleryId)?.let { dir ->
                runCatching { DocumentsContract.deleteDocument(resolver, dir) }
            }
        }
    }

    fun cleanupOrphans(validIds: Set<Long>) {
        internalRoot.listFiles()?.forEach { f ->
            val id = f.name.toLongOrNull()
            if (f.isDirectory && id != null && id !in validIds) f.deleteRecursively()
        }
        readTrees().forEach { tree ->
            docs(rootDocUri(tree)).forEach { d ->
                val id = d.name.toLongOrNull()
                if (d.mime == Document.MIME_TYPE_DIR && id != null && id !in validIds) {
                    runCatching { DocumentsContract.deleteDocument(resolver, d.uri) }
                }
            }
        }
    }

    private fun internalGalleryDir(galleryId: Long): File = File(internalRoot, galleryId.toString())

    private fun pageName(index: Int): String = "%04d.jpg".format(index)

    private companion object {
        val PAGE_NAME = Regex("""^(\d{4})\.jpg$""")
    }
}
