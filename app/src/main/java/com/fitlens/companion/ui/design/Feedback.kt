package com.fitlens.companion.ui.design

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fitlens.companion.ui.Brand
import com.fitlens.companion.ui.FitShapes

/**
 * A snackbar host styled for FlexNotes: raised surface, ivory text, gold action (#80). It keeps clear of the navigation
 * bar itself (#140): the app draws edge to edge, so without this a message and its action sat under the system's
 * Back / Home buttons.
 */
@Composable
fun UndoSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier.navigationBarsPadding()) { data ->
        Snackbar(
            snackbarData = data,
            shape = FitShapes.row,
            containerColor = Brand.SurfaceHighest,
            contentColor = Brand.Ivory,
            actionColor = Brand.Gold,
            dismissActionContentColor = Brand.Muted
        )
    }
}
