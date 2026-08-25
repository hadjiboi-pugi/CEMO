package com.example.cemo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cemo.data.model.WasteEntry
import com.example.cemo.ui.components.AppLogo
import com.example.cemo.ui.components.TranslatedText
import com.example.cemo.ui.components.UnifiedWasteEntryCard
import com.example.cemo.ui.theme.*
import com.example.cemo.viewmodel.WasteViewModel
import java.text.SimpleDateFormat
import java.util.*

enum class SortOrder {
    DESCENDING, ASCENDING
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WasteHistoryScreen(
    viewModel: WasteViewModel,
    innerPadding: PaddingValues,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sdf = remember<SimpleDateFormat> { SimpleDateFormat("MMM dd, HH:mm", Locale.US) }
    val dateSdf = remember<SimpleDateFormat> { 
        SimpleDateFormat("MMM dd, yyyy", Locale.US).apply { 
            timeZone = TimeZone.getTimeZone("UTC") 
        } 
    }

    var currentPage by remember { mutableStateOf(0) }
    var sortOrder by remember { mutableStateOf<SortOrder>(SortOrder.DESCENDING) }
    val pageSize = 10

    var entryToDelete by remember { mutableStateOf<WasteEntry?>(null) }

    // Date Filtering State
    var showDateRangePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()
    var selectedDateRange by remember { mutableStateOf<Pair<Long?, Long?>?>(null) }

    val filteredAndSortedHistory = remember(state.history, sortOrder, selectedDateRange) {
        val filtered = if (selectedDateRange != null && (selectedDateRange!!.first != null || selectedDateRange!!.second != null)) {
            val startUtc = selectedDateRange!!.first
            // If end date is not selected, default to the start date (single day selection)
            val endUtc = selectedDateRange!!.second ?: startUtc

            state.history.filter { entry ->
                var matches = true
                if (startUtc != null && endUtc != null) {
                    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                    
                    // Convert Start UTC midnight to Local Start of Day
                    utcCal.timeInMillis = startUtc
                    val localStartCal = Calendar.getInstance()
                    localStartCal.set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
                    localStartCal.set(Calendar.MILLISECOND, 0)
                    val localStartMillis = localStartCal.timeInMillis
                    
                    // Convert End UTC midnight to Local End of Day
                    utcCal.timeInMillis = endUtc
                    val localEndCal = Calendar.getInstance()
                    localEndCal.set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH), 23, 59, 59)
                    localEndCal.set(Calendar.MILLISECOND, 999)
                    val localEndMillis = localEndCal.timeInMillis

                    matches = entry.timestamp in localStartMillis..localEndMillis
                }
                matches
            }
        } else {
            state.history
        }

        if (sortOrder == SortOrder.DESCENDING) {
            filtered.sortedByDescending { it.timestamp }
        } else {
            filtered.sortedBy { it.timestamp }
        }
    }

    val paginatedHistory = remember(filteredAndSortedHistory, currentPage) {
        filteredAndSortedHistory.drop(currentPage * pageSize).take(pageSize)
    }

    val totalItems = filteredAndSortedHistory.size
    val totalPages = (totalItems + pageSize - 1) / pageSize

    val isFilterActive = selectedDateRange != null && (selectedDateRange!!.first != null || selectedDateRange!!.second != null)

    // Deletion Confirmation Dialog
    if (entryToDelete != null) {
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { TranslatedText("Confirm Deletion", style = MaterialTheme.typography.titleLarge) },
            text = { TranslatedText("Are you sure you want to delete this waste entry? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        entryToDelete?.let { viewModel.deleteWasteEntry(it) }
                        entryToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    TranslatedText("Delete", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    TranslatedText("Cancel", style = MaterialTheme.typography.labelLarge)
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        )
    }

    // Date Range Picker Dialog
    if (showDateRangePicker) {
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = dateRangePickerState.selectedStartDateMillis
                        val end = dateRangePickerState.selectedEndDateMillis
                        if (start != null || end != null) {
                            selectedDateRange = Pair(start, end)
                            currentPage = 0 // Reset pagination
                        } else {
                            selectedDateRange = null
                        }
                        showDateRangePicker = false
                    }
                ) {
                    TranslatedText("Apply", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) {
                    TranslatedText("Cancel")
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                title = { TranslatedText("Select Date Range", modifier = Modifier.padding(16.dp)) },
                headline = {
                    val start = dateRangePickerState.selectedStartDateMillis
                    val end = dateRangePickerState.selectedEndDateMillis
                    if (start != null || end != null) {
                        Row(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (start != null) dateSdf.format(Date(start)) else "...",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(" — ", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                text = if (end != null) dateSdf.format(Date(end)) else "...",
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    } else {
                        TranslatedText("Selected Range", modifier = Modifier.padding(16.dp))
                    }
                },
                showModeToggle = true,
                modifier = Modifier.weight(1f)
            )
        }
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 16.dp,
                    bottom = if (totalPages > 1) 140.dp else 32.dp // Extra space for floating pagination
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Column {
                        TranslatedText(
                            "History List",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = true,
                                onClick = { 
                                    sortOrder = if (sortOrder == SortOrder.DESCENDING) SortOrder.ASCENDING else SortOrder.DESCENDING
                                    currentPage = 0 // Reset to first page on sort
                                },
                                label = { 
                                    TranslatedText(if (sortOrder == SortOrder.DESCENDING) "Newest First" else "Oldest First") 
                                },
                                leadingIcon = {
                                    Icon(
                                        if (sortOrder == SortOrder.DESCENDING) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                shape = MaterialTheme.shapes.small,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                                )
                            )

                            FilterChip(
                                selected = isFilterActive,
                                onClick = { showDateRangePicker = true },
                                label = {
                                    if (isFilterActive) {
                                        val (start, end) = selectedDateRange!!
                                        when {
                                            start != null && end != null -> {
                                                Text("${dateSdf.format(Date(start))} - ${dateSdf.format(Date(end))}", fontSize = 11.sp)
                                            }
                                            start != null -> {
                                                Text("From ${dateSdf.format(Date(start))}", fontSize = 11.sp)
                                            }
                                            end != null -> {
                                                Text("Until ${dateSdf.format(Date(end))}", fontSize = 11.sp)
                                            }
                                            else -> TranslatedText("Filter by Date")
                                        }
                                    } else {
                                        TranslatedText("Filter by Date")
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                trailingIcon = if (isFilterActive) {
                                    {
                                        IconButton(
                                            onClick = { 
                                                selectedDateRange = null
                                                dateRangePickerState.setSelection(null, null)
                                            },
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear filter", modifier = Modifier.size(12.dp))
                                        }
                                    }
                                } else null,
                                shape = MaterialTheme.shapes.small,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }

                items(paginatedHistory) { entry ->
                    UnifiedWasteEntryCard(
                        entry = entry,
                        onDelete = { entryToDelete = entry }
                    )
                }
                
                if (filteredAndSortedHistory.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            TranslatedText(
                                if (isFilterActive) "No entries for this date range" else "No waste entries found",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Pagination Controls - Floating Overlay
            if (totalPages > 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .cemoSurface(shape = MaterialTheme.shapes.extraLarge)
                            .height(80.dp)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = { currentPage = (currentPage - 1).coerceAtLeast(0) },
                            enabled = currentPage > 0,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .background(
                                    color = if (currentPage > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) 
                                            else Color.Transparent,
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Previous",
                                tint = if (currentPage > 0) MaterialTheme.colorScheme.primary 
                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                        
                        Text(
                            text = "Page ${currentPage + 1} of $totalPages",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        IconButton(
                            onClick = { currentPage = (currentPage + 1).coerceAtMost(totalPages - 1) },
                            enabled = currentPage < totalPages - 1,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .background(
                                    color = if (currentPage < totalPages - 1) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) 
                                            else Color.Transparent,
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Next",
                                tint = if (currentPage < totalPages - 1) MaterialTheme.colorScheme.primary 
                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}
