package com.ronan.qmusicwatch.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WatchConfirmButton(onClick: () -> Unit, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    WatchDialogAction(onClick, enabled, false, content)
}

@Composable
fun WatchCancelButton(onClick: () -> Unit, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    WatchDialogAction(onClick, enabled, true, content)
}

@Composable
private fun WatchDialogAction(onClick: () -> Unit, enabled: Boolean, cancel: Boolean, content: @Composable RowScope.() -> Unit) {
    if (!LocalWatchDimensions.current.isRound) {
        TextButton(onClick, enabled = enabled, content = content)
        return
    }
    Column(
        Modifier.widthIn(min = 44.dp, max = 82.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(modifier = Modifier.size(44.dp), shape = androidx.compose.foundation.shape.CircleShape,
            color = if (cancel) WatchAccent else WatchSurfaceRaised) {
            Box(contentAlignment = Alignment.Center) {
                Icon(if (cancel) Icons.Default.Close else Icons.Default.Check, null, Modifier.size(24.dp),
                    tint = Color.White.copy(alpha = if (enabled) 1f else .38f))
            }
        }
        ProvideTextStyle(TextStyle(fontSize = 11.sp, color = WatchTextSecondary)) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, content = content)
        }
    }
}
