@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fitlens.companion.ui.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.fitlens.companion.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.fitlens.companion.ui.Brand
import com.fitlens.companion.ui.GoldHairline
import com.fitlens.companion.ui.Spacing
import androidx.compose.ui.res.stringResource

/**
 * An icon action in a [FitTopBar]. [description] is what TalkBack reads. With a [menu], tapping the icon opens it as a
 * small dropdown under the icon instead of calling [onClick] (the day log's Add photos, #182); a menu of one entry
 * runs that entry straight away.
 */
data class TopBarAction(
    val icon: ImageVector,
    val description: String,
    val enabled: Boolean = true,
    val menu: List<MenuAction> = emptyList(),
    val onClick: () -> Unit = {}
)

/** One entry in an overflow menu ([FitTopBar], [ExerciseCard], [ListRowWithMenu]). */
data class MenuAction(val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

/**
 * The FlexNotes top bar (#80): title, optional letter-spaced subtitle, up to three [actions], an optional Settings gear
 * and an overflow menu, over a gold hairline.
 *
 * - Pushed screens pass [onBack] and get a back arrow; tab screens leave it null and are centre-aligned.
 * - To collapse on scroll, pass a [scrollBehavior] and add `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)`
 *   to the scrolling content.
 * - [trailing] is a slot for anything the lists can't express, such as an existing dropdown; it sits after [actions].
 * - [titleMenu] turns the title into a dropdown (with a ▾), as the library's routine switcher does in FitNotes (#21).
 * - [brandMark] puts the FlexNotes character before the title, where FitNotes shows its app icon (#141). It's the torso
 *   art, as every small icon is (`CLAUDE.md` rule 4), placed unedited. Only for a screen with no back arrow.
 */
@Composable
fun FitTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    centered: Boolean = onBack == null,
    actions: List<TopBarAction> = emptyList(),
    overflow: List<MenuAction> = emptyList(),
    onSettings: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    /** What TalkBack reads for the back arrow, e.g. "Close full screen" where Back would mislead (#96). */
    backLabel: String = stringResource(R.string.cmn_back),
    titleMenu: List<MenuAction> = emptyList(),
    /** An icon in the navigation slot instead of a back arrow, such as the exercise screen's workout drawer (#129). */
    navigation: TopBarAction? = null,
    brandMark: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    var titleOpen by remember { mutableStateOf(false) }
    val colors = TopAppBarDefaults.topAppBarColors(
        // Transparent over the glass gradient drawn behind the bar (#102).
        containerColor = Brand.Black.copy(alpha = 0f),
        scrolledContainerColor = Brand.Black.copy(alpha = 0f),
        titleContentColor = Brand.Ivory,
        navigationIconContentColor = Brand.Gold,
        actionIconContentColor = Brand.Gold
    )
    val titleContent: @Composable () -> Unit = {
        Column(horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
            if (titleMenu.isEmpty()) {
                FittedTitle(
                    title,
                    if (onBack != null || navigation != null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall
                )
            } else {
                Box {
                    Row(
                        Modifier
                            .heightIn(min = Spacing.touch)
                            .clickable(onClickLabel = stringResource(R.string.cmn_switch)) { titleOpen = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FittedTitle(title, MaterialTheme.typography.titleLarge, Modifier.weight(1f, fill = false))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Brand.Gold)
                    }
                    DropdownMenu(expanded = titleOpen, onDismissRequest = { titleOpen = false }) {
                        titleMenu.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.label) },
                                onClick = { titleOpen = false; m.onClick() },
                                enabled = m.enabled
                            )
                        }
                    }
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle.uppercase(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    val navigationContent: @Composable () -> Unit = {
        if (navigation != null) {
            IconButton(onClick = navigation.onClick, enabled = navigation.enabled) {
                Icon(navigation.icon, contentDescription = navigation.description)
            }
        } else if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backLabel)
            }
        } else if (brandMark) {
            Image(
                painterResource(R.drawable.search_character),
                contentDescription = null,
                // The torso art is wider than tall (665 × 414), so the mark keeps that shape.
                modifier = Modifier.padding(start = Spacing.md).size(width = 45.dp, height = 28.dp).clip(RoundedCornerShape(4.dp))
            )
        }
    }
    // Icons that don't leave the title room (a narrow phone at a large font, #182) move into the ⋮ menu.
    var fits by remember { mutableIntStateOf(3) }
    val shown = actions.take(minOf(3, fits))
    val moved = actions.drop(shown.size).filter { it.enabled }.flatMap { a ->
        a.menu.ifEmpty { listOf(MenuAction(a.description, onClick = a.onClick)) }
    }
    val menu = moved + overflow
    val actionContent: @Composable RowScope.() -> Unit = {
        shown.forEach { a ->
            if (a.menu.size > 1) {
                ActionMenu(a)
            } else {
                IconButton(onClick = a.menu.singleOrNull()?.onClick ?: a.onClick, enabled = a.enabled) {
                    Icon(a.icon, contentDescription = a.description)
                }
            }
        }
        trailing()
        if (onSettings != null) {
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.cmn_settings)) }
        }
        if (menu.isNotEmpty()) OverflowMenu(menu)
    }
    val density = LocalDensity.current
    Column(
        modifier.background(Glass.bar).onSizeChanged { size ->
            fits = iconsThatFit(
                width = with(density) { size.width.toDp() },
                fontScale = density.fontScale,
                others = (if (onBack != null || navigation != null || brandMark) 1 else 0) + (if (onSettings != null) 1 else 0),
                actions = actions.size,
                menu = overflow.isNotEmpty()
            )
        }
    ) {
        if (centered) {
            CenterAlignedTopAppBar(
                title = titleContent,
                navigationIcon = navigationContent,
                actions = actionContent,
                colors = colors,
                scrollBehavior = scrollBehavior
            )
        } else {
            TopAppBar(
                title = titleContent,
                navigationIcon = navigationContent,
                actions = actionContent,
                colors = colors,
                scrollBehavior = scrollBehavior
            )
        }
        GoldHairline()
    }
}

/**
 * How many of a bar's [actions] icons fit beside the title in a bar [width] wide, with [others] icons already there
 * (the back arrow or brand mark, the Settings gear) and the ⋮ button when [menu] has entries. Each icon is 48dp, and
 * the title keeps at least [TITLE_ROOM] (more at a large font, where [FittedTitle] can only shrink it so far). At least
 * one, at most three.
 */
internal fun iconsThatFit(width: Dp, fontScale: Float, others: Int, actions: Int, menu: Boolean): Int {
    val icon = 48.dp
    val title = if (fontScale >= 1.5f) TITLE_ROOM_LARGE else TITLE_ROOM
    fun count(withMenu: Boolean): Int {
        val fixed = icon * (others + if (withMenu) 1 else 0) + Spacing.lg
        return ((width - fixed - title) / icon).toInt().coerceIn(1, 3)
    }
    val n = count(menu)
    // Moving an icon adds the ⋮ button when the bar has none yet.
    return if (!menu && n < actions) count(true) else n
}

private val TITLE_ROOM = 96.dp
private val TITLE_ROOM_LARGE = 144.dp

/** A top bar icon that opens its [TopBarAction.menu] under it (#182). */
@Composable
private fun ActionMenu(a: TopBarAction) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, enabled = a.enabled) {
            Icon(a.icon, contentDescription = a.description)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            a.menu.forEach { m ->
                DropdownMenuItem(text = { Text(m.label) }, onClick = { open = false; m.onClick() }, enabled = m.enabled)
            }
        }
    }
}

/**
 * The top bar's one-line title, made smaller when it doesn't fit, so a date such as "Thursday, 2 Oct" stays whole on a
 * small phone at a large font size (#157). It stops at [MIN_TITLE_SIZE] of its size at the standard font size; only a
 * title still too long then ends in an ellipsis.
 */
@Composable
private fun FittedTitle(title: String, style: TextStyle, modifier: Modifier = Modifier) {
    // Never smaller than [MIN_TITLE_SIZE] of the title's size at the standard font size, however large the user's is.
    val floor = (MIN_TITLE_SIZE / LocalDensity.current.fontScale).coerceAtMost(1f)
    var scale by remember(title, style, floor) { mutableFloatStateOf(1f) }
    Text(
        title,
        modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = if (scale == 1f) style else style.copy(
            fontSize = if (style.fontSize.isSpecified) style.fontSize * scale else style.fontSize,
            lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * scale else style.lineHeight
        ),
        onTextLayout = { layout ->
            if ((layout.hasVisualOverflow || layout.isLineEllipsized(0)) && scale > floor) {
                scale = (scale - 0.1f).coerceAtLeast(floor)
            }
        }
    )
}

private const val MIN_TITLE_SIZE = 0.8f

/** A 48dp overflow ("more") button that opens [items] as a dropdown. */
@Composable
fun OverflowMenu(items: List<MenuAction>, description: String = stringResource(R.string.cmn_more_options)) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = description)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m.label) },
                    onClick = { open = false; m.onClick() },
                    enabled = m.enabled
                )
            }
        }
    }
}
