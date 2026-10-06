package com.splitease.app.presentation.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.splitease.app.presentation.ui.SePreview

/**
 * Source of truth for SplitEase named typography composables.
 *
 * | Composable      | typography scale | default fontWeight | default color            |
 * |------------------|------------------|---------------------|---------------------------|
 * | SeDisplayLarge   | displayLarge     | Bold                | SplitEaseColors.Navy      |
 * | SeDisplayMedium  | displayMedium    | Bold                | SplitEaseColors.Navy      |
 * | SeHeadlineLarge  | headlineLarge    | Bold                | SplitEaseColors.Navy      |
 * | SeHeadlineMedium | headlineMedium   | SemiBold            | SplitEaseColors.Navy      |
 * | SeHeadlineSmall  | headlineSmall    | SemiBold            | SplitEaseColors.Navy      |
 * | SeTitleLarge     | titleLarge       | SemiBold            | SplitEaseColors.Navy      |
 * | SeTitleMedium    | titleMedium      | SemiBold            | SplitEaseColors.Navy      |
 * | SeTitleSmall     | titleSmall       | SemiBold            | SplitEaseColors.Navy      |
 * | SeBodyLarge      | bodyLarge        | Normal              | SplitEaseColors.Navy      |
 * | SeBodyMedium     | bodyMedium       | Normal              | SplitEaseColors.NavyMuted |
 * | SeBodySmall      | bodySmall        | Normal              | SplitEaseColors.NavyMuted |
 * | SeLabelLarge     | labelLarge       | SemiBold            | SplitEaseColors.Navy      |
 * | SeLabelMedium    | labelMedium      | Medium              | SplitEaseColors.NavyMuted |
 * | SeLabelSmall     | labelSmall       | Medium              | SplitEaseColors.NavyMuted |
 */

@Composable
fun SeDisplayLarge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SplitEaseColors.Navy,
    fontWeight: FontWeight = FontWeight.Bold,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.displayLarge,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun SeDisplayMedium(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SplitEaseColors.Navy,
    fontWeight: FontWeight = FontWeight.Bold,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.displayMedium,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun SeHeadlineLarge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SplitEaseColors.Navy,
    fontWeight: FontWeight = FontWeight.Bold,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textDecoration: TextDecoration? = null,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineLarge.copy(textDecoration = textDecoration),
        fontWeight = fontWeight,
        color = color,
        modifier = modifier,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun SeHeadlineMedium(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SplitEaseColors.Navy,
    fontWeight: FontWeight = FontWeight.SemiBold,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun SeHeadlineMedium(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = SplitEaseColors.Navy,
    fontWeight: FontWeight = FontWeight.SemiBold,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun SeHeadlineSmall(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SplitEaseColors.Navy,
    fontWeight: FontWeight = FontWeight.SemiBold,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun SeTitleLarge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SplitEaseColors.Navy,
    fontWeight: FontWeight = FontWeight.SemiBold,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun SeTitleMedium(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SplitEaseColors.Navy,
    fontWeight: FontWeight = FontWeight.SemiBold,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun SeTitleMedium(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = SplitEaseColors.Navy,
    fontWeight: FontWeight = FontWeight.SemiBold,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun SeTitleSmall(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SplitEaseColors.Navy,
    fontWeight: FontWeight = FontWeight.SemiBold,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun SeBodyLarge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SplitEaseColors.Navy,
    fontWeight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun SeBodyLarge(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = SplitEaseColors.Navy,
    fontWeight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun SeBodyMedium(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SplitEaseColors.NavyMuted,
    fontWeight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign? = null,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier,
        textAlign = textAlign,
        minLines = minLines,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun SeBodyMedium(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = SplitEaseColors.NavyMuted,
    fontWeight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign? = null,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier,
        textAlign = textAlign,
        minLines = minLines,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun SeBodySmall(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SplitEaseColors.NavyMuted,
    fontWeight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun SeBodySmall(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = SplitEaseColors.NavyMuted,
    fontWeight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun SeLabelLarge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SplitEaseColors.Navy,
    fontWeight: FontWeight = FontWeight.SemiBold,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun SeLabelMedium(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SplitEaseColors.NavyMuted,
    fontWeight: FontWeight = FontWeight.Medium,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun SeLabelSmall(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SplitEaseColors.NavyMuted,
    fontWeight: FontWeight = FontWeight.Medium,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Preview(showBackground = true, name = "SeText typography scale")
@Composable
private fun SeTextScalePreview() {
    SePreview {
        Column(
            modifier =
                Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SeDisplayLarge("Display Large")
            SeDisplayMedium("Display Medium")
            SeHeadlineLarge("Headline Large")
            SeHeadlineMedium("Headline Medium")
            SeHeadlineSmall("Headline Small")
            SeTitleLarge("Title Large")
            SeTitleMedium("Title Medium")
            SeTitleSmall("Title Small")
            SeBodyLarge("Body Large")
            SeBodyMedium("Body Medium")
            SeBodySmall("Body Small")
            SeLabelLarge("Label Large")
            SeLabelMedium("Label Medium")
            SeLabelSmall("Label Small")
        }
    }
}
