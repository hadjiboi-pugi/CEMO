// ui/screens/ReportsScreen.kt  — unchanged except the import/signature
package com.example.cemo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cemo.ui.components.SectionTitle
import com.example.cemo.ui.theme.AllocationRed
import com.example.cemo.ui.theme.ImpactGold
import com.example.cemo.ui.theme.PrimaryGreen
import com.example.cemo.viewmodel.WasteViewModel

@Composable
fun ReportsScreen(wasteViewModel: WasteViewModel = viewModel()) {
    val state       by wasteViewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionTitle("Waste Generation Trend")

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalAlignment     = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val values = if (state.history.isEmpty()) {
                    listOf(0.1f)
                } else {
                    state.history
                        .take(7)
                        .map { (it.weightAdded / 10.0).toFloat().coerceIn(0.1f, 1.0f) }
                        .reversed()
                }
                values.forEach { h ->
                    Box(
                        modifier = Modifier
                            .width(30.dp)
                            .fillMaxHeight(h)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                            )
                    )
                }
            }
        }

        SectionTitle("Thesis Impact Analytics")

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier            = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ImpactMetricRow(
                    label = "Total CH₄ Avoided",
                    value = "${String.format("%.3f", state.totalMethane)} kg",
                    color = ImpactGold
                )
                ImpactMetricRow(
                    label = "CO₂ Eq. Reduced",
                    value = "${String.format("%.2f", state.totalMethane * 25)} kg",
                    color = AllocationRed
                )
                ImpactMetricRow(
                    label = "Compost Potential",
                    value = "${String.format("%.2f",
                        state.history.sumOf { it.weightAdded } * 0.45)} kg",
                    color = PrimaryGreen
                )
            }
        }
    }
}

@Composable
private fun ImpactMetricRow(label: String, value: String, color: Color) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                fontWeight = FontWeight.Medium,
                color      = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(value, fontWeight = FontWeight.Bold, color = color)
    }
}