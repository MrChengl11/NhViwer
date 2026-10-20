package com.mangaviewer.app.data.repo

import com.mangaviewer.app.data.AppContainer
import com.mangaviewer.app.data.model.TagUi
import com.mangaviewer.app.data.model.toUi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * tag 字母索引。nhentai v2 的 /api/v2/tags/{type} 响应尾部自带 alphabet：
 *   {"#":[1,1],"A":[1,3],"B":[3,4], ... "Z":[40,40]}
 * 值是 [起始页, 结束页]，1 基、闭区间；页大小由 per_page 决定
 * （实测 per_page=100 时 total=4753 / num_pages=40）。
 *
 * 取某字母的 tag = 拉该区间内所有页，再按名字首字母过滤（区间边界会混入相邻字母）。
 */
class TagIndexRepository(private val container: AppContainer) {

    private class Index(
        val type: String,
        var alphabet: Map<String, List<Int>> = emptyMap(),
        var total: Int = 0,
    ) {
        val byLetter = mutableMapOf<String, List<TagUi>>()
        val loading = mutableSetOf<String>()
    }

    private val mutex = Mutex()
    private val indexes = mutableMapOf<String, Index>()

    private suspend fun api() = container.api(container.settings.current().effectiveBase)

    private suspend fun ensureIndex(type: String): Index = mutex.withLock {
        indexes[type]?.let { return it }
        val idx = Index(type)
        indexes[type] = idx
        idx
    }

    /** 首次访问某类型时拉第一页，拿到 alphabet 与 total。 */
    private suspend fun loadAlphabetIfEmpty(type: String): Index {
        val idx = ensureIndex(type)
        val needLoad = mutex.withLock { idx.alphabet.isEmpty() }
        if (!needLoad) return idx
        val page = api().tags(type = type, page = 1, perPage = PER_PAGE, sort = "name")
        mutex.withLock {
            if (idx.alphabet.isEmpty()) {
                idx.alphabet = page.alphabet
                idx.total = page.total ?: 0
            }
        }
        return idx
    }

    suspend fun alphabet(type: String): Map<String, List<Int>> = loadAlphabetIfEmpty(type).alphabet

    suspend fun totalTags(type: String): Int = loadAlphabetIfEmpty(type).total

    /**
     * 取某字母下的全部 tag（按 count 降序，count 相同按名字升序）。
     * @param letter "#" 或 "A".."Z"（大小写不敏感）
     */
    suspend fun tagsForLetter(type: String, letter: String): List<TagUi> {
        val key = letter.uppercase()
        val idx = loadAlphabetIfEmpty(type)

        mutex.withLock { idx.byLetter[key]?.let { return it } }
        val range = idx.alphabet[key] ?: run {
            mutex.withLock { idx.byLetter[key] = emptyList() }
            return emptyList()
        }

        val claimed = mutex.withLock {
            if (key in idx.loading) false else { idx.loading += key; true }
        }
        // 已有并发请求在拉同一字母：等它填缓存
        if (!claimed) {
            while (!mutex.withLock { idx.byLetter.containsKey(key) }) {
                kotlinx.coroutines.delay(80)
            }
            return mutex.withLock { idx.byLetter[key].orEmpty() }
        }

        val collected = mutableListOf<TagUi>()
        try {
            val from = range.getOrNull(0) ?: 1
            val to = range.getOrNull(1) ?: from
            for (pageNo in from..to) {
                val page = api().tags(type = type, page = pageNo, perPage = PER_PAGE, sort = "name")
                if (page.result.isEmpty()) break
                page.result.map { it.toUi() }
                    .filter { letterOf(it.name) == key }
                    .let { collected += it }
            }
        } finally {
            mutex.withLock { idx.loading -= key }
        }

        val sorted = collected
            .distinctBy { it.id }
            .sortedWith(compareByDescending<TagUi> { it.count }.thenBy { it.name })
        mutex.withLock { idx.byLetter[key] = sorted }
        return sorted
    }

    /** 已缓存的字母列表（null 表示尚未加载）。 */
    fun cached(type: String, letter: String): List<TagUi>? =
        indexes[type]?.byLetter?.get(letter.uppercase())

    companion object {
        const val PER_PAGE = 100

        /** nhentai 的字母表键：# A..Z */
        val LETTERS: List<String> = listOf("#") + ('A'..'Z').map { it.toString() }

        /** tag 名首字母；非 A-Z 归到 "#"。 */
        fun letterOf(name: String): String {
            val c = name.trim().firstOrNull() ?: return "#"
            val up = c.uppercaseChar()
            return if (up in 'A'..'Z') up.toString() else "#"
        }
    }
}
