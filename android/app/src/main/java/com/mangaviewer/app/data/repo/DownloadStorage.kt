package com.mangaviewer.app.data.repo
import com.mangaviewer.app.i18n.AppLocale


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
        if (rel.isBlank() || rel == "/") AppLocale.strings().settings.storageRoot else rel
    }

    /** 默认目录的绝对路径（给设置页展示）。 */
    fun internalText(): String = internalRoot.absolutePath

    // ---------------- 查询 ----------------

    private data class Doc(
        val uri: Uri,
        val name: String,
        val mime: String?,
        val size: Long,
        /** 部分 DocumentsProvider 不返回 SIZE；未知不等于空文件。 */
        val sizeKnown: Boolean,
        val lastModified: Long,
    ) {
        val hasContent: Boolean
            get() = mime != Document.MIME_TYPE_DIR && if (sizeKnown) size > 0L else true
    }

    /** 扫描结果：一个本地漫画目录（内部目录或 SAF 目录）里实际存在的内容。 */
    data class LocalGallery(
        val galleryId: Long,
        val pageCount: Int,
        /** 目录里最大的页号（页号有缺口时用它判断是否下完）。 */
        val maxPage: Int,
        val bytes: Long,
        /** 最早一页的修改时间，用作「下载日期」；取不到时退回目录时间。 */
        val lastModified: Long,
        /** 最低页号文件的本地路径 / content URI，用作补全前的封面。 */
        val cover: String?,
    )

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
                Document.COLUMN_LAST_MODIFIED,
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
                    sizeKnown = !c.isNull(3),
                    lastModified = if (c.isNull(4)) 0L else c.getLong(4),
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
        PAGE_NAME.find(name)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it >= 1 }

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
                    if (d.hasContent) merged.putIfAbsent(index, d.uri.toString())
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
            docs(dir).any { it.name == pageName(index) && it.hasContent }
        }
    }

    /**
     * 扫描「应用私有目录 + 所有历史 SAF 目录」里实际存在的漫画子目录。
     *
     * - 只认 `<galleryId>/0001.jpg` 这种由本应用写出的目录结构；
     * - 同一部漫画可能同时出现在多个目录，按页号去重合并（与阅读顺序一致）；
     * - 目录里一页都没有时忽略；读取失败（SAF 权限被回收）按空处理，不抛异常；
     * - 只读：绝不删除或改写任何文件。
     */
    fun scanLocalGalleries(): List<LocalGallery> {
        val pageSizes = HashMap<Long, MutableMap<Int, Long>>()
        val covers = HashMap<Long, Pair<Int, String>>()
        val earliest = HashMap<Long, Long>()
        val dirModified = HashMap<Long, Long>()

        fun record(id: Long, index: Int, size: Long, location: String, modified: Long) {
            pageSizes.getOrPut(id) { HashMap() }.putIfAbsent(index, size)
            val cover = covers[id]
            if (cover == null || index < cover.first) covers[id] = index to location
            if (modified > 0L) {
                earliest[id] = minOf(earliest[id] ?: Long.MAX_VALUE, modified)
            }
        }

        internalRoot.listFiles().orEmpty().forEach { dir ->
            if (!dir.isDirectory) return@forEach
            val id = dir.name.toLongOrNull() ?: return@forEach
            dir.listFiles().orEmpty().forEach { file ->
                val index = indexOf(file.name) ?: return@forEach
                if (file.length() > 0L) {
                    record(id, index, file.length(), file.absolutePath, file.lastModified())
                }
            }
            dirModified[id] = maxOf(dirModified[id] ?: 0L, dir.lastModified())
        }

        readTrees().forEach { tree ->
            docs(rootDocUri(tree)).forEach child@{ child ->
                if (child.mime != Document.MIME_TYPE_DIR) return@child
                val id = child.name.toLongOrNull() ?: return@child
                docs(child.uri).forEach file@{ file ->
                    if (file.mime == Document.MIME_TYPE_DIR) return@file
                    val index = indexOf(file.name) ?: return@file
                    if (file.hasContent) {
                        record(id, index, file.size.coerceAtLeast(0L), file.uri.toString(), file.lastModified)
                    }
                }
                if (child.lastModified > 0L) {
                    dirModified[id] = maxOf(dirModified[id] ?: 0L, child.lastModified)
                }
            }
        }

        return pageSizes.mapNotNull { (id, pages) ->
            if (pages.isEmpty()) null else LocalGallery(
                galleryId = id,
                pageCount = pages.size,
                maxPage = pages.keys.maxOrNull() ?: pages.size,
                bytes = pages.values.sum(),
                lastModified = earliest[id] ?: dirModified[id] ?: 0L,
                cover = covers[id]?.second,
            )
        }.sortedByDescending { it.lastModified }
    }

    fun countExisting(galleryId: Long, total: Int): Int {
        val present = HashSet<Int>()
        internalGalleryDir(galleryId).listFiles()?.forEach { f ->
            indexOf(f.name)?.let { if (f.length() > 0L) present += it }
        }
        readTrees().forEach { tree ->
            findGalleryDir(tree, galleryId)?.let { dir ->
                docs(dir).forEach { d -> indexOf(d.name)?.let { if (d.hasContent) present += it } }
            }
        }
        return present.count { it in 1..total }
    }

    fun galleryBytes(galleryId: Long): Long {
        var sum = internalGalleryDir(galleryId).listFiles()?.sumOf { it.length() } ?: 0L
        readTrees().forEach { tree ->
            findGalleryDir(tree, galleryId)?.let { dir ->
                sum += docs(dir).sumOf { if (it.sizeKnown) it.size else 0L }
            }
        }
        return sum
    }

    // ---------------- 写入 / 删除 ----------------

    fun writePage(galleryId: Long, index: Int, input: InputStream): Boolean {
        val tree = treeUri()
        if (tree != null) {
            val ok = runCatching {
                val dir = ensureGalleryDir(tree, galleryId) ?: error(AppLocale.strings().messages.cannotCreateDir)
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
        /**
         * 读取 / 扫描时放宽到 1–6 位页码与常见图片扩展名，便于识别用户手动放进目录的页面；
         * 应用自己写入仍固定使用 `%04d.jpg`。
         */
        val PAGE_NAME = Regex("""^(\d{1,6})\.(?:jpe?g|png|webp)$""", RegexOption.IGNORE_CASE)
    }
}
