# Function audit: calendar-shell

> No full audit yet: rows from build gates only.
> Build gate 1.0.198: 3 functions checked.
> Audited 2026-10-31 against 1.0.198 (working tree over 830f7e1, before CI ran the tests named). Files read in full: 3 of the 3 this gate needed; not the area.
> Results: 0 Proved, 3 Read OK, 0 Fails, 0 Gap, 0 Not checked.

## Files

| File | Read in full |
| --- | --- |
| App.kt | Yes |
| data/AppHooks.kt | Yes |
| ui/TodayWidget.kt | Yes |
| ui/MainActivity.kt | No (build gate: changed functions only) |

## Functions

| Function | Expected | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| Widget Taken | Logs that dose at the shared clock's time, says so | TodayWidget.kt:204 | Read OK | `Dates.nowDateTime()` → `MedSchedules.take`. No test drives the widget | |
| Widget Taken clears the notification | Only that dose's goes | TodayWidget.kt:231 | Read OK | `handle` → `MedReminders.arm` → `clearStale`. The same clearing is proved in the medications ledger, not through the widget. Its site clash is the medications ledger's site row | |
| Notification and alarm-icon intents carry a data URI | Never read as a file to import | MainActivity.kt:396, 415 | Read OK | `Intent(context, MainActivity)` has no action, so `handleIntent` returns at `else -> return` after the extras | |
