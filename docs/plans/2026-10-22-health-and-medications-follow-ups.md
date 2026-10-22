# Health and medications follow-ups: plan across builds (2026-10-22)

> **Status (2026-10-22): Not started.** All 16 suggestions from the Medications plan
> (`docs/plans/2026-10-22-medications.md`) were picked by the owner and filed as #207–#222, each imported into the
> tracker; tracker plan item "Health and medications follow-ups: plan across builds (2026-10-22)".
> Left: #207–#222. Most wait on the Medications plan (see "Depends on" below); #211, #212, #216, #217, #221 and #222
> can start any time.
> Next: finish the Medications plan first, then triage these with `/new-build`.

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
