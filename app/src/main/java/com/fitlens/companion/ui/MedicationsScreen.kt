package com.fitlens.companion.ui

import androidx.compose.runtime.Composable
import com.fitlens.companion.data.Snapshot

/**
 * Medications (#200), from the day log's ⋮ menu after Body tracker: the Body tracker's Track, History and Graph tabs,
 * + to log a dose and the pencil to add, edit, reorder or move medications, for the measurements marked as
 * medications (#199) only. It reads [Snapshot.medicationView], so the body screens' panes and sheets are reused as
 * they are, and a medication never shows on the Body tracker, the day log or a body graph.
 */
@Composable
fun MedicationsScreen(snap: Snapshot, nav: Nav, startEditing: Boolean = false) =
    BodyTracker(snap.medicationView, nav, startEditing, medications = true)

/** One medication (#200): Track, History and Graph, as one body measurement ([BodyMeasurementScreen]). */
@Composable
fun MedicationScreen(snap: Snapshot, nav: Nav, name: String, page: Int = 0) =
    BodyMeasurementScreen(snap.medicationView, nav, name, page)
