package com.fitlens.companion.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.fitlens.companion.ui.Brand

/*
 * The `Fit` controls (#306). Every Material control the app uses is drawn through one of these, with its colours set
 * here from `Brand` rather than left to Material's defaults. Material 3 moves its defaults between releases (the
 * outlined button's text went from gold to grey in the platform update, #293, and only one screenshot caught it), so
 * pinning them keeps a library update from restyling the app. Call sites use the `Fit` name; a raw Material call
 * outside `ui/design/` fails `RawMaterialControlsTest`. `DesignSamples.materialDefaults()` shows each control in each
 * state for the screenshot, phone-size and accessibility tests.
 *
 * Disabled colours are pinned too, at the same 38% that Material uses, so a disabled control stays readable on black.
 */

private const val DISABLED = 0.38f

/**
 * Gold text for a `TextButton`, set here rather than left to Material's default, which can move between releases as
 * the outlined button's did (#293). Also pins the clear container and the dimmed disabled text.
 */
@Composable
fun fitTextButtonColors(): ButtonColors = ButtonDefaults.textButtonColors(
    containerColor = Color.Transparent,
    contentColor = Brand.Gold,
    disabledContainerColor = Color.Transparent,
    disabledContentColor = Brand.Ivory.copy(alpha = DISABLED)
)

/** A text button in gold, with its colours pinned ([fitTextButtonColors]). */
@Composable
fun FitTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = fitTextButtonColors(),
        contentPadding = contentPadding,
        content = content
    )
}

/**
 * A choice chip: chosen is gold on gold dusk with a gold rim, not chosen is ivory on clear with an outline rim, so
 * the state never depends on the colour alone being noticed (the rim and fill both change).
 */
@Composable
fun FitFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = Brand.Ivory,
            iconColor = Brand.Gold,
            disabledContainerColor = Color.Transparent,
            disabledLabelColor = Brand.Ivory.copy(alpha = DISABLED),
            disabledLeadingIconColor = Brand.Gold.copy(alpha = DISABLED),
            disabledTrailingIconColor = Brand.Gold.copy(alpha = DISABLED),
            selectedContainerColor = Brand.GoldDusk,
            disabledSelectedContainerColor = Brand.GoldDusk.copy(alpha = DISABLED),
            selectedLabelColor = Brand.GoldLight,
            selectedLeadingIconColor = Brand.GoldLight,
            selectedTrailingIconColor = Brand.GoldLight
        ),
        border = BorderStroke(
            1.dp,
            when {
                !enabled -> Brand.Hairline
                selected -> Brand.Gold
                else -> Brand.Outline
            }
        )
    )
}

/** A chip that offers an action (add a suggested name): ivory label, gold icon, fine hairline rim. */
@Composable
fun FitAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    AssistChip(
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = Color.Transparent,
            labelColor = Brand.Ivory,
            leadingIconContentColor = Brand.Gold,
            trailingIconContentColor = Brand.Gold,
            disabledContainerColor = Color.Transparent,
            disabledLabelColor = Brand.Ivory.copy(alpha = DISABLED),
            disabledLeadingIconContentColor = Brand.Gold.copy(alpha = DISABLED),
            disabledTrailingIconContentColor = Brand.Gold.copy(alpha = DISABLED)
        ),
        border = BorderStroke(1.dp, if (enabled) Brand.Hairline else Brand.Hairline.copy(alpha = DISABLED))
    )
}

/** A chip that suggests a value: ivory label, gold icon, fine hairline rim. */
@Composable
fun FitSuggestionChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null
) {
    SuggestionChip(
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = Color.Transparent,
            labelColor = Brand.Ivory,
            iconContentColor = Brand.Gold,
            disabledContainerColor = Color.Transparent,
            disabledLabelColor = Brand.Ivory.copy(alpha = DISABLED),
            disabledIconContentColor = Brand.Gold.copy(alpha = DISABLED)
        ),
        border = BorderStroke(1.dp, if (enabled) Brand.Hairline else Brand.Hairline.copy(alpha = DISABLED))
    )
}

/** The switch's colours: on is a light gold thumb over a deep gold track, off is a muted thumb over an outlined dark track. */
@Composable
fun fitSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = Brand.GoldLight,
    checkedTrackColor = Brand.GoldDeep,
    checkedBorderColor = Brand.Gold,
    uncheckedThumbColor = Brand.Muted,
    uncheckedTrackColor = Brand.SurfaceHighest,
    uncheckedBorderColor = Brand.Outline,
    disabledCheckedThumbColor = Brand.GoldLight.copy(alpha = DISABLED),
    disabledCheckedTrackColor = Brand.GoldDeep.copy(alpha = DISABLED),
    disabledCheckedBorderColor = Brand.Gold.copy(alpha = DISABLED),
    disabledUncheckedThumbColor = Brand.Muted.copy(alpha = DISABLED),
    disabledUncheckedTrackColor = Brand.SurfaceHighest.copy(alpha = DISABLED),
    disabledUncheckedBorderColor = Brand.Outline.copy(alpha = DISABLED)
)

/** An on/off switch with its colours pinned ([fitSwitchColors]). */
@Composable
fun FitSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled, colors = fitSwitchColors())
}

/**
 * A slider: gold thumb and active track over an outline-coloured inactive track, which stays clearly visible on
 * black (Material's newer default is too faint there). Ticks are dark dots on either track.
 */
@Composable
fun fitSliderColors(): SliderColors = SliderDefaults.colors(
    thumbColor = Brand.Gold,
    activeTrackColor = Brand.Gold,
    activeTickColor = Brand.Black.copy(alpha = 0.5f),
    inactiveTrackColor = Brand.Outline,
    inactiveTickColor = Brand.Black.copy(alpha = 0.5f),
    disabledThumbColor = Brand.Gold.copy(alpha = DISABLED),
    disabledActiveTrackColor = Brand.Gold.copy(alpha = DISABLED),
    disabledActiveTickColor = Brand.Black.copy(alpha = 0.3f),
    disabledInactiveTrackColor = Brand.Outline.copy(alpha = DISABLED),
    disabledInactiveTickColor = Brand.Black.copy(alpha = 0.3f)
)

/** A slider with its colours pinned ([fitSliderColors]). */
@Composable
fun FitSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = fitSliderColors()
    )
}

/** The checkbox's colours: a gold box with a black tick (a glyph, not text), a muted outline when empty. */
@Composable
fun fitCheckboxColors(): CheckboxColors = CheckboxDefaults.colors(
    checkedColor = Brand.Gold,
    uncheckedColor = Brand.Muted,
    checkmarkColor = Brand.Black,
    disabledCheckedColor = Brand.Ivory.copy(alpha = DISABLED),
    disabledUncheckedColor = Brand.Ivory.copy(alpha = DISABLED),
    disabledIndeterminateColor = Brand.Ivory.copy(alpha = DISABLED)
)

/** A checkbox with its colours pinned ([fitCheckboxColors]). */
@Composable
fun FitCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Checkbox(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled, colors = fitCheckboxColors())
}

/** The radio button's colours: gold when chosen, muted when not. */
@Composable
fun fitRadioButtonColors(): RadioButtonColors = RadioButtonDefaults.colors(
    selectedColor = Brand.Gold,
    unselectedColor = Brand.Muted,
    disabledSelectedColor = Brand.Ivory.copy(alpha = DISABLED),
    disabledUnselectedColor = Brand.Ivory.copy(alpha = DISABLED)
)

/** A radio button with its colours pinned ([fitRadioButtonColors]). */
@Composable
fun FitRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    RadioButton(selected = selected, onClick = onClick, modifier = modifier, enabled = enabled, colors = fitRadioButtonColors())
}

/** A card's colours: raised onyx under ivory text unless the caller picks another container. */
@Composable
fun fitCardColors(containerColor: Color = Brand.SurfaceHighest, contentColor: Color = Brand.Ivory): CardColors =
    CardDefaults.cardColors(
        containerColor = containerColor,
        contentColor = contentColor,
        disabledContainerColor = containerColor.copy(alpha = DISABLED),
        disabledContentColor = contentColor.copy(alpha = DISABLED)
    )

/** A card with its colours pinned. [containerColor] and [contentColor] are the caller's to change. */
@Composable
fun FitCard(
    modifier: Modifier = Modifier,
    containerColor: Color = Brand.SurfaceHighest,
    contentColor: Color = Brand.Ivory,
    shape: Shape = CardDefaults.shape,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = modifier, shape = shape, colors = fitCardColors(containerColor, contentColor), content = content)
}

/** A card that opens something when tapped, with its colours pinned. */
@Composable
fun FitCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Brand.SurfaceHighest,
    contentColor: Color = Brand.Ivory,
    shape: Shape = CardDefaults.shape,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = fitCardColors(containerColor, contentColor),
        content = content
    )
}

/** A thin progress bar: gold over a hairline track. [progress] is read as the bar draws, so it can change without recomposing. */
@Composable
fun FitLinearProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = Brand.Gold,
    trackColor: Color = Brand.Hairline
) {
    LinearProgressIndicator(progress = progress, modifier = modifier, color = color, trackColor = trackColor)
}

/**
 * A dialog on raised onyx with an ivory title and muted body text. Its buttons are [FitTextButton]s, which the caller
 * passes as [confirmButton] and [dismissButton]. The colours are Material's current defaults for these slots, set here
 * so a library update can't move them.
 */
@Composable
fun FitAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        title = title,
        text = text,
        containerColor = Brand.Surface,
        iconContentColor = Brand.GoldLight,
        titleContentColor = Brand.Ivory,
        textContentColor = Brand.Muted
    )
}
