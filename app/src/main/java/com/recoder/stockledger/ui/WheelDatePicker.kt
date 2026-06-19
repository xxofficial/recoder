package com.recoder.stockledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.recoder.stockledger.ui.theme.BackgroundPrimary
import com.recoder.stockledger.ui.theme.BorderSubtle
import com.recoder.stockledger.ui.theme.ForegroundMuted
import com.recoder.stockledger.ui.theme.ForegroundPrimary
import com.recoder.stockledger.ui.theme.ForegroundSecondary
import com.recoder.stockledger.ui.theme.SurfaceSecondary
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.absoluteValue

private enum class WheelDateRangeSide {
    START,
    END,
}

internal data class WheelVisibleItemInfo(
    val value: Int,
    val offset: Int,
    val size: Int,
)

internal fun parseWheelDateOrNull(value: String): LocalDate? =
    runCatching { LocalDate.parse(value) }.getOrNull()

private val DefaultWheelMinDate: LocalDate = LocalDate.of(2000, 1, 1)

private fun LocalDate.coerceInRange(minDate: LocalDate, maxDate: LocalDate): LocalDate {
    return when {
        isBefore(minDate) -> minDate
        isAfter(maxDate) -> maxDate
        else -> this
    }
}

internal fun coerceWheelDate(
    year: Int,
    month: Int,
    day: Int,
    minDate: LocalDate = DefaultWheelMinDate,
    maxDate: LocalDate = LocalDate.now(),
): LocalDate {
    val safeMonth = month.coerceIn(1, 12)
    val maxDay = YearMonth.of(year, safeMonth).lengthOfMonth()
    return LocalDate.of(year, safeMonth, day.coerceIn(1, maxDay)).coerceInRange(minDate, maxDate)
}

internal fun updateWheelDate(
    current: LocalDate,
    year: Int = current.year,
    month: Int = current.monthValue,
    day: Int = current.dayOfMonth,
    minDate: LocalDate = DefaultWheelMinDate,
    maxDate: LocalDate = LocalDate.now(),
): LocalDate = coerceWheelDate(year, month, day, minDate, maxDate)

internal fun findCenteredWheelValue(
    visibleItems: List<WheelVisibleItemInfo>,
    viewportStartOffset: Int,
    viewportEndOffset: Int,
): Int? {
    if (visibleItems.isEmpty()) return null
    val viewportCenter = (viewportStartOffset + viewportEndOffset) / 2
    return visibleItems.minByOrNull { item ->
        (item.offset + item.size / 2 - viewportCenter).absoluteValue
    }?.value
}

@Composable
fun WheelDatePickerSheet(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    fallbackDate: LocalDate = LocalDate.now(),
    allowClear: Boolean = false,
    minDate: LocalDate = DefaultWheelMinDate,
    maxDate: LocalDate = LocalDate.now(),
) {
    val safeFallbackDate = fallbackDate.coerceInRange(minDate, maxDate)
    var draftDate by rememberSaveable(value) {
        mutableStateOf((parseWheelDateOrNull(value)?.coerceInRange(minDate, maxDate) ?: safeFallbackDate).toString())
    }
    val selectedDate = parseWheelDateOrNull(draftDate)?.coerceInRange(minDate, maxDate) ?: safeFallbackDate

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = BackgroundPrimary,
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(title, color = ForegroundPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                WheelDatePicker(
                    selectedDate = selectedDate,
                    onDateSelected = { draftDate = it.toString() },
                    minDate = minDate,
                    maxDate = maxDate,
                    showSelectedPill = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (allowClear) {
                        TextButton(
                            onClick = {
                                onValueChange("")
                                onDismiss()
                            },
                        ) {
                            Text("清空日期")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) {
                            Text("取消")
                        }
                        Button(
                            onClick = {
                                onValueChange(draftDate)
                                onDismiss()
                            },
                        ) {
                            Text("确定")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WheelDateRangePicker(
    startDate: String,
    endDate: String,
    onStartDateChange: (String) -> Unit,
    onEndDateChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fallbackDate: LocalDate = LocalDate.now(),
    minDate: LocalDate = DefaultWheelMinDate,
    maxDate: LocalDate = LocalDate.now(),
) {
    val safeFallbackDate = fallbackDate.coerceInRange(minDate, maxDate)
    var activeSide by rememberSaveable { mutableStateOf(WheelDateRangeSide.START) }
    val parsedStart = parseWheelDateOrNull(startDate)?.coerceInRange(minDate, maxDate)
    val parsedEnd = parseWheelDateOrNull(endDate)?.coerceInRange(minDate, maxDate)
    val activeDate = when (activeSide) {
        WheelDateRangeSide.START -> parsedStart ?: parsedEnd ?: safeFallbackDate
        WheelDateRangeSide.END -> parsedEnd ?: parsedStart ?: safeFallbackDate
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WheelDatePill(
                text = parsedStart?.toString() ?: "开始日期",
                selected = activeSide == WheelDateRangeSide.START,
                onClick = { activeSide = WheelDateRangeSide.START },
                modifier = Modifier.weight(1f),
            )
            Text("—", color = ForegroundMuted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
            WheelDatePill(
                text = parsedEnd?.toString() ?: "结束日期",
                selected = activeSide == WheelDateRangeSide.END,
                onClick = { activeSide = WheelDateRangeSide.END },
                modifier = Modifier.weight(1f),
            )
        }

        WheelDatePicker(
            selectedDate = activeDate,
            onDateSelected = { nextDate ->
                when (activeSide) {
                    WheelDateRangeSide.START -> onStartDateChange(nextDate.toString())
                    WheelDateRangeSide.END -> onEndDateChange(nextDate.toString())
                }
            },
            minDate = minDate,
            maxDate = maxDate,
            showSelectedPill = false,
        )
    }
}

@Composable
private fun WheelDatePicker(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    minDate: LocalDate = DefaultWheelMinDate,
    maxDate: LocalDate = LocalDate.now(),
    showSelectedPill: Boolean,
) {
    val safeSelectedDate = selectedDate.coerceInRange(minDate, maxDate)
    val years = remember(minDate, maxDate) { (minDate.year..maxDate.year).toList() }
    val months = remember(safeSelectedDate.year, minDate, maxDate) {
        val start = if (safeSelectedDate.year == minDate.year) minDate.monthValue else 1
        val end = if (safeSelectedDate.year == maxDate.year) maxDate.monthValue else 12
        (start..end).toList()
    }
    val days = remember(safeSelectedDate.year, safeSelectedDate.monthValue, minDate, maxDate) {
        val yearMonth = YearMonth.of(safeSelectedDate.year, safeSelectedDate.monthValue)
        val start = if (
            safeSelectedDate.year == minDate.year &&
            safeSelectedDate.monthValue == minDate.monthValue
        ) {
            minDate.dayOfMonth
        } else {
            1
        }
        val end = if (
            safeSelectedDate.year == maxDate.year &&
            safeSelectedDate.monthValue == maxDate.monthValue
        ) {
            maxDate.dayOfMonth
        } else {
            yearMonth.lengthOfMonth()
        }
        (start..end).toList()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showSelectedPill) {
            WheelDatePill(
                text = safeSelectedDate.toString(),
                selected = true,
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth(0.34f)
                    .align(Alignment.CenterHorizontally),
            )
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                verticalArrangement = Arrangement.spacedBy(34.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(BorderSubtle),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(BorderSubtle),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                WheelColumn(
                    values = years,
                    selected = safeSelectedDate.year,
                    label = { "${it}年" },
                    onSelected = {
                        onDateSelected(updateWheelDate(safeSelectedDate, year = it, minDate = minDate, maxDate = maxDate))
                    },
                    modifier = Modifier.weight(1f),
                )
                WheelColumn(
                    values = months,
                    selected = safeSelectedDate.monthValue,
                    label = { "${it}月" },
                    onSelected = {
                        onDateSelected(updateWheelDate(safeSelectedDate, month = it, minDate = minDate, maxDate = maxDate))
                    },
                    modifier = Modifier.weight(1f),
                )
                WheelColumn(
                    values = days,
                    selected = safeSelectedDate.dayOfMonth,
                    label = { it.toString() },
                    onSelected = {
                        onDateSelected(updateWheelDate(safeSelectedDate, day = it, minDate = minDate, maxDate = maxDate))
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WheelDatePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(28.dp)
            .clipOrBorder(selected)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = ForegroundPrimary,
            fontSize = 11.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

private fun Modifier.clipOrBorder(selected: Boolean): Modifier {
    val shape = RoundedCornerShape(999.dp)
    return this
        .background(if (selected) BackgroundPrimary else SurfaceSecondary, shape)
        .border(
            width = if (selected) 1.dp else 0.dp,
            color = if (selected) ForegroundPrimary else SurfaceSecondary,
            shape = shape,
        )
}

@Composable
private fun WheelColumn(
    values: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemHeight = 30.dp
    val columnHeight = 152.dp
    val verticalPadding = (columnHeight - itemHeight) / 2
    val listState = rememberLazyListState()
    val selectedIndex = values.indexOf(selected).coerceAtLeast(0)
    LaunchedEffect(selectedIndex, values) {
        if (!listState.isScrollInProgress) {
            listState.animateScrollToItem(selectedIndex)
        }
    }
    LaunchedEffect(listState, values, selected) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrollInProgress ->
                if (!isScrollInProgress) {
                    val layoutInfo = listState.layoutInfo
                    val centeredValue = findCenteredWheelValue(
                        visibleItems = layoutInfo.visibleItemsInfo.mapNotNull { item ->
                            values.getOrNull(item.index)?.let { value ->
                                WheelVisibleItemInfo(
                                    value = value,
                                    offset = item.offset,
                                    size = item.size,
                                )
                            }
                        },
                        viewportStartOffset = layoutInfo.viewportStartOffset,
                        viewportEndOffset = layoutInfo.viewportEndOffset,
                    )
                    if (centeredValue != null) {
                        if (centeredValue != selected) {
                            onSelected(centeredValue)
                        } else {
                            val centeredIndex = values.indexOf(centeredValue)
                            if (centeredIndex >= 0) {
                                listState.animateScrollToItem(centeredIndex)
                            }
                        }
                    }
                }
            }
    }

    LazyColumn(
        modifier = modifier.height(columnHeight),
        state = listState,
        contentPadding = PaddingValues(vertical = verticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(values) { _, value ->
            val isSelected = value == selected
            Text(
                text = label(value),
                color = if (isSelected) ForegroundPrimary else ForegroundMuted.copy(alpha = 0.42f),
                fontSize = if (isSelected) 13.sp else 12.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .clickable { onSelected(value) }
                    .padding(vertical = 6.dp),
            )
        }
    }
}
