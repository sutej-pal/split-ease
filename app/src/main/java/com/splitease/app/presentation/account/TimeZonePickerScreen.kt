package com.splitease.app.presentation.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import android.widget.Toast
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.presentation.theme.SeBodyLarge
import com.splitease.app.presentation.theme.SeBodySmall
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeTextField
import java.time.ZoneId
import java.time.ZonedDateTime

@Composable
fun TimeZonePickerScreen(
    onBack: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(settings.errorMessage) {
        val message = settings.errorMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearMessages()
    }

    val allTimeZones = remember {
        ZoneId.getAvailableZoneIds().sorted().map { id ->
            val (offset, city) = formatTimeZoneDisplay(id)
            TimeZoneOption(id, offset, city)
        }
    }

    val filteredTimeZones = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            allTimeZones
        } else {
            val q = searchQuery.trim().lowercase()
            allTimeZones.filter {
                it.id.lowercase().contains(q) ||
                    it.city.lowercase().contains(q) ||
                    it.offset.lowercase().contains(q)
            }
        }
    }

    SeScreen(
        title = stringResource(R.string.account_time_zone_title),
        onBack = onBack,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding.values)
                    .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            SeTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = stringResource(R.string.account_time_zone_search),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = SplitEaseColors.NavyMuted,
                    )
                },
            )
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                items(filteredTimeZones, key = { it.id }) { option ->
                    val isSelected = option.id == settings.timeZoneId
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) SplitEaseColors.PrimarySoft else SplitEaseColors.Surface)
                                .clickable {
                                    scope.launch {
                                        if (viewModel.updateTimeZone(option.id)) onBack()
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            SeBodyLarge(
                                text = "${option.offset} ${option.city}",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = SplitEaseColors.Navy,
                            )
                            SeBodySmall(
                                text = option.id,
                                color = SplitEaseColors.NavyMuted,
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = SplitEaseColors.Primary,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

data class TimeZoneOption(
    val id: String,
    val offset: String,
    val city: String,
)

fun formatTimeZoneDisplay(zoneIdStr: String): Pair<String, String> {
    return try {
        val zoneId = ZoneId.of(zoneIdStr)
        val zdt = ZonedDateTime.now(zoneId)
        val offset = zdt.offset.id.let { if (it == "Z") "+00:00" else it }
        val city = zoneIdStr.substringAfterLast('/').replace('_', ' ')
        Pair("(GMT$offset)", city)
    } catch (_: Exception) {
        Pair("", zoneIdStr)
    }
}
