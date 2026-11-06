package com.mangaviewer.app.i18n

/**
 * 字符串表的格式化辅助：把「模板 + 参数」收成有名字的函数，调用点读起来接近自然语言。
 * 模板统一用 java.util.Formatter 语法（%1$d / %2$s），中文与英文共用同一套参数位置。
 */
fun UiStrings.fmt(template: String, vararg args: Any?): String =
    String.format(language.locale, template, *args)

// ---- 通用 ----
fun UiStrings.statsPages(pages: Int, favorites: Long): String = fmt(common.statsPages, pages, favorites)
fun UiStrings.statsDetail(pages: Int, favorites: Long, comments: Int): String =
    fmt(common.statsDetail, pages, favorites, comments)

// ---- 首页 / 搜索 ----
fun UiStrings.noResultFor(query: String): String = fmt(home.noResultFor, query)

// ---- 阅读器 ----
fun UiStrings.pageN(page: Int): String = fmt(reader.pageN, page)
fun UiStrings.pageOfTotal(page: Int, total: Int): String = fmt(reader.pageOfTotal, page, total)

// ---- 下载 / 收藏 ----
fun UiStrings.downloadsTitle(count: Int): String = fmt(downloads.title, count)
fun UiStrings.favoritesTitle(count: Int): String = fmt(favorites.title, count)
fun UiStrings.selectedCount(count: Int): String = fmt(favorites.selectedN, count)
fun UiStrings.allCount(count: Int): String = fmt(favorites.allN, count)
fun UiStrings.bulkFailed(count: Int): String = fmt(favorites.bulkFailed, count)
fun UiStrings.bulkOk(count: Int): String = fmt(favorites.bulkOk, count)
fun UiStrings.bulkMixed(ok: Int, failed: Int): String = fmt(favorites.bulkMixed, ok, failed)
fun UiStrings.confirmRemove(count: Int): String = fmt(favorites.confirmRemove, count)
fun UiStrings.removedCount(count: Int): String = fmt(favorites.removedN, count)

// ---- 账号 ----
fun UiStrings.syncDone(pulled: Int, pushed: Int): String = fmt(account.syncDone, pulled, pushed)
fun UiStrings.syncPushFailed(count: Int): String = fmt(account.syncPushFailed, count)
fun UiStrings.loggedInAs(name: String): String = fmt(account.loggedInAs, name)
fun UiStrings.loginSuccess(name: String): String = fmt(account.loginSuccess, name)

// ---- 历史 ----
fun UiStrings.historyProgress(page: Int, total: Int, date: String): String =
    fmt(history.progress, page, total, date)

// ---- 评论 ----
fun UiStrings.commentsTitle(count: Int): String = fmt(comments.title, count)
fun UiStrings.writeComment(min: Int, max: Int): String = fmt(comments.write, min, max)

// ---- 标签 ----
fun UiStrings.loadedOf(loaded: Int, total: Int): String = fmt(tagBrowse.loadedOf, loaded, total)
fun UiStrings.worksCount(count: Long): String = fmt(tagBrowse.works, count)
fun UiStrings.startsWith(letter: String): String = fmt(tagBrowse.startsWith, letter)

// ---- 设置 ----
fun UiStrings.preloadPages(count: Int): String = fmt(settings.preloadPages, count)
fun UiStrings.concurrentDownloads(count: Int): String = fmt(settings.concurrentDownloads, count)
fun UiStrings.directNote(base: String): String = fmt(settings.directNote, base)
fun UiStrings.customDir(label: String): String = fmt(settings.customDir, label)

// ---- 数据层 / 通知 ----
fun UiStrings.powFailed(attempts: Long): String = fmt(messages.powFailed, attempts)
fun UiStrings.commentLength(min: Int, max: Int): String = fmt(messages.commentLength, min, max)
fun UiStrings.failedPages(count: Int): String = fmt(messages.failedPages, count)
fun UiStrings.activeTasks(count: Int): String = fmt(service.activeTasks, count)
fun UiStrings.notificationLine(title: String, done: Int, total: Int): String =
    fmt(service.notificationLine, title, done, total)
