package com.fitlens.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.fitlens.companion.ui.design.ExerciseCard
import com.fitlens.companion.ui.design.FitAssistChip
import com.fitlens.companion.ui.design.FitCard
import com.fitlens.companion.ui.design.FitCheckbox
import com.fitlens.companion.ui.design.FitFilterChip
import com.fitlens.companion.ui.design.FitLinearProgress
import com.fitlens.companion.ui.design.FitRadioButton
import com.fitlens.companion.ui.design.FitSlider
import com.fitlens.companion.ui.design.FitSuggestionChip
import com.fitlens.companion.ui.design.FitSwitch
import com.fitlens.companion.ui.design.FitTabRow
import com.fitlens.companion.ui.design.FitTextButton
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

    /**
     * Every `Fit` control from `ui/design/FitControls.kt` in each state (#306), so a Material update that moves a
     * default colour shows in the screenshot. A control that needs a name for TalkBack has one, as on a real screen.
     */
    @Composable
    fun materialDefaults() {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            SectionLabel("Chips")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FitFilterChip(selected = true, onClick = {}, label = { Text("Squat") }, modifier = Modifier.semantics { stateDescription = "chosen" })
                FitFilterChip(selected = false, onClick = {}, label = { Text("Bench") }, modifier = Modifier.semantics { stateDescription = "not chosen" })
            }
            FitFilterChip(selected = false, onClick = {}, enabled = false, label = { Text("Deadlift") }, modifier = Modifier.semantics { stateDescription = "unavailable" })
            FitAssistChip(onClick = {}, label = { Text("+ Zinc") })
            FitSuggestionChip(onClick = {}, label = { Text("Weight 82.4 kg") })
            FitTextButton(onClick = {}) { Text("Text button") }
            FitTextButton(onClick = {}, enabled = false) { Text("Disabled button") }

            SectionLabel("Switch, checkbox, radio")
            SampleRow("Switch on") { FitSwitch(checked = true, onCheckedChange = {}, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Switch on" }) }
            SampleRow("Switch off") { FitSwitch(checked = false, onCheckedChange = {}, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Switch off" }) }
            SampleRow("Switch disabled") { FitSwitch(checked = true, onCheckedChange = {}, enabled = false, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Switch disabled" }) }
            SampleRow("Checkbox on") { FitCheckbox(checked = true, onCheckedChange = {}, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Checkbox on" }) }
            SampleRow("Checkbox off") { FitCheckbox(checked = false, onCheckedChange = {}, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Checkbox off" }) }
            Column(Modifier.selectableGroup()) {
                SampleRow("Radio on") { FitRadioButton(selected = true, onClick = {}, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Radio on" }) }
                SampleRow("Radio off") { FitRadioButton(selected = false, onClick = {}, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Radio off" }) }
            }

            SectionLabel("Slider, progress, card")
            FitSlider(value = 0.4f, onValueChange = {}, steps = 4, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Volume" })
            FitSlider(value = 0.7f, onValueChange = {}, enabled = false, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Volume locked" })
            FitLinearProgress(
                progress = { 0.6f },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(6.dp).semantics { contentDescription = "Goal 60 percent" }
            )
            FitCard(Modifier.fillMaxWidth()) { Text("A card of made-up text", Modifier.padding(12.dp)) }
            FitCard(onClick = {}, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 56.dp)) {
                Text("A tappable card", Modifier.padding(12.dp))
            }
        }
    }

    /** A label with a control on its right, as a settings row has. */
    @Composable
    private fun SampleRow(label: String, control: @Composable () -> Unit) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            control()
        }
    }

    private fun cells(weight: String, reps: String) = listOf(
        SetCell(weight, "kg", "$weight kilograms"),
        SetCell(reps, "reps", "$reps reps")
    )
}
