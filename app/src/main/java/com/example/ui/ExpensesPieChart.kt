package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.Expense

@Composable
fun ExpensesPieChart(expenses: List<Expense>, modifier: Modifier = Modifier) {
    if (expenses.isEmpty()) {
        Text("Nenhuma despesa para o gráfico.")
        return
    }

    val colors = listOf(
        Color(0xFFE57373), Color(0xFF81C784), Color(0xFF64B5F6), Color(0xFFFFD54F),
        Color(0xFFBA68C8), Color(0xFFFF8A65), Color(0xFF4DB6AC), Color(0xFFFFF176)
    )

    val categoryTotals = expenses.groupBy { it.category }
        .mapValues { it.value.sumOf { e -> e.amount } }
    val totalAmount = categoryTotals.values.sum().toFloat()

    val slices = categoryTotals.entries.mapIndexed { index, entry ->
        Pair(entry.key, entry.value.toFloat() / totalAmount)
    }.sortedByDescending { it.second }

    Row(modifier = modifier, horizontalArrangement = Arrangement.SpaceEvenly) {
        Box(modifier = Modifier.size(150.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                var startAngle = -90f
                slices.forEachIndexed { index, slice ->
                    val sweepAngle = slice.second * 360f
                    drawArc(
                        color = colors[index % colors.size],
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = true,
                        size = Size(size.width, size.height),
                        topLeft = Offset(0f, 0f)
                    )
                    startAngle += sweepAngle
                }
            }
        }
        
        Column(modifier = Modifier.padding(start = 16.dp)) {
            slices.forEachIndexed { index, (category, percent) ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(12.dp),
                        color = colors[index % colors.size],
                        shape = androidx.compose.foundation.shape.CircleShape
                    ) {}
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$category (${(percent * 100).toInt()}%)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
