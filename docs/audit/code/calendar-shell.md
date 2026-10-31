# Code audit: calendar-shell

> No full audit yet: rows from build gates only.
> Build gate 1.0.198: 1 function checked.
> Audited 2026-10-31 against 1.0.198 (working tree on 830f7e1, nothing committed). Files read in full: 0 of 1 in scope; the rest of the area was not read.
> Results: 0 Verified, 1 Read OK, 0 Defect, 0 Risk, 0 Improve, 0 Not checked.

## Files

| File | Read in full |
| --- | --- |
| ui/TodayWidget.kt | No (build gate: changed functions only) |

## Checks

| Function, class or concern | Check | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| TodayWidgetActions.handle: time from the shared clock | Dates and time | TodayWidget.kt:204 | Read OK | Same value, now through the seam; ends in MedReminders.arm, whose clearStale removes the dose's notification. No test presses the widget | |
