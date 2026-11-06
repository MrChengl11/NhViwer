#!/usr/bin/env python3
"""Generate the Kotlin UI string table (English + Simplified Chinese).

Source of truth for all user-facing text. Edit SECTIONS below, then run:

    python tools/i18n/gen_strings.py

It rewrites app/src/main/java/com/mangaviewer/app/i18n/Strings.kt.
Format placeholders use java.util.Formatter syntax (%1$d, %2$s ...).
"""

from pathlib import Path
import re
import sys

# (section, [(key, english, simplified_chinese), ...])
SECTIONS = [
    ("common", [
        ("retry", "Retry", "重试"),
        ("noImage", "No image", "无图"),
        ("selected", "Selected", "已选中"),
        ("loadFailed", "Failed to load", "加载失败"),
        ("back", "Back", "返回"),
        ("cancel", "Cancel", "取消"),
        ("delete", "Delete", "删除"),
        ("loading", "Loading…", "加载中…"),
        ("endOfList", "You've reached the end", "已经到底了"),
        ("swipeUpLoadMore", "Swipe up to load more", "上滑继续加载"),
        ("loadFailedTapRetry", "Failed to load. Tap to retry", "加载失败，点击重试"),
        ("noResults", "No results", "没有结果"),
        ("statsPages", "Pages %1$d · Saves %2$d", "%1$d 页 · %2$d 收藏"),
        ("statsDetail", "Pages %1$d · Saves %2$d · Comments %3$d", "%1$d 页 · %2$d 收藏 · %3$d 评论"),
    ]),
    ("nav", [
        ("home", "Home", "首页"),
        ("browse", "Browse", "分类"),
        ("favorites", "Favorites", "收藏"),
        ("downloads", "Downloads", "下载"),
        ("settings", "Settings", "设置"),
    ]),
    ("gate", [
        ("title", "Adult content notice", "成人内容提醒"),
        ("body1",
         "This app is for technical research and study only. All content comes from third-party sites; the app itself hosts nothing.\n\n",
         "本应用仅用于技术研究与学习，内容来自第三方站点，应用本身不托管任何内容。\n\n"),
        ("body2",
         "By continuing you confirm that you are at least 18 years old, and you accept responsibility for the risks and legal obligations of viewing such content.",
         "继续使用即表示你已年满 18 周岁，并自行承担查看相关内容的风险与法律责任。"),
        ("confirm", "I am 18 or older", "我已年满 18 周岁"),
        ("leave", "Under 18? Please uninstall", "未满 18 周岁请卸载"),
    ]),
    ("home", [
        ("latest", "Latest", "最新"),
        ("mostFavorites", "Most saved", "最多收藏"),
        ("searchFailed", "Search failed", "搜索失败"),
        ("searchHint", "Search tags / artists / keywords", "搜索标签 / 作者 / 关键词"),
        ("search", "Search", "搜索"),
        ("clearSearch", "Clear search field", "清空搜索框"),
        ("discover", "Discover manga", "发现漫画"),
        ("history", "History", "历史"),
        ("random", "Random", "随机"),
        ("enterKeyword", "Type a keyword to start searching", "输入关键词后开始搜索"),
        ("noResultFor", "No results for \u201c%1$s\u201d. Try another keyword.", "没有找到「%1$s」，试试别的关键词"),
        ("noContent", "Nothing here. Make sure the proxy service is running.", "暂无内容，确认反代服务已启动"),
    ]),
    ("search", [
        ("title", "Search", "搜索"),
        ("searchFailed", "Search failed", "搜索失败"),
        ("hint", "Search: tag: / artist: / language: / pages:>10", "搜索：tag: / artist: / language: / pages:>10"),
    ]),
    ("tagTypes", [
        ("tag", "Tag", "标签"),
        ("artist", "Artist", "作者"),
        ("parody", "Parody", "原作"),
        ("character", "Character", "角色"),
        ("group", "Circle", "社团"),
        ("language", "Language", "语言"),
        ("category", "Category", "分类"),
    ]),
    ("detail", [
        ("title", "Details", "详情"),
        ("favAddedLocal", "Added to favorites (local)", "已加入收藏（本地）"),
        ("favRemoved", "Removed from favorites", "已取消收藏"),
        ("favAddedSynced", "Added to favorites and synced", "已加入收藏并同步到账号"),
        ("favRemovedSynced", "Removed from favorites and synced", "已取消收藏并同步到账号"),
        ("favAddedSyncFailed", "Added to favorites (cloud sync failed)", "已加入收藏（云端同步失败）"),
        ("favRemovedSyncFailed", "Removed from favorites (cloud sync failed)", "已取消收藏（云端同步失败）"),
        ("favorite", "Favorite", "收藏"),
        ("startReading", "Start reading", "开始阅读"),
        ("queued", "Added to the download queue. Check Downloads for progress.", "已加入下载队列，可在「下载」页查看进度"),
        ("download", "Download", "下载"),
        ("related", "Related", "相关作品"),
    ]),
    ("reader", [
        ("localReading", "Local", "本地阅读"),
        ("noPages", "No pages", "没有页面"),
        ("zoomOut", "Zoom out", "缩小"),
        ("zoomIn", "Zoom in", "放大"),
        ("refreshPage", "Reload page", "刷新当前页"),
        ("switchLayout", "Switch layout", "切换布局"),
        ("pageN", "Page %1$d", "第 %1$d 页"),
        ("jumpToPage", "Jump to page", "跳转到页面"),
        ("pageOfTotal", "Page %1$d of %2$d", "共 %2$d 页 · 当前 %1$d"),
    ]),
    ("downloads", [
        ("filterRunning", "Downloading", "正在下载"),
        ("filterQueued", "Queued", "排队中"),
        ("filterPaused", "Paused / resumable", "已暂停 / 待继续"),
        ("filterFailed", "Failed", "失败"),
        ("filterDone", "Completed", "已完成"),
        ("title", "Downloads (%1$d)", "下载（%1$d）"),
        ("pauseAll", "Pause all", "全部暂停"),
        ("resumeAll", "Resume all", "全部继续"),
        ("empty", "No download tasks yet", "还没有下载任务"),
        ("headerTask", "Task", "任务"),
        ("headerProgress", "Progress", "进行中"),
        ("headerSize", "Size", "占用"),
        ("read", "Read", "阅读"),
        ("resume", "Resume", "继续"),
        ("pause", "Pause", "暂停"),
    ]),
    ("favorites", [
        ("title", "Favorites (%1$d)", "收藏（%1$d）"),
        ("bulkFailed", "Failed to queue %1$d item(s)", "加入下载队列失败（%1$d 本）"),
        ("bulkOk", "Queued %1$d item(s)", "已加入下载队列：%1$d 本"),
        ("bulkMixed", "Queued %1$d, %2$d failed", "已加入 %1$d 本，%2$d 本失败"),
        ("exitSelection", "Exit selection", "退出多选"),
        ("selectedN", "Selected %1$d", "已选 %1$d"),
        ("allN", "All (%1$d)", "全部 %1$d"),
        ("bulkDownloadSelected", "Download selected", "下载选中"),
        ("bulkDeleteSelected", "Delete selected", "删除选中"),
        ("empty", "No favorites yet. Tap the heart on a detail page.", "还没有收藏，去详情页点心形"),
        ("confirmRemove", "Remove %1$d favorites?", "取消收藏 %1$d 本？"),
        ("removedN", "Removed %1$d favorites", "已取消收藏 %1$d 本"),
        ("defaultLabel", "Default", "默认"),
    ]),
    ("history", [
        ("title", "History", "阅读历史"),
        ("clear", "Clear", "清空"),
        ("empty", "No reading history", "还没有阅读记录"),
        ("progress", "Page %1$d/%2$d · %3$s", "看到第 %1$d/%2$d 页 · %3$s"),
        ("resume", "Resume", "继续"),
    ]),
    ("account", [
        ("title", "Account", "账号"),
        ("login", "Sign in", "登录"),
        ("logout", "Sign out", "退出登录"),
        ("loggedIn", "Signed in", "已登录"),
        ("loggedInAs", "Signed in: %1$s", "已登录：%1$s"),
        ("noUsername", "(no username)", "（无用户名）"),
        ("noUsernameReturned", "(username not returned)", "（未返回用户名）"),
        ("authApiKey", "Auth: API Key", "认证方式：API Key"),
        ("syncing", "Syncing…", "同步中…"),
        ("syncFavorites", "Sync account favorites", "同步账号收藏"),
        ("syncDone", "Sync complete: pulled %1$d, pushed %2$d", "同步完成：拉取 %1$d 条，推送 %2$d 条"),
        ("syncPushFailed", " (%1$d failed)", "（%1$d 条推送失败）"),
        ("syncFailed", "Sync failed", "同步失败"),
        ("syncDesc",
         "Two-way sync: cloud favorites are added locally (local folders kept), and local extras are pushed to the cloud.",
         "双向同步：云端收藏会补到本地（保留本地文件夹），本地多出来的收藏会推送到云端。"),
        ("loggedOutMsg", "Signed out. Local favorites kept.", "已退出登录，本地收藏保留"),
        ("keyGeneratedOnce", "New API key generated (shown once)", "已生成 API Key（只显示一次）"),
        ("keyGenerated", "API key generated", "已生成 API Key"),
        ("generateFailed", "Generation failed: ", "生成失败："),
        ("generateKey", "Generate a new API key for this account", "为当前账号生成新 API Key"),
        ("keyNote",
         "The key is shown only once, so copy it now. Old keys keep working; you can delete them on the website.",
         "生成的 Key 只显示这一次，建议复制保存；旧 Key 可继续使用，也可到网页端删除。"),
        ("help1",
         "Sign in with an API key. On the website open the avatar menu (top right) → Settings → API Keys → New, then paste the generated key below.",
         "用 API Key 登录。网页端右上角头像 → 设置 → API Keys → 新建，把生成的字符串粘贴到下面。"),
        ("help2",
         "Why API key only: password sign-in is protected by Cloudflare Turnstile, and access tokens require a web login that is unreachable from this network. API keys are long-lived credentials issued for third-party clients.",
         "为什么只有 API Key：账号密码登录被服务端强制 Cloudflare Turnstile 人机验证，而访问令牌需要网页登录才能拿到（本机网络下网页登录不可达）。API Key 长期有效，是官方给第三方客户端的凭证。"),
        ("loginSuccess", "Signed in: %1$s", "登录成功：%1$s"),
        ("loginFailed", "Sign-in failed", "登录失败"),
        ("verifying", "Verifying…", "校验中…"),
        ("submitting", "Contacting server…", "正在请求服务端…"),
    ]),
    ("settings", [
        ("title", "Settings", "设置"),
        ("languageSection", "Language", "语言"),
        ("languageDesc", "Applies immediately to the whole app.", "立即应用到整个应用。"),
        ("languageEnglish", "English", "English"),
        ("languageChinese", "简体中文", "简体中文"),
        ("sectionAccount", "Account", "账号"),
        ("loggedOut", "Not signed in. Sign in to sync favorites; favorite changes will also sync to your account.", "未登录。登录后可以把账号收藏同步到本地，收藏/取消收藏也会同步到账号。"),
        ("accountDetails", "Account details", "账号详情"),
        ("goLogin", "Sign in", "去登录"),
        ("sectionNetwork", "Network", "网络连接"),
        ("networkHelp",
         "Choose Direct when the device itself can open nhentai.net (proxy or TUN on the phone/tablet); no desktop proxy is needed. Use Local proxy only when the device cannot connect on its own.",
         "设备自己能打开 nhentai.net（手机/平板上挂着代理或 TUN）时选「直连」，不需要电脑开反代；设备自己连不上时才需要「本地反代」。"),
        ("localProxy", "Local proxy", "本地反代"),
        ("directProxy", "Direct (via proxy)", "直连（已挂代理）"),
        ("directNote", "Direct mode: API via %1$s; images load straight from the image CDN.", "直连模式：API 走 %1$s，图片直接请求图床域名。"),
        ("proxyAddress", "Proxy address", "反代地址"),
        ("proxyHelp", "Run the proxy on the host first (default 8787), then run: adb reverse tcp:8787 tcp:8787.", "先在宿主机运行 proxy（默认 8787），再执行 adb reverse tcp:8787 tcp:8787。"),
        ("saved", "Saved", "已保存"),
        ("save", "Save", "保存"),
        ("connectFailed", "Connection failed", "连接失败"),
        ("directOk", "Direct OK", "直连正常"),
        ("proxyOk", "Proxy OK", "反代正常"),
        ("checking", "Checking…", "检测中…"),
        ("checkConnection", "Test connection", "检测连接"),
        ("sectionReading", "Reading", "阅读"),
        ("direction", "Page direction", "翻页方向"),
        ("rtl", "Manga (right to left)", "日漫（从右到左）"),
        ("ltr", "Left to right", "从左到右"),
        ("layout", "Layout", "布局"),
        ("preloadPages", "Preload pages: %1$d", "预加载页数：%1$d"),
        ("quality", "Image quality", "画质"),
        ("sectionAppearance", "Appearance", "外观"),
        ("darkMode", "Dark mode", "深色模式"),
        ("dynamicColor", "Dynamic color (Android 12+)", "动态取色（Android 12+）"),
        ("sectionDownloads", "Downloads", "下载"),
        ("saveLocation", "Save location", "保存位置"),
        ("customDir", "Custom folder: %1$s", "自定义目录：%1$s"),
        ("privateDir", "App-private folder (default)", "应用私有目录（默认）"),
        ("downloadDirHelp",
         "Each manga gets a subfolder named after its ID and pages are saved by number. Switching folders only affects new downloads; existing ones stay readable. Uninstalling removes app-private content.",
         "每部漫画在所选目录下建一个以 ID 命名的子文件夹，图片按页号保存；切换目录只影响之后的新下载，已下载的内容仍可正常阅读；卸载 App 会删除应用私有目录里的内容。"),
        ("changeDir", "Change download folder", "更改下载目录"),
        ("chooseDir", "Choose download folder", "选择下载目录"),
        ("defaultRestored", "Default download folder restored", "已恢复默认下载目录"),
        ("restoreDefault", "Restore default", "恢复默认"),
        ("dirUpdated", "Download folder updated. Applies to new downloads only.", "下载目录已更新，仅影响之后的新下载"),
        ("dirNoPermission", "No read/write permission for that folder; settings unchanged.", "没有获得该目录的读写权限，已保持原设置"),
        ("concurrentDownloads", "Concurrent downloads: %1$d", "并发下载：%1$d"),
        ("sectionAbout", "About", "关于"),
        ("aboutText",
         "Nhviwer is for technical research and study only. Content comes from third-party sites; the app hosts nothing. Follow your local laws and regulations.",
         "Nhviwer 仅用于技术研究与学习，内容来自第三方站点，应用不托管任何内容；请遵守当地法律法规。"),
        ("showGate", "Show adult notice again", "重新显示成人确认页"),
        ("storageRoot", "Storage root", "存储根目录"),
        ("layoutH", "Horizontal paging", "左右翻页"),
        ("layoutDouble", "Two-page spread", "双页跨页"),
        ("layoutV", "Vertical paging", "上下翻页"),
        ("layoutContinuous", "Continuous scroll", "连续滚动"),
        ("qualityOriginal", "Original", "原图"),
        ("qualityHigh", "High", "高清"),
        ("qualityMedium", "Medium", "中等"),
        ("qualityLow", "Data saver", "省流"),
    ]),
    ("tagBrowse", [
        ("title", "Categories", "分类"),
        ("digits", "Digits / symbols", "数字 / 符号"),
        ("startsWith", "Starts with %1$s", "%1$s 开头"),
        ("loadedOf", "%1$d of %2$d", "%1$d 个 · 共 %2$d 个"),
        ("empty", "No tags under this letter", "该字母下暂无标签"),
        ("works", "%1$d works", "%1$d 部"),
    ]),
    ("tag", [
        ("latest", "Latest", "最新"),
        ("popular", "Popular", "热门"),
    ]),
    ("comments", [
        ("title", "Comments (%1$d)", "评论（%1$d）"),
        ("empty", "No comments yet", "还没有评论"),
        ("write", "Write a comment (%1$d-%2$d characters)", "写评论（%1$d-%2$d 字）"),
        ("hideCaptcha", "Hide CAPTCHA", "收起验证码"),
        ("showCaptcha", "Need a CAPTCHA?", "需要验证码？"),
        ("tokenLabel", "Turnstile token (optional)", "Turnstile token（可选）"),
        ("tokenNote",
         "The site uses Cloudflare Turnstile, which cannot be solved automatically. If you solved it in a browser, paste the token.",
         "站点使用 Cloudflare Turnstile，无法自动通过；如在浏览器完成验证可粘贴 token。"),
        ("posted", "Comment posted", "评论已发布"),
        ("postFailed", "Failed to post", "发布失败"),
        ("solvingPow", "Solving PoW…", "正在求解 PoW…"),
        ("post", "Post comment", "发布评论"),
    ]),
    ("messages", [
        ("anonymous", "Anonymous", "匿名"),
        ("unauthorized", "Sign in required", "需要登录后操作"),
        ("rateLimited", "Too many requests. Try again shortly.", "请求过于频繁，请稍后再试"),
        ("invalidKey", "API key is invalid or expired. Regenerate it on the website: Account settings → API Keys.", "API Key 无效或已失效，请到网页端 账号设置 → API Keys 重新生成"),
        ("parseComments", "Failed to parse comments", "评论列表解析失败"),
        ("parseList", "Failed to parse list", "列表解析失败"),
        ("networkError", "Network error", "网络错误"),
        ("requestFailed", "Request failed", "请求失败"),
        ("powFailed", "PoW failed after %1$d attempts", "PoW 求解失败（尝试 %1$d 次）"),
        ("apiKeyEmpty", "API key must not be empty", "API Key 不能为空"),
        ("commentLength", "Comment must be %1$d-%2$d characters", "评论长度需在 %1$d-%2$d 字之间"),
        ("pageListEmpty", "Page list is empty", "页面列表为空"),
        ("downloadFailed", "Download failed", "下载失败"),
        ("failedPages", "%1$d pages failed. Tap Resume to retry.", "%1$d 页下载失败，可点继续重试"),
        ("statusQueued", "Queued", "排队中"),
        ("statusRunning", "Downloading", "下载中"),
        ("statusPaused", "Paused", "已暂停"),
        ("statusDone", "Completed", "已完成"),
        ("statusFailed", "Failed", "失败"),
        ("cannotCreateDir", "Cannot create manga folder", "无法创建漫画目录"),
    ]),
    ("service", [
        ("preparing", "Preparing download…", "准备下载…"),
        ("activeTasks", "%1$d downloads in progress", "%1$d 个任务下载中"),
        ("notificationLine", "%1$s · %2$d/%3$d pages", "%1$s · 已完成 %2$d/%3$d 页"),
        ("channelName", "Download progress", "下载进度"),
        ("notificationTitle", "Manga downloads", "漫画下载"),
        ("pauseAll", "Pause all", "全部暂停"),
        ("channelDesc", "Show manga download progress", "显示漫画下载进度"),
    ]),
]


PLACEHOLDER = re.compile(r"%(\d+)\$[a-zA-Z]")


def validate() -> None:
    """Fail fast on blank entries or EN/ZH placeholder mismatches."""
    problems = []
    for section, entries in SECTIONS:
        seen = set()
        for key, en, zh in entries:
            if key in seen:
                problems.append("%s.%s duplicated" % (section, key))
            seen.add(key)
            if not en.strip() or not zh.strip():
                problems.append("%s.%s is blank" % (section, key))
            a = sorted(PLACEHOLDER.findall(en))
            b = sorted(PLACEHOLDER.findall(zh))
            if a != b:
                problems.append("%s.%s placeholder mismatch: %r vs %r" % (section, key, en, zh))
    if problems:
        message = "i18n validation failed:\n  " + "\n  ".join(problems)
        raise SystemExit(message)


def kstr(s: str) -> str:
    s = s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")
    s = s.replace("\r", "").replace("\n", "\\n")
    return '"' + s + '"'


def emit() -> str:
    out = []
    out.append("// Generated by tools/i18n/gen_strings.py — do not edit by hand.")
    out.append("// Edit the SECTIONS table in that script and re-run it.")
    out.append("package com.mangaviewer.app.i18n")
    out.append("")
    out.append("import androidx.compose.runtime.staticCompositionLocalOf")
    out.append("import kotlinx.coroutines.flow.MutableStateFlow")
    out.append("import kotlinx.coroutines.flow.StateFlow")
    out.append("import kotlinx.coroutines.flow.asStateFlow")
    out.append("import java.util.Locale")
    out.append("")
    out.append("/** App UI language. English is the product default. */")
    out.append("enum class AppLanguage(val tag: String) {")
    out.append('    ENGLISH("en"),')
    out.append('    CHINESE_SIMPLIFIED("zh-CN");')
    out.append("")
    out.append("    val locale: Locale get() = Locale.forLanguageTag(tag)")
    out.append("")
    out.append("    companion object {")
    out.append("        fun fromTag(tag: String?): AppLanguage =")
    out.append("            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: ENGLISH")
    out.append("    }")
    out.append("}")
    out.append("")
    for section, entries in SECTIONS:
        cls = section[0].upper() + section[1:] + "Strings"
        out.append("data class %s(" % cls)
        for i, (key, en, zh) in enumerate(entries):
            comma = "," if i < len(entries) - 1 else ","
            out.append("    val %s: String%s" % (key, comma))
        out.append(")")
        out.append("")

    out.append("data class UiStrings(")
    out.append("    val language: AppLanguage,")
    for section, entries in SECTIONS:
        cls = section[0].upper() + section[1:] + "Strings"
        out.append("    val %s: %s," % (section, cls))
    out.append(")")
    out.append("")
    for lang_name, idx in (("EnStrings", 1), ("ZhStrings", 2)):
        lang_const = "AppLanguage.ENGLISH" if lang_name == "EnStrings" else "AppLanguage.CHINESE_SIMPLIFIED"
        out.append("val %s = UiStrings(" % lang_name)
        out.append("    language = %s," % lang_const)
        for section, entries in SECTIONS:
            out.append("    %s = %s(" % (section, section[0].upper() + section[1:] + "Strings"))
            for key, en, zh in entries:
                val = en if idx == 1 else zh
                out.append("        %s = %s," % (key, kstr(val)))
            out.append("    ),")
        out.append(")")
        out.append("")

    out.append("fun stringsFor(language: AppLanguage): UiStrings = when (language) {")
    out.append("    AppLanguage.ENGLISH -> EnStrings")
    out.append("    AppLanguage.CHINESE_SIMPLIFIED -> ZhStrings")
    out.append("}")
    out.append("")
    out.append("/** Compose entry point: screens read [LocalStrings].current. */")
    out.append("val LocalStrings = staticCompositionLocalOf<UiStrings> { EnStrings }")
    out.append("")
    out.append("/** Process-wide language for non-Compose call sites (repositories, services, notifications). */")
    out.append("object AppLocale {")
    out.append("    private val _language = MutableStateFlow(AppLanguage.ENGLISH)")
    out.append("    val language: StateFlow<AppLanguage> = _language.asStateFlow()")
    out.append("")
    out.append("    @Volatile")
    out.append("    private var current: UiStrings = EnStrings")
    out.append("")
    out.append("    fun strings(): UiStrings = current")
    out.append("")
    out.append("    fun currentLanguage(): AppLanguage = _language.value")
    out.append("")
    out.append("    fun apply(language: AppLanguage) {")
    out.append("        current = stringsFor(language)")
    out.append("        if (_language.value != language) _language.value = language")
    out.append("        val locale = language.locale")
    out.append("        if (Locale.getDefault() != locale) Locale.setDefault(locale)")
    out.append("    }")
    out.append("}")
    return "\n".join(out) + "\n"


def main() -> int:
    validate()
    root = Path(__file__).resolve().parents[2]  # manga-app/
    target = root / "android/app/src/main/java/com/mangaviewer/app/i18n/Strings.kt"
    target.parent.mkdir(parents=True, exist_ok=True)
    text = emit()
    target.write_text(text, encoding="utf-8")
    keys = sum(len(entries) for _, entries in SECTIONS)
    print("wrote %s (%d sections, %d keys)" % (target, len(SECTIONS), keys))
    return 0


if __name__ == "__main__":
    sys.exit(main())
