# Training Load & Energy Expenditure: Reference Specification

> **Source:** supplied by the owner, 2026-10-26. The text below is the owner's, unchanged except that the tables are
> tidied to plain Markdown and the data model is set out as a code block.
>
> **How FlexNotes applies it:** roadmap Builds 10–13 in `docs/plans/2026-10-24-roadmap.md` (issues #240–#247).
> Owner decisions (2026-10-26):
> - A session is logged like any other entry: the built-in **Session** exercise type (#240), one set per session,
>   predefinable in workouts and editable at any time. No timed prompt or notification.
> - The cardio zone is recorded by the user (Zone 1–5), with an on-demand help sheet (#241).
> - In: session load, ratio bands and the spike warning, internal vs external load (#242, #243); combat and other
>   sports with an intensity choice (#244); gross and net calories (#245); a Rounds type (#246); starter sport
>   exercises (#247).
>
> **Not adopted**, and why:
> - **Heart-rate metrics and recovery** (§2 priority 4, §3.4, `average_hr`, `max_hr`, `hr_zones_minutes`): reading
>   them from Health Connect would duplicate the wearable's own app (human first, value-add only). The user records
>   the zone they trained in instead (#241).
> - **Wearable imports beyond Health Connect** (§6.3: Garmin, Whoop, Apple Health, Google Fit): each needs an account,
>   a cloud service or the internet, which the local-only rule forbids. Health Connect stays the one on-phone source.
> - **Team-sport GPS metrics** (§5.2: high-speed running, accelerations, collisions): they need GPS or sensor hardware.
>   Anything a user counts by hand fits a custom exercise type (#14).
> - **A timed RPE prompt** (§6.1, 20–40 minutes after the session): the owner wants sessions logged like any entry,
>   with no timed prompts or notifications. The RPE is entered when the user logs or edits the session.

---

**Training Load & Energy Expenditure**

**Reference Specification for Multi-Sport Tracking Application**

*Competitive Combat Sports and Physical Training Sports*

## 1. Core Purpose & Scope

This specification defines metrics, formulas, data models, and implementation guidance for tracking energy
expenditure, internal training load, and external training load in sports where the primary goal is physical
performance improvement through structured training (skill + conditioning + competitive preparation).

### In Scope

- Competitive combat sports / martial arts training (hobbyist to professional level training in the same environment)
- Team sports with significant physical conditioning components
- Individual endurance, power, and skill-based sports involving repeated high physical demand

### Out of Scope

- Pure cardio/fitness classes marketed as martial arts (e.g., cardio kickboxing)
- Primarily recreational or low-physical-demand activities (shooting sports, most boating, camping, leisure hiking, etc.)

## 2. Primary Metrics Hierarchy

Metrics ranked by usefulness for progressive overload tracking and energy estimation in intermittent, skill-based
sports:

| Priority | Metric | Type | Primary Use Case | Calculation / Capture |
| --- | --- | --- | --- | --- |
| 1 | Session RPE (sRPE) | Internal | Overall training stress | User rates 0–10 ~30 min post-session |
| 2 | Session Load | Internal | Progressive overload & weekly load | sRPE × duration (minutes) |
| 3 | Live / High-Intensity Volume | External | Specific competitive stimulus | Minutes or number of hard rounds/sets |
| 4 | Heart Rate Metrics | Physiological | Intensity distribution & recovery | Wearable (avg HR, zones, HR recovery) |
| 5 | Estimated Energy Expenditure | Energy | Calorie tracking & cross-sport comparison | MET formula or wearable estimate |
| 6 | Sport-Specific Volume Metrics | External | Skill/conditioning density | Strikes, takedowns, distance, sets, etc. |

## 3. Core Formulas

### 3.1 Session Load (Foster Method)

**Session_Load = Session_RPE × Duration_Minutes**

- Session_RPE: Integer or decimal 0.0–10.0 (CR-10 scale preferred)
- Captured approximately 30 minutes after session ends for best validity

### 3.2 Acute:Chronic Workload Ratio (ACWR)

**ACWR = Acute_Load / Chronic_Load**

- Acute_Load = Sum of Session_Load over last 7 days
- Chronic_Load = Average weekly Session_Load over previous 21–28 days (rolling)

**Common risk bands:**

- < 0.8 → underloading / detraining risk
- 0.8–1.3 → optimal range
- \> 1.5 → elevated injury risk

### 3.3 MET-based Energy Expenditure (Gross)

**Calories = (MET × 3.5 × Body_Weight_kg / 200) × Duration_Minutes**

- Use active duration when possible (exclude long instructional periods)
- Provide both gross and net (MET – 1) options in the application

### 3.4 Heart Rate Recovery (Simple)

**HR_Recovery_1min = Peak_HR_end_of_bout – HR_at_60s_post_bout**

Higher values generally indicate better fitness and recovery status.

## 4. MET Reference Values by Category

Values derived from the Compendium of Physical Activities and sport-specific research. Applications should allow
users to select intensity modifiers that scale the base MET.

### 4.1 Combat Sports / Martial Arts (Competitive Training Context)

| Sport / Activity | Base MET (Moderate) | Hard / Sparring MET | Notes |
| --- | --- | --- | --- |
| Brazilian Jiu-Jitsu (gi/no-gi) | 9.0–10.3 | 11.0–13.0 | Rolling intensity dominates |
| Judo | 10.3–11.3 | 12.0–14.0 | Throws + groundwork |
| Wrestling (all styles) | 9.5–11.0 | 12.0–14.0 | Very high anaerobic demand |
| Boxing | 8.5–10.0 | 11.0–13.0 | Sparring higher than bag work |
| Muay Thai / Kickboxing | 9.5–10.5 | 11.5–13.5 | Clinch and kicks raise cost |
| MMA (mixed) | 10.0–11.5 | 12.0–14.5 | Highest variability |
| Karate / Taekwondo (competitive) | 9.0–10.5 | 12.0–14.3 | Combat simulation peaks high |
| Wrestling / Grappling drills | 7.0–9.0 | — | Lower than live |
| Technical / positional sparring | 7.5–9.5 | — | Controlled intensity |

**Intensity Modifiers (multiply base MET):**

- Light / technical focus: × 0.75–0.85
- Moderate class: × 1.0
- Hard live focus: × 1.15–1.30
- Fight simulation / competition pace: × 1.30–1.50

### 4.2 Other Physical Training Sports

| Category | Example Sports | Typical MET Range | Key Volume Metrics |
| --- | --- | --- | --- |
| Team Invasion Sports | Soccer, Rugby, Basketball, Lacrosse, Hockey | 8–12 | High-intensity running, accelerations, collisions |
| Court / Net Sports | Tennis, Badminton, Squash, Volleyball | 7–11 | Points, high-intensity efforts, court coverage |
| Endurance Sports | Running, Cycling, Rowing, Swimming, XC Skiing | 6–14 (pace dependent) | Distance, pace/power, elevation, time in zones |
| Strength / Power Sports | Weightlifting, Powerlifting, Strongman, Sprints/Throws | 5–10 (session dependent) | Tonnes lifted, sets × reps × load, peak power |
| Combat-Adjacent | Fencing, Historical European Martial Arts | 7–11 | Bouts, high-intensity exchanges |
| Hybrid / Functional | CrossFit, Hyrox, Obstacle Course Racing | 8–13 | Work capacity, round times, total volume |

## 5. Recommended Data Model

### 5.1 Session Object (Core)

Recommended JSON structure for a training session:

```json
{
  "session_id": "uuid",
  "user_id": "uuid",
  "sport": "enum or free text + mapped category",
  "session_type": ["technical", "drilling", "live_sparring",
                   "conditioning", "mixed", "competition", "strength"],
  "start_time": "ISO8601",
  "duration_minutes": "float",
  "live_high_intensity_minutes": "float (optional but highly recommended)",
  "session_rpe": "float 0-10",
  "session_load": "float (calculated)",
  "average_hr": "int (optional)",
  "max_hr": "int (optional)",
  "hr_zones_minutes": {"z1": 0, "z2": 0, "z3": 0, "z4": 0, "z5": 0},
  "estimated_calories": "float",
  "met_used": "float",
  "body_weight_kg_at_time": "float",
  "notes": "string",
  "sport_specific_metrics": {}
}
```

### 5.2 Sport-Specific Metrics Examples

- **Combat:** `{"hard_rounds": 6, "round_duration_sec": 180, "takedowns": 4, "submissions_attempted": 2}`
- **Team sports:** `{"high_speed_running_m": 450, "accelerations": 38, "collisions": 12}`
- **Endurance:** `{"distance_km": 12.4, "avg_pace": "4:35", "elevation_m": 180, "avg_power": 245}`
- **Strength:** `{"total_tonnage_kg": 12500, "main_lifts": [...]}`

## 6. Implementation Recommendations

1. **Default Session RPE prompt timing:** Prompt the user 20–40 minutes after session end (or on next app open if
   offline).
2. **Smart defaults by sport:** Pre-populate likely MET ranges and suggested sport-specific fields based on the
   selected sport.
3. **Dual calorie sources:** Primary = user-selected MET + formula. Secondary = wearable import (Apple Health, Google
   Fit, Garmin, Whoop, etc.). Allow the user to choose the source of truth or display both.
4. **Load monitoring dashboard:** Surface daily/weekly Session Load, ACWR with colour coding, live high-intensity
   volume trend, and comparison of internal vs external load.
5. **Edge cases to handle:** Very short high-intensity sessions (e.g. 20 min hard sparring); long low-intensity
   technical sessions; multiple sessions per day; competition days (higher RPE expected for same external work);
   illness / poor recovery (RPE inflation).
6. **Validation rules:** Session RPE required for Session Load calculation; warn if duration < 15 min or > 240 min
   without confirmation; flag large day-to-day load spikes (> 30–50% increase).

## 7. Sport Category Mapping Suggestions

Recommended taxonomy for the application's sport picker:

- **Combat / Martial Arts:** BJJ, Judo, Wrestling, Boxing, Muay Thai, Kickboxing, MMA, Karate, Taekwondo, Sambo, etc.
- **Team Sports:** Soccer, Rugby, American Football, Basketball, Hockey, Lacrosse, Handball
- **Court / Racket:** Tennis, Squash, Badminton, Volleyball, Table Tennis
- **Endurance:** Running, Cycling, Swimming, Rowing, Cross-country skiing, Triathlon
- **Strength & Power:** Weightlifting, Powerlifting, Strongman, Sprinting, Throwing events
- **Hybrid:** CrossFit, Hyrox, Military / tactical training, Obstacle racing

## 8. Key Principles for the Application

- Prioritise Session Load (sRPE × duration) and live high-intensity volume over pure calorie or average HR numbers for
  long-term progressive overload tracking.
- Treat MET-based calories as useful but secondary estimates.
- Make sport-specific volume metrics first-class citizens — they are the closest equivalent to "weight on the bar".
- Support both simple logging (duration + RPE + sport) and advanced logging (HR + live minutes + sport-specific
  stats).
- Surface ACWR and weekly load trends prominently for injury-risk awareness.

*This document is intended as a comprehensive reference for implementation by a coding agent. It provides clear data
models, formulas, reference values, and UX priorities required to build a robust multi-sport training load and
energy tracking system focused on real competitive physical training.*
