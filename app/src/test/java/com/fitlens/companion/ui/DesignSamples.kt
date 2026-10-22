package com.fitlens.companion.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitlens.companion.ui.design.ExerciseCard
import com.fitlens.companion.ui.design.FitTabRow
import com.fitlens.companion.ui.design.FitIcons
import com.fitlens.companion.ui.design.FitTopBar
import com.fitlens.companion.ui.design.GlassOutlinedButton
import com.fitlens.companion.ui.design.GoldButton
import com.fitlens.companion.ui.design.MenuAction
import com.fitlens.companion.ui.design.SectionLabel
import com.fitlens.companion.ui.design.SetCell
import com.fitlens.companion.ui.design.SetRow
import com.fitlens.companion.ui.design.SettingsActionRow
import com.fitlens.companion.ui.design.SettingsChoiceRow
import com.fitlens.companion.ui.design.SettingsDangerRow
import com.fitlens.companion.ui.design.SettingsFolderRow
import com.fitlens.companion.ui.design.SettingsGroup
import com.fitlens.companion.ui.design.SettingsNumberRow
import com.fitlens.companion.ui.design.SettingsStatusCard
import com.fitlens.companion.ui.design.SettingsSwitchRow
import com.fitlens.companion.ui.design.StatTile
import com.fitlens.companion.ui.design.StatusLine
import com.fitlens.companion.ui.design.TopBarAction
import com.fitlens.companion.ui.design.Trend

/**
 * The design components with made-up data, drawn by [ScreenshotTest] (#95) and checked at phone sizes by
 * [PhoneSizeTest] and [AccessibilityTest] (#157). One sample per component family; add new components here so all
 * three cover them.
 */
@OptIn(ExperimentalMaterial3Api::class)
object DesignSamples {

    @Composable
    fun topBar() {
        FitTopBar(
            title = "Thursday, 2 Oct",
            actions = listOf(
                TopBarAction(Icons.Filled.DateRange, "Calendar") {},
                // The day log's Add photos (#182): a camera icon that opens Take photo / Choose from phone.
                TopBarAction(FitIcons.Camera, "Add photos to this day", menu = listOf(MenuAction("Take photo") {}, MenuAction("Choose from phone") {})),
                TopBarAction(Icons.Filled.Add, "Add exercise") {}
            ),
            overflow = listOf(MenuAction("Settings") {})
        )
    }

    @Composable
    fun exerciseCard() {
        ExerciseCard(
            name = "Flat Barbell Bench Press",
            categoryColor = Brand.Gold,
            onClick = {},
            comment = "Paused reps, elbows tucked",
            hasPr = true,
            setsDone = 2,
            setsTotal = 3
        ) {
            SetRow(1, "60 kg, 10 reps", cells = cells("60.0", "10"), done = true, onDoneChange = {})
            SetRow(2, "80 kg, 6 reps", cells = cells("80.0", "6"), isPr = true, done = true, onDoneChange = {})
            SetRow(3, "80 kg, 5 reps", cells = cells("80.0", "5"), comment = "Last rep slow", done = false, onDoneChange = {})
        }
    }

    @Composable
    fun setRows() {
        Column(Modifier.padding(vertical = 8.dp)) {
            SectionLabel("Track", Modifier.padding(horizontal = 16.dp))
            SetRow(1, "Warm-up, 40 kg, 12 reps", cells = cells("40.0", "12"), badge = "W", badgeSpoken = "warm-up")
            SetRow(2, "100 kg, 5 reps", cells = cells("100.0", "5"), effort = "RPE 8", effortSpoken = "2 reps in reserve")
            SetRow(3, "100 kg, 5 reps", cells = cells("100.0", "5"), selected = true)
        }
    }

    @Composable
    fun statTiles() {
        Row(Modifier.padding(8.dp)) {
            StatTile("Body weight", "82.4 kg", Modifier.weight(1f), delta = "−1.2 kg from 83.6 kg", trend = Trend.Down, dateLine = "2 Oct")
            StatTile("Est. 1RM", "120 kg", Modifier.weight(1f), delta = "+5 kg from 115 kg", trend = Trend.Up)
        }
    }

    @Composable
    fun settingsRows() {
        Column {
            SettingsGroup("General")
            SettingsSwitchRow("Count warm-up sets", checked = true, summary = "Include warm-ups in totals and records") {}
            SettingsChoiceRow("Weight unit", options = listOf("kg", "lbs"), selected = 0) {}
            SettingsActionRow("Back up now", summary = "Saves a .flexnotes backup to your chosen folder", value = "Today") {}
        }
    }

    @Composable
    fun settingsRowVariants() {
        Column {
            SettingsGroup("Automatic backups")
            SettingsFolderRow("Backup folder", "Documents/FlexNotes", summary = "Choose a folder outside FlexNotes") {}
            SettingsFolderRow("FitNotes folder", "FitNotes", lost = true, lostText = "FlexNotes can no longer reach this folder. Choose it again.") {}
            SettingsActionRow("Automatic Backup", value = "On", warning = "The last automatic backup failed. Open to see why.") {}
            SettingsNumberRow("Photos per day", 2, 0..4, summary = "In the PDF report") {}
            SettingsStatusCard(listOf(StatusLine("Last backup: 2 Oct 2026, 03:12"), StatusLine("The folder can't be reached", problem = true)))
            SettingsDangerRow("Delete workout history", "Asks before deleting") {}
        }
    }

    @Composable
    fun tabsAndButtons() {
        Column {
            FitTabRow(titles = listOf("Track", "History", "Graph"), selected = 0, onSelect = {})
            Row(Modifier.padding(16.dp)) {
                GoldButton(onClick = {}) { Text("Save") }
                GlassOutlinedButton(onClick = {}, modifier = Modifier.padding(start = 8.dp)) { Text("Clear") }
            }
        }
    }

    private fun cells(weight: String, reps: String) = listOf(
        SetCell(weight, "kg", "$weight kilograms"),
        SetCell(reps, "reps", "$reps reps")
    )
}
