# Health and medications follow-ups: plan across builds (2026-10-22)

> **Superseded (2026-10-24)** by `docs/plans/2026-10-24-roadmap.md`: #218, #219, #220, #222 and #225 moved there.
> History (1.0.163): All 16 suggestions from the Medications plan
> (`docs/plans/2026-10-22-medications.md`) were picked by the owner and filed as #207–#222, each imported into the
> tracker; tracker plan item "Health and medications follow-ups: plan across builds (2026-10-22)".
> Build 1 done in 1.0.158 (the medication extras #207, #209, #210: database v26 with `med_info`, `mrecord.site` and the
> symptom tables, `MedExtras.kt` and `ui/MedicationExtras.kt`). Filed under rule 5, not in this plan: #223 (symptoms
> and sites in the doctor's report), #224 (a site from the dose reminder).
> Build 2 done in 1.0.159 (#215, the emergency card: `EmergencyCard.kt`, `report/EmergencyCardPdf.kt`,
> `ui/EmergencyCardSheet.kt`, details in `PortableSettings`, no database change), with #223 (symptoms and sites in the
> doctor's report and CSV) and #224 (the reminder's Taken records the next site) from outside the plan.
> Build 3 done in 1.0.161 (`/safe-build`; its first run, 1.0.160, failed CI for a missing Material 3 opt-in and published nothing: #213, the appointment summary: body values and quick ranges in the doctor's
> report; #214, Lock Medications: `ui/MedLock.kt`, `DeviceSettings.medLock`, private reminders; #208, medication
> overlays on exercise, Analysis and body graphs). No database change.
> Build 4 done in 1.0.162 (`/safe-build`: #212, vitals from Health Connect and by hand, `VitalUnits` for mmol/L ↔ mg/dL
> and °C ↔ °F; #211, lab markers and reference ranges for any measurement: database v27 with `measurement.ref_low`,
> `ref_high` and `lab`, `LabMarkers.kt`, `ui/LabResults.kt`, High / Low flags, the graph's range band and ranges in the
> doctor's report). Filed under rule 5 as #211's deferred part: #225 (lab results from Health Connect medical records).
> Build 5 done in 1.0.163 (`/safe-build`: #216, the readiness check-in and score, `Readiness.kt`,
> `ui/ReadinessViews.kt`, the day log's Readiness card; #217, training load, `TrainingLoad.kt`, both in the new
> Analysis › Readiness tab, `ui/ReadinessTab.kt`; #221, water and caffeine with caffeine at bedtime against sleep,
> `Drinks.kt`, `ui/DrinksViews.kt`). No database change: check-ins and drinks are body values, like intake.

## Context

When the Medications plan was approved, the owner was offered a list of gaps that Health Connect-linked apps don't
cover well, ordered by value to the most users, and picked all of them (2026-10-22). They go in this follow-up plan so
the Medications plan stays M1–M7. Everything stays local only: no internet, accounts or cloud services. Drug
interaction or dosing advice stays out (medical advice that needs an online, licensed database).

## Issues

| # | Title | Area | Depends on |
| --- | --- | --- | --- |
| #207 | Side effects and symptoms log against doses | medications | #202 |
| #208 | Dose vs outcome graphs: overlay a medication on body and strength graphs | analysis | #200 |
| #209 | Medication supply and refill tracking | medications | #202 |
| #210 | Injection site rotation with a body map | medications | #202 |
| #211 | Lab results and bloodwork with reference ranges | medications | — |
| #212 | Vitals from Health Connect: blood pressure, glucose, SpO2, HRV, temperature | body tracker | — |
| #213 | Appointment summary PDF for a chosen period | medications | #205 |
| #214 | App lock for Medications: PIN or fingerprint | medications | #200 |
| #215 | Emergency card: current medications, allergies and conditions | medications | #200 |
| #216 | Daily readiness check-in and score | analysis | — |
| #217 | Training load: acute:chronic workload ratio and monotony | analysis | — |
| #218 | Training blocks and periodisation using the cycle engine | routines | #201 |
| #219 | Reminders for other logging: weigh-ins, measurements, photos, workout days | settings | #203 |
| #220 | Home-screen widget: today's doses and today's workout | ui | #202 |
| #221 | Hydration and caffeine log | body tracker | — |
| #222 | Menstrual cycle overlay from Health Connect | analysis | — |

## Suggested grouping

- **Medication extras** (after Medications Build B): #207, #209, #210, then #214 and #215.
- **Health data** (any time): #212, #211, #221, #222, then #216.
- **Reports and graphs** (after Build D): #208, #213.
- **Training** (any time / after #201): #217, #218.
- **Platform** (after Build C): #219, #220.
