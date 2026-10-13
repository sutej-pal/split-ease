package com.splitease.app.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.splitease.app.presentation.theme.SeBodyMedium
import com.splitease.app.presentation.theme.SplitEaseColors

/**
 * Screen body for grouped settings: a slight dark shade of the primary color,
 * with room for one or more [SeContentCard]s.
 *
 * The shade is the full body under the app bar. Pass scaffold padding on [modifier]
 * so the top bar stays on the screen background.
 */
@Composable
fun SeContentArea(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues =
        PaddingValues(
            start = SeLayout.screenHorizontal,
            top = SeLayout.screenTop,
            end = SeLayout.screenHorizontal,
            bottom = SeLayout.screenBottom,
        ),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(SplitEaseColors.ContentArea)
                .padding(contentPadding),
        content = content,
    )
}

/**
 * Rounded sheet on a [SeContentArea]. White in light theme, surface in dark,
 * with a soft drop shadow instead of a hard border.
 */
@Composable
fun SeContentCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(SeLayout.cardRadius)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 10.dp,
                    shape = shape,
                    ambientColor = SplitEaseColors.ContentCardShadowAmbient,
                    spotColor = SplitEaseColors.ContentCardShadowSpot,
                ).clip(shape)
                .background(SplitEaseColors.ContentCard),
        content = content,
    )
}

@Preview(name = "Content card", showBackground = true, heightDp = 280)
@Composable
private fun SeContentCardPreview() {
    SePreview {
        SeContentArea {
            SeContentCard {
                SeBodyMedium(
                    text = "Light",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        }
    }
}
