package com.mangaviewer.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DateRangePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mangaviewer.app.i18n.LocalStrings
import com.mangaviewer.app.i18n.filterDateRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 下载页 / 收藏页共用的筛选基础设施：日期预设、标题首字母、日期范围选择器与面板小组件。
 * 只放与具体实体无关的纯工具；各页面自己的 Filter data class 留在各自文件里。
 */

internal val INITIALS: List<String> = listOf("#") + ('A'..'Z').map { it.toString() }

/** 半开区间 [from, to)，与筛选条件里的毫秒判断保持一致。 */
internal data class DateRange(val from: Long, val to: Long)

internal fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value

/** 标题首字母：全角字母先归一化（Ａ -> A），再跳过开头的括号 / 标点，取第一个字母或数字。 */
internal fun titleInitial(title: String): String {
    val normalized = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFKC)
    for (ch in normalized) {
        if (ch.isDigit()) return "#"
        if (ch.isLetter()) {
            val upper = ch.uppercaseChar()
            return if (upper in 'A'..'Z') upper.toString() else "#"
        }
    }
    return "#"
}

internal fun startOfDay(date: LocalDate): Long =
    date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

internal fun presetToday(): DateRange {
    val today = LocalDate.now()
    return DateRange(startOfDay(today), startOfDay(today.plusDays(1)))
}

internal fun presetDays(days: Long): DateRange {
    val today = LocalDate.now()
    return DateRange(startOfDay(today.minusDays(days - 1)), startOfDay(today.plusDays(1)))
}

internal fun presetYear(): DateRange {
    val today = LocalDate.now()
    return DateRange(startOfDay(today.withDayOfYear(1)), startOfDay(today.plusDays(1)))
}

internal val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/** 毫秒时间戳 -> 本地日期文本（列表里的「收藏于 / 下载于」）。 */
internal fun formatLocalDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FORMAT)

/** DateRangePicker 给的是 UTC 零点；headline 里按「用户选的那一天」原样显示。 */
internal fun formatUtcDate(utcMillis: Long): String =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate().format(DATE_FORMAT)

/** 日期筛选的本地化文案；[rangeLine] 负责「起 – 止」这一行的格式化（各语言格式相同，但走各自的字符串表）。 */
internal data class DateFilterLabels(
    val all: String,
    val today: String,
    val days7: String,
    val days30: String,
    val year: String,
    val rangeLine: (String, String) -> String,
)

/** 当前日期筛选的人类可读标签：命中预设显示预设名，自定义区间显示起止日期。 */
internal fun dateFilterLabel(from: Long?, to: Long?, labels: DateFilterLabels): String {
    from ?: return labels.all
    val presets = listOf(
        presetToday() to labels.today,
        presetDays(7) to labels.days7,
        presetDays(30) to labels.days30,
        presetYear() to labels.year,
    )
    presets.firstOrNull { from == it.first.from && to == it.first.to }
        ?.let { return it.second }
    val fromText = formatLocalDate(from)
    val end = to ?: return labels.rangeLine(fromText, "…")
    val toText = formatLocalDate((end - 1).coerceAtLeast(from))
    return labels.rangeLine(fromText, toText)
}

internal fun localStartToUtcDate(localMillis: Long): Long =
    Instant.ofEpochMilli(localMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

internal fun utcDateToLocalStart(utcMillis: Long): Long =
    Instant.ofEpochMilli(utcMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

@Composable
internal fun DatePresetChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
internal fun FilterSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

@Composable
internal fun FilterHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
internal fun FilterDivider() {
    HorizontalDivider(modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

/** 日期区间选择弹窗；下载页与收藏页共用。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterDateRangeDialog(
    title: String,
    rangeTitle: String,
    doneText: String,
    initialFrom: Long?,
    initialTo: Long?,
    onConfirm: (Long, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    // DatePicker 内部月份 / 星期跟随 state.locale；显式按应用语言创建，
    // 避免系统中文 + 应用英文出现中英混排。
    val state = remember(s.language) {
        DateRangePickerState(
            locale = s.language.locale,
            initialSelectedStartDateMillis = initialFrom?.let(::localStartToUtcDate),
            initialSelectedEndDateMillis = initialTo?.let { localStartToUtcDate(it - 1) },
        )
    }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val start = state.selectedStartDateMillis ?: return@TextButton
                    val end = state.selectedEndDateMillis ?: start
                    val from = utcDateToLocalStart(start)
                    val to = utcDateToLocalStart(end).let { endStart ->
                        Instant.ofEpochMilli(endStart)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                            .plusDays(1)
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                    }
                    onConfirm(from, to)
                },
            ) {
                Text(doneText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.common.cancel) }
        },
    ) {
        DateRangePicker(
            state = state,
            showModeToggle = false,
            modifier = Modifier.weight(1f),
            title = {
                Text(
                    title,
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                )
            },
            headline = {
                val start = state.selectedStartDateMillis
                val end = state.selectedEndDateMillis
                val label = when {
                    start == null -> rangeTitle
                    end == null -> formatUtcDate(start)
                    else -> s.filterDateRange(formatUtcDate(start), formatUtcDate(end))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            },
        )
    }
}
