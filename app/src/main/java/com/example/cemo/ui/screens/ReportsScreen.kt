package com.example.cemo.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cemo.ui.components.*
import com.example.cemo.ui.theme.*
import com.example.cemo.viewmodel.WasteViewModel
import java.text.SimpleDateFormat
import java.util.*
import com.example.cemo.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: WasteViewModel = viewModel(),
    innerPadding: PaddingValues = PaddingValues(0.dp)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by com.example.cemo.AppState.themeMode.collectAsState()
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        com.example.cemo.ThemeMode.LIGHT -> false
        com.example.cemo.ThemeMode.DARK -> true
        com.example.cemo.ThemeMode.SYSTEM -> isSystemDark
    }

    var selectedFilter by remember { mutableStateOf("Week") }
    var showDatePicker by remember { mutableStateOf(false) }
    var customRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val sdf = remember<SimpleDateFormat> { SimpleDateFormat("MMM dd", Locale.US) }

    // Aggregate over the selected date range. Every metric below is derived from
    // this filtered set, so the range chips actually change what's shown.
    val filteredHistory = remember(uiState.history, selectedFilter, customRange) {
        val now = System.currentTimeMillis()
        val dayMillis = 24L * 60 * 60 * 1000
        when (selectedFilter) {
            "Today" -> {
                val startOfDay = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                uiState.history.filter { it.timestamp >= startOfDay }
            }
            "Week" -> uiState.history.filter { it.timestamp >= now - 7 * dayMillis }
            "Month" -> uiState.history.filter { it.timestamp >= now - 30 * dayMillis }
            "Custom" -> customRange?.let { (start, end) ->
                uiState.history.filter { it.timestamp in start..end }
            } ?: uiState.history
            else -> uiState.history
        }
    }

    // Real Data Calculations (over the selected range)
    val totalWasteLogged = filteredHistory.sumOf { it.weightAdded }
    val lastEntries = filteredHistory.take(10)
    val avgTemp = if (lastEntries.isEmpty()) uiState.temperature else lastEntries.sumOf { it.temperature } / lastEntries.size.toDouble()
    val avgHumidity = if (lastEntries.isEmpty()) uiState.humidity else lastEntries.sumOf { it.humidity } / lastEntries.size.toDouble()

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(800)
        isLoading = false
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (showDatePicker) {
                ReportsDateRangeDialog(
                    onDismiss = { showDatePicker = false },
                    onApply = { start, end ->
                        customRange = start to end
                        selectedFilter = "Custom"
                        showDatePicker = false
                    }
                )
            }
            if (isLoading) {
                ReportsLoadingSkeleton(innerPadding)
            } else if (uiState.history.isEmpty()) {
                ReportsEmptyState(innerPadding) { viewModel.refresh() }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = innerPadding.calculateTopPadding() + 32.dp,
                        bottom = innerPadding.calculateBottomPadding() + 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // ── Top Section ───────────────────────────────────────────────
                    item {
                        Column {
                            Text(
                                stringResource(R.string.analytics),
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                stringResource(R.string.analytics_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(listOf("Today", "Week", "Month", "Custom")) { filter ->
                                val isSelected = selectedFilter == filter
                                Surface(
                                    onClick = {
                                        if (filter == "Custom") {
                                            showDatePicker = true
                                        } else {
                                            selectedFilter = filter
                                        }
                                    },
                                    shape = MaterialTheme.shapes.small,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    border = if (isSelected) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ) {
                                    val range = customRange
                                    if (filter == "Custom" && range != null) {
                                        Text(
                                            "${sdf.format(Date(range.first))} – ${sdf.format(Date(range.second))}",
                                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    } else {
                                        TranslatedText(
                                            filter,
                                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Summary Stats Grid ────────────────────────────────────────
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                            Row(
                                modifier = Modifier.height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(Space.md)
                            ) {
                                TranslatedReportStatCard(
                                    title = "TOTAL WASTE",
                                    value = "${"%.1f".format(totalWasteLogged)} kg",
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                                TranslatedReportStatCard(
                                    title = "AVG TEMP",
                                    value = "${"%.0f".format(avgTemp)}°C",
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            }
                            Row(
                                modifier = Modifier.height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(Space.md)
                            ) {
                                TranslatedReportStatCard(
                                    title = "AVG HUMIDITY",
                                    value = "${"%.0f".format(avgHumidity)}%",
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                                TranslatedReportStatCard(
                                    title = "ENTRIES LOGGED",
                                    value = "${filteredHistory.size}",
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            }
                        }
                    }

                    // ── Smart Insights ────────────────────────────────────────────
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .cemoSurface(shape = MaterialTheme.shapes.large)
                        ) {
                            Column(modifier = Modifier.padding(Space.card)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(Size.iconSmall)
                                    )
                                    Spacer(Modifier.width(Space.xs))
                                    TranslatedText(
                                        "Insights",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(Space.md))

                                // Every line below is derived from logged entries.
                                // Nothing is asserted that the data cannot support.
                                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                                    InsightRow(
                                        "Average across the last ${lastEntries.size} " +
                                            "entries: ${"%.0f".format(avgTemp)}°C, " +
                                            "${"%.0f".format(avgHumidity)}% humidity"
                                    )
                                    if (avgHumidity > 70) {
                                        InsightRow("Humidity is above 70% — expect faster spoilage")
                                    }
                                    InsightRow(
                                        "${filteredHistory.size} entries totalling " +
                                            "${"%.1f".format(totalWasteLogged)} kg"
                                    )
                                }
                            }
                        }
                    }

                    // ── Charts Section ────────────────────────────────────────────
                    item { TranslatedSectionTitle("Visual Analytics") }

                    item {
                        ChartCard(title = "Waste logged (kg)", icon = Icons.Default.Timeline) {
                            val historyPoints = filteredHistory.take(7).map { it.weightAdded.toFloat() }.reversed()
                            RealLineChart(historyPoints, MaterialTheme.dataPalette.load)
                        }
                    }

                    item {
                        ChartCard(title = "Temperature (°C)", icon = Icons.Default.Thermostat) {
                            val tempPoints = filteredHistory.take(7).map { it.temperature.toFloat() }.reversed()
                            RealAreaChart(tempPoints, MaterialTheme.dataPalette.temperature)
                        }
                    }

                    item {
                        ChartCard(title = "Humidity (%)", icon = Icons.Default.WaterDrop) {
                            val humPoints = filteredHistory.take(7).map { it.humidity.toFloat() }.reversed()
                            RealAreaChart(humPoints, MaterialTheme.dataPalette.humidity)
                        }
                    }

                    // ── Recent Reports ────────────────────────────────────────────
                    item { TranslatedSectionTitle("Audit Trail") }

                    items(filteredHistory.take(5)) { entry ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .cemoSurface(shape = MaterialTheme.shapes.large)
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(PrimaryGreen.copy(alpha = 0.08f), MaterialTheme.shapes.small),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Description,
                                        null,
                                        tint = PrimaryGreen,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        sdf.format(Date(entry.timestamp)),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "${"%.2f".format(entry.weightAdded)} kg",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.deleteWasteEntry(entry) },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f), MaterialTheme.shapes.extraSmall)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Surface(
                                    color = PrimaryGreen.copy(alpha = 0.1f),
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    TranslatedText(
                                        "VERIFIED",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 9.sp
                                        ),
                                        color = PrimaryGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Components ────────────────────────────────────────────────────────────────

/**
 * Calendar range picker for the "Custom" date filter. Returns the selected
 * start/end as epoch millis, with the end extended to the last millisecond of
 * the chosen end day so entries logged that day are included.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportsDateRangeDialog(
    onDismiss: () -> Unit,
    onApply: (Long, Long) -> Unit
) {
    val state = rememberDateRangePickerState()
    val canApply = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = canApply,
                onClick = {
                    val start = state.selectedStartDateMillis
                    val end = state.selectedEndDateMillis
                    if (start != null && end != null) {
                        onApply(start, end + (24L * 60 * 60 * 1000 - 1))
                    }
                }
            ) { TranslatedText("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { TranslatedText("Cancel") }
        }
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.weight(1f),
            title = {
                TranslatedText(
                    "Select range",
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        )
    }
}

@Composable
private fun RealLineChart(points: List<Float>, color: Color) {
    if (points.isEmpty()) {
        EmptyChartPlaceholder()
        return
    }
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val labelColor = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
        drawChartGrid(gridColor)
        val maxVal = (points.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val stepX = size.width / (points.size.coerceAtLeast(2) - 1).coerceAtLeast(1)
        val path = androidx.compose.ui.graphics.Path().apply {
            points.forEachIndexed { index, p ->
                val x = index * stepX
                val y = size.height * (1f - (p / maxVal))
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPointValues(points, maxVal, stepX, color, textMeasurer, labelColor)
    }
}

@Composable
private fun RealAreaChart(points: List<Float>, color: Color) {
    if (points.isEmpty()) {
        EmptyChartPlaceholder()
        return
    }
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val labelColor = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
        drawChartGrid(gridColor)
        val maxVal = (points.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val stepX = size.width / (points.size.coerceAtLeast(2) - 1).coerceAtLeast(1)
        val path = androidx.compose.ui.graphics.Path().apply {
            points.forEachIndexed { index, p ->
                val x = index * stepX
                val y = size.height * (1f - (p / maxVal))
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }

        val fillPath = androidx.compose.ui.graphics.Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.3f), Color.Transparent)
            )
        )
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        drawPointValues(points, maxVal, stepX, color, textMeasurer, labelColor)
    }
}

/**
 * Marks each data point with a dot and prints its value above it, so the graph
 * shows real numbers instead of just a bare line. Labels are centred over their
 * point and clamped inside the canvas so they never clip at the edges.
 */
private fun DrawScope.drawPointValues(
    points: List<Float>,
    maxVal: Float,
    stepX: Float,
    color: Color,
    textMeasurer: TextMeasurer,
    labelColor: Color
) {
    val labelStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = labelColor)
    points.forEachIndexed { index, p ->
        val x = index * stepX
        val y = size.height * (1f - (p / maxVal))
        drawCircle(color = color, radius = 3.5.dp.toPx(), center = Offset(x, y))

        val label = if (p >= 10f) "%.0f".format(p) else "%.1f".format(p)
        val measured = textMeasurer.measure(label, style = labelStyle)
        val tx = (x - measured.size.width / 2f).coerceIn(0f, (size.width - measured.size.width).coerceAtLeast(0f))
        val ty = (y - measured.size.height - 6.dp.toPx()).coerceAtLeast(0f)
        drawText(measured, topLeft = Offset(tx, ty))
    }
}

/** Faint horizontal baseline grid so a chart reads as a chart, not a blank box. */
private fun DrawScope.drawChartGrid(color: Color, lines: Int = 4) {
    for (i in 0..lines) {
        val y = size.height * i / lines
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx()
        )
    }
}

/**
 * Shown in place of a chart when the selected range has no entries. Dashed
 * gridlines plus an icon and label make the empty area unmistakably a graph
 * awaiting data, rather than looking like a rendering glitch.
 */
@Composable
private fun EmptyChartPlaceholder() {
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Box(
        modifier = Modifier.fillMaxWidth().height(150.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            val lines = 4
            for (i in 0..lines) {
                val y = size.height * i / lines
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dash
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.ShowChart,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(6.dp))
            TranslatedText(
                "No data for this range",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * A single statistic: label, value, and an optional [caption] explaining how the
 * value was derived.
 *
 * This card used to render a trend chip ("+8%", "-6% vs last week") that was a
 * hardcoded string — no period-over-period comparison existed to produce it. A
 * number the UI invents is worse than no number, so the trend is gone until
 * there is real history to compute one from; [caption] carries honest context
 * (an assumed rate, a sample size) instead.
 */
@Composable
fun TranslatedReportStatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null
) {
    Box(
        modifier = modifier
            .cemoSurface(shape = MaterialTheme.shapes.medium)
    ) {
        Column(modifier = Modifier.padding(Space.card)) {
            TranslatedText(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(Space.sm))
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            caption?.let {
                Spacer(modifier = Modifier.height(Space.xs))
                TranslatedText(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun InsightRow(text: String) {
    Row(
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = Space.sm)
                .size(Space.xs)
                .background(MaterialTheme.colorScheme.outline, CircleShape)
        )
        Spacer(Modifier.width(Space.md))
        TranslatedText(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ChartCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .cemoSurface(shape = MaterialTheme.shapes.medium)
    ) {
        Column(modifier = Modifier.padding(Space.card)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Size.iconSmall)
                )
                Spacer(Modifier.width(Space.sm))
                TranslatedText(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(Space.lg))
            content()
        }
    }
}

@Composable
private fun ReportsLoadingSkeleton(innerPadding: PaddingValues) {
    Column(
        modifier = Modifier.padding(16.dp).padding(top = innerPadding.calculateTopPadding()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SkeletonBox(width = 150.dp, height = 40.dp)
        SkeletonBox(width = 280.dp, height = 16.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) { SkeletonBox(width = 70.dp, height = 32.dp, shape = MaterialTheme.shapes.small) }
        }
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBox(modifier = Modifier.weight(1f).height(100.dp))
                SkeletonBox(modifier = Modifier.weight(1f).height(100.dp))
            }
        }
    }
}

@Composable
private fun SkeletonBox(
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp? = null,
    height: androidx.compose.ui.unit.Dp? = null,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.medium
) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "alpha"
    )
    
    val m = if (width != null && height != null) modifier.size(width, height) else modifier
    Box(
        modifier = m
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f * alpha))
    )
}

@Composable
private fun ReportsEmptyState(innerPadding: PaddingValues, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Analytics,
            null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
        Spacer(Modifier.height(16.dp))
        TranslatedText(
            "No reports available yet",
            style = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRefresh,
            shape = MaterialTheme.shapes.small
        ) {
            Icon(Icons.Default.Refresh, null)
            Spacer(Modifier.width(8.dp))
            TranslatedText("Refresh")
        }
    }
}
