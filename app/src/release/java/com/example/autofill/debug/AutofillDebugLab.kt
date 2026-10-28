package com.example.autofill.debug

import androidx.compose.runtime.Composable

/**
 * Release no-op stub for the Debug Autofill Test Lab (Step 8G).
 * In release builds, no debug test forms or second-app simulators are included.
 */
object AutofillDebugLab {
    const val isAvailable: Boolean = false
}

@Composable
fun AutofillDebugLabDialog(
    onDismiss: () -> Unit
) {
    // No-op in release source set
}
