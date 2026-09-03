package com.splitease.app.presentation.spending

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.splitease.app.domain.spending.CategorySpending

/**
 * Column chart of spending totals for the largest currency bucket (top categories).
 */
@Composable
fun SpendingCategoryChart(
    rows: List<CategorySpending>,
    modifier: Modifier = Modifier,
) {
    val chartRows =
        rows
            .groupBy { it.currencyCode }
            .maxByOrNull { (_, list) -> list.sumOf { it.total.toDouble() } }
            ?.value
            .orEmpty()
            .take(8)
    if (chartRows.isEmpty()) return

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(chartRows) {
        modelProducer.runTransaction {
            columnModel {
                series(chartRows.map { it.total.toDouble() })
            }
        }
    }

    CartesianChartHost(
        chart =
            rememberCartesianChart(
                rememberColumnCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis =
                    HorizontalAxis.rememberBottom(
                        valueFormatter = { _, value, _ ->
                            chartRows
                                .getOrNull(value.toInt())
                                ?.categoryName
                                ?.take(8)
                                .orEmpty()
                        },
                    ),
            ),
        modelProducer = modelProducer,
        modifier = modifier.fillMaxWidth().height(220.dp),
    )
}
