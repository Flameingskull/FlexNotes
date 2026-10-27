# FlexNotes

<p align="center"><img src="branding/flexnotes-icon-512.png" alt="FlexNotes icon" width="180"></p>

**Your workouts, body stats and progress photos in one private Android app.**

*Formerly FitLens. Renamed FlexNotes in 1.0.124; it installs over the old app as an update and keeps all your data.*

[![Latest release](https://img.shields.io/github/v/release/Flameingskull/FlexNotes?label=latest%20release)](https://github.com/Flameingskull/FlexNotes/releases/latest)
[![Build](https://github.com/Flameingskull/FlexNotes/actions/workflows/build.yml/badge.svg)](https://github.com/Flameingskull/FlexNotes/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

FlexNotes puts your training history, body measurements and progress photos together, day by day. It lets you see what
your body looked like next to what you lifted and what you measured. Everything stays on your phone.

## Purpose and where it's heading

FlexNotes started as a companion to [FitNotes](https://www.fitnotesapp.com/). It read FitNotes backups and matched
each progress photo to that day's workout and measurements. **It is now a workout logger in its own right.** The aim
is to record workouts the way FitNotes does, plus what FitNotes can't: photos, progress videos, PDF reports and
custom metrics.

- **Today:** FlexNotes opens on today's training log and moves around the way FitNotes does. It logs your workouts
  (an exercise library with twelve exercise types plus types you make yourself, including **Session** for sports,
  martial arts and classes with 16 sports ready to use and **Rounds** for sparring, bag work and rolls, each with up to three values and a
  metric of your own, set-by-set entry with set types, effort, comments and a done tick, workouts made of days you
  name with planned sets and rests, a workout timer and a rest timer), analyses your training (with graphs you can
  compare side by side, overlay with a body measurement and pin to an overview, and strength relative to your
  bodyweight), tracks goals, and imports and shows your FitNotes history. It also manages progress photos, tracks body
  measurements (with body fat calculated from them) and custom metrics, shows the nearest progress photo beside a
  graph point, makes slideshows, videos and PDF reports that remember your options, exports photos as ordinary image files, reads
  steps, food, body values, vitals, sleep and other apps' workouts from Health Connect on the phone, keeps medications
  with dose schedules (including tapering and as-needed doses) and reminders, lab results with reference ranges, a
  daily readiness check-in with training load from sets or from how hard each session felt, training blocks,
  reminders to log, and a home-screen widget, and backs everything up locally. Every
  exercise, set and workout records whether it came from FitNotes or was created in FlexNotes, so the two histories sit
  side by side without colliding.
- **In progress:** FitNotes is the guide for every screen that does what a FitNotes screen does, so moving across
  feels familiar. A screen-by-screen pass laid each FlexNotes screen beside FitNotes and fixed the differences, one
  group per release from 1.0.75 to 1.0.87: the day log and workout drawer, the exercise screen, the library and
  workout editor, the calendar and Body Tracker, Analysis, the photo screens and finally Settings, now FitNotes's
  single list. In 1.0.96 the photo, viewer, compare, slideshow and PDF screens joined the same design system, and in
  1.0.101 Settings had an accessibility pass and gained Reset settings to defaults. From 1.0.103 to 1.0.107 every
  screen's wording moved into the app's string resources, with counts that read correctly for one or many, ready for
  translations ([#94](https://github.com/Flameingskull/FlexNotes/issues/94)), and 1.0.108 and 1.0.109 did the same for
  the messages built while saving, importing and backing up ([#156](https://github.com/Flameingskull/FlexNotes/issues/156)).
  From 1.0.110 to 1.0.112 Settings was finished ([#41](https://github.com/Flameingskull/FlexNotes/issues/41): status
  and warnings, search that opens the page at the setting, keyboard and switch access) and the motion and polish pass
  closed ([#93](https://github.com/Flameingskull/FlexNotes/issues/93))
  ([parity epic #134](https://github.com/Flameingskull/FlexNotes/issues/134), building on the
  [redesign epic #79](https://github.com/Flameingskull/FlexNotes/issues/79)), along with the FitNotes parity
  ([#59](https://github.com/Flameingskull/FlexNotes/issues/59)) and analysis hub
  ([#58](https://github.com/Flameingskull/FlexNotes/issues/58)) epics. From 1.0.113 to 1.0.118 the data layer moved to
  Room, Android's standard database library ([#36](https://github.com/Flameingskull/FlexNotes/issues/36)): every
  upgrade is checked against a recorded layout, and every read and save goes through queries checked when the app is
  built. From 1.0.119 to 1.0.122 big workout changes, photo dates and screen updates got faster and more accurate (photos
  can now re-read their dates from their metadata), and in 1.0.124 the app was renamed FlexNotes. From 1.0.125 to
  1.0.131 everything became editable where it's shown ([#171](https://github.com/Flameingskull/FlexNotes/issues/171)):
  body values on any day and in History, FitNotes values included, reordering in place behind a Reorder toggle, and
  a profile with a TDEE calculator and daily calories ([#172](https://github.com/Flameingskull/FlexNotes/issues/172),
  [#173](https://github.com/Flameingskull/FlexNotes/issues/173), [#174](https://github.com/Flameingskull/FlexNotes/issues/174)).
  1.0.133 added food logging and a TDEE measured from it ([#176](https://github.com/Flameingskull/FlexNotes/issues/176)); 1.0.134 made saving a set as quick with
  years of history as with weeks ([#60](https://github.com/Flameingskull/FlexNotes/issues/60)) and fitted the workout screens to small phones at the largest font
  size; 1.0.136 converts every imported body value into your own units ([#178](https://github.com/Flameingskull/FlexNotes/issues/178)). 1.0.137 counts
  daily steps with the phone's step counter for the calorie estimate ([#175](https://github.com/Flameingskull/FlexNotes/issues/175)), and 1.0.139 stops
  logged walks and runs being counted twice ([#181](https://github.com/Flameingskull/FlexNotes/issues/181)). 1.0.141 adds FlexNotes's own camera for progress
  photos, from a camera button on the day log ([#182](https://github.com/Flameingskull/FlexNotes/issues/182), [#183](https://github.com/Flameingskull/FlexNotes/issues/183)), and converts imported FitNotes
  cardio distances into your unit ([#180](https://github.com/Flameingskull/FlexNotes/issues/180)). From 1.0.143 to 1.0.147 FlexNotes learned to read **Health
  Connect**, on the phone and read only: steps, food and macros from apps such as MacrosFirst, bodyweight, body fat,
  active calories, resting heart rate, sleep and other apps' workouts ([#184](https://github.com/Flameingskull/FlexNotes/issues/184) to [#190](https://github.com/Flameingskull/FlexNotes/issues/190)). 1.0.147 and
  1.0.148 export progress photos as image files, by hand or with each automatic backup ([#195](https://github.com/Flameingskull/FlexNotes/issues/195)), and 1.0.148
  can read Health Connect's older data too ([#192](https://github.com/Flameingskull/FlexNotes/issues/192)). 1.0.149 can write your bodyweight, body
  fat and workouts to Health Connect ([#191](https://github.com/Flameingskull/FlexNotes/issues/191)). 1.0.150 checks what every shared control tells TalkBack on
  each build, and 1.0.151 gave graphs a wider, colour-blind-checked palette ([#198](https://github.com/Flameingskull/FlexNotes/issues/198)). From 1.0.152 FlexNotes
  has **Medications**: any measurement can be marked as a medication and moves to its own screen with its whole
  history ([#199](https://github.com/Flameingskull/FlexNotes/issues/199), [#200](https://github.com/Flameingskull/FlexNotes/issues/200)), and 1.0.154 adds dose schedules made of on and off cycles,
  started from any date, with today's doses ticked off as they're taken ([#201](https://github.com/Flameingskull/FlexNotes/issues/201), [#202](https://github.com/Flameingskull/FlexNotes/issues/202)).
  1.0.156 to 1.0.158 added dose reminders, dose changes, a Medications report for a doctor, supply and refill
  warnings, injection sites and symptom ratings ([epic #206](https://github.com/Flameingskull/FlexNotes/issues/206), [#207](https://github.com/Flameingskull/FlexNotes/issues/207), [#209](https://github.com/Flameingskull/FlexNotes/issues/209),
  [#210](https://github.com/Flameingskull/FlexNotes/issues/210)), and 1.0.159 puts symptoms and sites in the doctor's report, records a site from the reminder and
  adds an emergency card ([#215](https://github.com/Flameingskull/FlexNotes/issues/215), [#223](https://github.com/Flameingskull/FlexNotes/issues/223), [#224](https://github.com/Flameingskull/FlexNotes/issues/224)). 1.0.161 adds Lock Medications, an
  appointment summary and medication overlays on graphs ([#213](https://github.com/Flameingskull/FlexNotes/issues/213), [#214](https://github.com/Flameingskull/FlexNotes/issues/214), [#208](https://github.com/Flameingskull/FlexNotes/issues/208)); 1.0.162 reads vitals from
  Health Connect and adds lab results with reference ranges ([#212](https://github.com/Flameingskull/FlexNotes/issues/212), [#211](https://github.com/Flameingskull/FlexNotes/issues/211)); 1.0.163 adds the daily readiness
  check-in and score, training load, and water and caffeine ([#216](https://github.com/Flameingskull/FlexNotes/issues/216), [#217](https://github.com/Flameingskull/FlexNotes/issues/217), [#221](https://github.com/Flameingskull/FlexNotes/issues/221)). 1.0.164 splits
  Medications into Today, Schedules and Medications tabs, adds doses with no end, and lets a running schedule's start
  date be fixed or the schedule start again from day 1 ([#227](https://github.com/Flameingskull/FlexNotes/issues/227), [#228](https://github.com/Flameingskull/FlexNotes/issues/228), [#229](https://github.com/Flameingskull/FlexNotes/issues/229)). 1.0.165 adds a simpler
  schedule setup, tapering and step-up doses, and as-needed medications ([#232](https://github.com/Flameingskull/FlexNotes/issues/232), [#234](https://github.com/Flameingskull/FlexNotes/issues/234), [#233](https://github.com/Flameingskull/FlexNotes/issues/233)).
  1.0.166 adds training blocks for workouts and a body outline on the camera ([#218](https://github.com/Flameingskull/FlexNotes/issues/218), [#230](https://github.com/Flameingskull/FlexNotes/issues/230)); 1.0.167
  reads water, caffeine, your menstrual cycle and lab results from Health Connect ([#226](https://github.com/Flameingskull/FlexNotes/issues/226), [#222](https://github.com/Flameingskull/FlexNotes/issues/222), [#225](https://github.com/Flameingskull/FlexNotes/issues/225));
  1.0.168 adds reminders to weigh in, measure, take photos and train, and a home-screen widget ([#219](https://github.com/Flameingskull/FlexNotes/issues/219), [#220](https://github.com/Flameingskull/FlexNotes/issues/220));
  1.0.169 lets a training block lighten or add to a workout's planned sets ([#235](https://github.com/Flameingskull/FlexNotes/issues/235)). 1.0.171 keeps FlexNotes to
  what helps a workout, fitness and medication log: it removed water, caffeine, the menstrual cycle and lab results
  read from other apps again, and medication dates read as real weekday dates ([#237](https://github.com/Flameingskull/FlexNotes/issues/237), [#238](https://github.com/Flameingskull/FlexNotes/issues/238), [#239](https://github.com/Flameingskull/FlexNotes/issues/239)).
  1.0.173 adds **sessions**: a BJJ class, a tennis match or how hard a gym day felt, logged like any set with its
  length, rating, hard minutes and cardio zone, and a Session load for training load ([#240](https://github.com/Flameingskull/FlexNotes/issues/240)–[#243](https://github.com/Flameingskull/FlexNotes/issues/243)). 1.0.176
  adds 16 ready-made sports and martial arts and lets you set up your own sport ([#249](https://github.com/Flameingskull/FlexNotes/issues/249)). 1.0.177 gives sports their own
  calories, scaled by intensity, with gross and net figures ([#244](https://github.com/Flameingskull/FlexNotes/issues/244), [#245](https://github.com/Flameingskull/FlexNotes/issues/245)); 1.0.178 adds the **Rounds** type,
  optional starter sport exercises and the rest timer in the phone's notification and lock screen ([#246](https://github.com/Flameingskull/FlexNotes/issues/246), [#247](https://github.com/Flameingskull/FlexNotes/issues/247), [#250](https://github.com/Flameingskull/FlexNotes/issues/250));
  1.0.180 and 1.0.181 add sport counts such as takedowns or points ([#266](https://github.com/Flameingskull/FlexNotes/issues/266)) and begin a screen-by-screen review so
  every page reads as real weekday dates and keeps its layout at large font sizes ([#251](https://github.com/Flameingskull/FlexNotes/issues/251)–[#254](https://github.com/Flameingskull/FlexNotes/issues/254)).
- **Next:** the rest of the screen-by-screen review, so each one suits people first ([#263](https://github.com/Flameingskull/FlexNotes/issues/263): body and photos,
  medications, settings, calendar, widget and notifications), then a clean-up of code left from earlier versions
  ([#264](https://github.com/Flameingskull/FlexNotes/issues/264)) and an accessibility pass on a real phone ([#157](https://github.com/Flameingskull/FlexNotes/issues/157)). The plan is `docs/plans/2026-10-24-roadmap.md`; other
  ideas are on the [feature request list](https://github.com/Flameingskull/FlexNotes/issues?q=is%3Aissue+is%3Aopen+label%3Aenhancement).
- **FitNotes stays supported** as an import source. You can import during first-run setup or at any time afterwards.
  Imports always merge and never overwrite FlexNotes data. There's no export back to FitNotes.

### Exercises, workouts and routines

- An **exercise** is one movement, such as Bench Press, kept in the exercise library.
- A **workout** is one you make yourself (FitNotes calls it a routine): a name and days you name freely, such as
  "Monday" or "Push Day", each holding its exercises and how their sets are filled. FlexNotes suggests the next day each
  time you train.
- A **logged workout** is what you recorded on a date. Adding a workout adds a whole day of exercises at once; a
  control that adds one exercise always says **Add exercise**.

### Principles

- **Local only.** No accounts, no cloud services, no subscriptions and **no internet permission**. Google cloud backup
  is turned off for FlexNotes. Backups go only to a folder you choose.
- **Work where the data is shown.** Anything you can see, you can change, move or delete right there, on any date,
  whether you typed it in or it came from FitNotes. Reordering is done in place: a **Reorder** button in the top bar
  shows drag handles, and turns gold while it's on.
- **Every update installs over the last one** and keeps your data. Database changes are always migrated, never reset.
- **Android phones** (Android 10 or newer), across phone screen sizes. A self-hosted web version may come later.
- **The FlexNotes look:** luxury black and vibrant gold. Cards, bars and buttons are smoked glass with fine gold
  rims, graphs start from a red line over a translucent gold fill and use a wider, colour-blind-checked palette where
  several series or slices need telling apart, rises show in green and falls in red, and all text is ivory or
  gold, never black. Everything is set in **Manrope**, a clean, highly legible typeface bundled with the app, on one
  calm type scale, with FitNotes-style uppercase section headings over a fine gold rule. Text wraps between words,
  never inside one. The FlexNotes character stands
  faintly behind every screen, and its torso appears in notifications and beside the magnifying glass in search
  fields.

---

## What it does

- **Logs your workouts.** Open any day — past, present or a day you haven't trained yet — add exercises to it, and
  record each set. The set entry screen shows only the fields that exercise uses, pre-fills from the last time you
  did it, and has − and + buttons either side of each value for nudging the numbers. Sets read as in FitNotes, each value in its own column with its
  unit. Each set has a speech-bubble button for its own comment, and the exercise itself can carry an **exercise
  comment** for that day: a large box for detailed notes ("left shoulder tight"), shown under its sets and in its
  History. The last one appears under the box the next time you log the exercise, and its editor lists the earlier
  ones. Sets can be edited or
  deleted, and a deleted set can be brought straight back with **Undo**. A new set
  that beats your best weight for that many reps or more is marked as a **personal record** straight away, with an
  optional vibration and message. **Settings → Calculate Personal Records** works every PR mark out again from your history.
  **Settings** also chooses whether the screen stays on while you log, whether new sets fill in from
  your last workout or start empty, and whether the next set is selected after you update one.
- **Set types and effort.** Mark a set as warm-up, drop set or to failure (shown as a gold **W**, **D** or **F**).
  Warm-ups stay out of records and statistics unless you choose to count them. Optionally record how hard each set
  felt, as **RPE** or **reps in reserve**.
- **Exercise library.** Every category and exercise in one place, with quick add, notes, editing and deletion, side
  by side on wide screens. Star the ones you use most and they come first in every picker. Each exercise has a type:
  Weight & reps, Distance & time, Weight & distance, Time, Weight & time (a weighted plank or loaded carry), Reps &
  time, Reps & distance, or Weight, Reps or Distance alone. It can also have its own weight step, rest time and the
  graph it opens on, all shown at a glance by the **(i)** button on its screen. New and edited exercises open in a full-screen form, as
  in FitNotes, where ✓ saves and ✓+ saves and starts the next exercise in the same category. **Merge into…** joins a duplicate (say
  "Bench Press" and "Barbell Bench Press") into one exercise with all its history, goals, comments and places in your
  workouts; later FitNotes imports follow the merge. Starting without a FitNotes backup, you can add a starter library of common exercises, only when you
  ask and never on top of exercises you already have.
- **Workout editing.** Add exercises to a day, and add or edit the workout comment under the day's summary. **Copy Workout** copies a logged
  workout to another day (leaving out any exercises you choose), moves it, or copies a previous workout into today,
  and a workout can be deleted.
  Each of these opens as a sheet, and the result can be undone. **Share workout** sends a workout to any app as text or
  as a FlexNotes image card (with one of that day's photos only if you choose it), with a checklist of exactly which
  exercises and sets to include.
- **Workouts.** The exercise library's title (**+** on the day log) switches between **All exercises**, each of your
  workouts and **Create new workout**. A workout shows its days as cards, with the next one marked in gold, and
  **Log all** adds a whole day at once, with Undo. In the editor, each day has **+** (Add exercise) and a menu to
  rename, duplicate, copy or delete it (the top bar's **Reorder** drags days and exercises), and each exercise chooses how its sets are filled: **copy previous**,
  **predefined** sets (a blank weight or reps copies last time's), or **none**. Each exercise can also plan its
  **rest**: **copy previous rest** (the rests from its last workout, as sets can be copied), or one rest for every
  set or a rest per set, and a longer break before the next exercise; a day's menu sets them for every exercise at
  once, and each exercise shows its plan, such as "Rest 90 s · then 2 min". On the day log's ⋮ menu, **Add
  workout** adds a day from any workout (or exercises you pick on the spot) after a review, **Replace this workout**
  swaps the day's sets for one, and **Save as a workout day** turns a logged day into a new workout or a day of an
  existing one. Exercises can be swapped on a day or for good.
- **Timers.** **⋮ → Workout time** sets a workout's start and finish, or runs a workout timer (optionally started by
  the first set). The **rest timer** sits in the top bar as an alarm clock; while a rest runs, its countdown takes the
  clock's place, on the exercise screen and the day log. It takes any length from 1 second to 60 minutes, pauses,
  starts after each set if you like, and plays a sound (your choice of the phone's sounds, at its own volume) and
  vibrates when rest is over. The default length and these options are also in **Settings → Rest Timer**.
  While a rest or workout timer runs, its countdown also shows in the phone's notification, on the lock screen and
  (on Android 16) as a Live Update in the status bar; FlexNotes asks once, with the reason, before turning that on.
  An exercise can have its own rest time, and a workout can plan rests per set and between exercises: after each set
  the timer uses that set's planned rest, then the workout's, then the exercise's own, then your usual length, and
  after an exercise's last set it uses the planned break before the next one. A day keeps the rests it was logged with,
  even if the workout changes later. Both timers keep running with the screen off, in a notification with its own
  buttons.
- **Training blocks.** A workout's editor can plan it in blocks from a start date: **Add training blocks** starts with
  three weeks of **Build** and a one-week **Deload**, and every block can be renamed, lengthened in weeks or days,
  marked as a deload, duplicated, deleted or reordered. The last block can be **Ongoing**, and the plan can start
  again after its last block. A block can also **change the planned sets**: weights a percentage lighter or heavier
  (rounded to the exercise's weight step) and sets added or taken away per exercise, applied when a day of that
  workout is logged during the block and shown in the review first. The day log opens with a card for the block
  you're in, how far through it you are and what comes next.
- **Reorder, supersets and ticking sets off.** Drag exercises into a new order in the workout drawer, or choose
  **⋮ → Reorder exercises** on the day log to drag them there; a superset always moves as one. Group
  exercises into **supersets**: they sit together with a gold bar, and saving a set moves you round-robin to the next
  exercise in the group, with the rest timer starting after each round. Workouts keep their supersets. Every set on
  the exercise screen has a **done tick**, which can start the rest timer. Ticking an exercise's last set shows
  "Done. Next: …" with **Undo** and opens the next exercise of the day a moment later, in the workout's order; after the
  last one, the day log opens with **Workout complete**, offering to stop the workout timer. With **Settings → Mark Sets
  Complete**, the day log and the workout drawer show how many of each exercise's sets are done. Saving or ticking a
  set gives a short pulse, and a new personal record its own pattern, following the phone's touch feedback setting.
- **Goals.** Each exercise has a **Goals** tab for targets such as max weight, estimated 1RM, reps, or volume in a
  set or workout, with progress bars and an optional goal line on its graph. Body measurements can have a goal too:
  increase, decrease or a specific value.
- **Imports FitNotes backups** (`.fitnotes`), which contain workouts, sets, PRs, comments, workout times, exercises,
  categories, all body tracker measurements and custom measurements. It also accepts Body Tracker CSV exports.
  Before importing, FlexNotes shows what will be added and what is skipped as already present, and offers to save a
  FlexNotes backup first (see [FitNotes imports and your FlexNotes data](#fitnotes-imports-and-your-flexnotes-data)).
- **Optional FitNotes folder sync** for moving over gradually. Point FlexNotes at the folder where FitNotes saves its
  backups, then tap **Sync now**, or turn on automatic sync to import the newest backup whenever FlexNotes opens.
  Automatic sync is **off by default**. You can also share a backup from FitNotes straight into FlexNotes.
- **FlexNotes's own camera:** the camera button on the day log (and Take photo on the Photos screen's +) opens a
  viewfinder inside FlexNotes. Choose the pose first, turn on a faint copy of your last photo in that pose with a bold
  white outline of your body traced from it (found on the phone, nothing uploaded) so the shots line up week to week, set a 3 or 10 second self-timer for full-body shots, and switch between the front and
  back cameras. After each shot, Retake or Use photo: a kept photo goes onto that day with its pose, into your
  backups, and never into your phone's gallery. The camera permission is asked for only when the camera opens.
- **Bulk photo import:** choose many photos or a whole folder, or share photos from your gallery. You pick the pose
  (Front, Side, Back or Other) for the batch as you import, or set a **pose for new photos** in **Settings → Progress
  Photos & Media** and FlexNotes stops asking. Each photo is dated from:
  1. camera metadata (EXIF "date taken"),
  2. then the media library date,
  3. then a date in the file name (for example `IMG_20230826_132000.jpg`, `PXL_…` or `Screenshot_2023-08-26…`),
  4. then the EXIF edit time, flagged for you to check,
  5. then the file's modified date, also flagged.

  Duplicates are detected and skipped, so re-importing a folder is safe. **Photos → ⋮ → Re-read photo dates** runs
  the same order again over photos already imported (except any you dated by hand), says how many changed, and can be
  undone.
- **Body values where you see them:** log a value with the **+** on the day log's body values card (for that day),
  on an empty day, or in the Body tracker. Tap any value, on the day log or in History, to change its value, date,
  time or comment, or delete it; a long press deletes it at once, with **Undo**. Values imported from FitNotes can be
  changed and deleted too, and a later import keeps your change: an edited value isn't doubled and a deleted one
  doesn't come back. If a value you typed later arrives in a FitNotes backup, the FitNotes copy is skipped.
- **Medications:** from the day log's ⋮ menu. Any measurement can be marked as a medication, from its ⋮ on the
  Body tracker or with **This is a medication** in its editor, and it moves to the Medications screen with every
  value logged so far (**Move to Body tracker** brings it back, with Undo). Medications are kept off the Body
  tracker, the day log, body graphs, energy estimates, body exports and Health Connect, so nothing shows twice. The
  screen has five tabs: **Today** (the doses due, supply warnings and symptoms; it opens there when a dose is due),
  **Schedules**, **Medications** (the list, like the Body tracker's Track), **History** and **Graph**, with doses
  logged, edited or deleted in place, and suggested units such as mg, mL, IU and tablets. Bodyweight, body fat, height, food, steps and Health
  Connect's values stay body measurements, because other features use them.
- **Dose schedules:** a schedule is built like a workout, from **on** and **off** phases lasting days, weeks, months
  or years (calendar months and years), each on phase holding its doses: medication, amount, time, and every day,
  chosen weekdays or every few days. **New schedule** first asks how you take it (every day, set weekdays, every few
  days or in cycles) and builds it for you. The last phase can be **Ongoing (no end)**, for a medication taken for
  good, and otherwise the cycle can repeat. A dose can **taper down** or **step up** by a set amount every few days,
  to a floor or ceiling, and every screen, reminder and report uses each day's amount. A medication can be **taken as
  needed**, with an optional most a day: Today lists it with the time since the last dose and a **Take** button.
  A schedule's earlier runs are listed, with their dates fixable and each one deletable. Start a schedule from any date, then pause, resume or end it; several can run at
  once. A running schedule's start date can be changed, or it can **start again from day 1**, keeping the earlier
  run's history; an ended one offers **Start again**. Each has Undo. The **Today** tab lists the doses due, each with
  a done box like a set's, where each schedule is in its cycle ("On · day 12 of 56", "Off · on again 3 Dec",
  "On · day 40 · ongoing"), and missed or skipped doses. Ticking a dose logs it as an ordinary medication value, once, with Undo; tapping it offers Taken or Skip,
  or a change of amount for that day or the rest of the run.
- **Dose reminders:** a notification when a scheduled dose is due, with **Taken**, **Snooze** and **Skip**, and an
  optional second reminder (Settings › Medications). The lock screen hides medication names unless you choose to
  show them. For an injected medication, **Taken** names the site it records, the one used longest ago.
- **Supply, sites and symptoms:** a medication can track its supply, counted down by every dose, with warnings
  before it runs out or expires. An injected one gets a body map that suggests the next site and keeps every dose's
  site. **Symptoms** are rated 0 to 10 on any day, shown in each medication's History and overlaid on its Graph.
- **Report for a doctor and emergency card:** the Medications ⋮ menu makes a PDF with only medication data
  (schedules, adherence, graphs, symptoms, the dose log with injection sites, comments), or the same dose log as
  CSV. Pick body values too and it becomes an **appointment summary**, with quick ranges from 30 days to a year.
  **Emergency card** makes a one-page PDF or image with current medications, allergies, conditions and an emergency
  contact, to save or share.
- **Lock Medications:** in **Settings › Medications**, the Medications screens can ask for your fingerprint or phone
  screen lock, and while locked, reminders name nothing. Medications with logged doses can be overlaid on exercise,
  Analysis and body graphs.
- **Vitals and lab results:** blood pressure, glucose, SpO2, HRV and temperature come from Health Connect or are
  logged by hand (**Add vitals**), with glucose in mmol/L or mg/dL and temperature in °C or °F. **New lab marker**
  adds common bloodwork markers, and any body value can have a **reference range**: values outside it are flagged
  **▲ High** or **▼ Low**, the graph shows the range as a band, and the doctor's report lists it.
- **Readiness and training load:** the day log's **Readiness** card is a daily check-in (sleep quality, soreness,
  energy, stress and mood), combined with HRV, resting heart rate and sleep when Health Connect has them, gives a
  score from 0 to 100. **Analysis › Readiness** graphs it, with your acute and
  chronic training load, a warning when this week jumps above 1.5× your usual load, and monotony and strain. Load
  can be measured as **Session load** (how hard each session felt × its minutes), with the ratio's band in plain
  words and a warning on the day log when a day's load jumps more than 50%. The card can be hidden in **Settings ›
  Home Screen Settings**.
- **Measurements:** the Body tracker's pencil switches it to editing measurements: an on/off switch for each, its
  unit and goal, the standard set (bodyweight and eight tape measurements), and your own, such as sleep, with their
  own unit. **Body fat** and **Height** are there for everyone. You can link one to a FitNotes measurement so imports
  fill it in, and imports keep your on/off choices.
- **Body fat from your measurements:** enter body fat by hand, or tap **Calculate from measurements**. It uses the US
  Navy circumference method (metric body-density form with Siri's equation, typically within about ±3.5 percentage
  points of underwater weighing), working from your latest height, neck and waist (plus hips for women). Each one is
  shown with its date, and any value more than 14 days old is flagged. A missing measurement is named so you can enter
  it on the spot, and impossible inputs are refused with the reason. Your sex comes from your profile, and your profile
  height is used when no Height measurement is logged.
- **Profile:** **Settings → Profile** keeps your sex, date of birth (your age is worked out for each date), height and
  daily activity outside training, from Mostly sitting to Heavy physical work, for every calculation that needs them.
- **TDEE calculator and daily calories:** from the Body tracker's ⋮ menu or your profile. It estimates your resting
  energy (Mifflin-St Jeor, or Katch-McArdle when a recent body fat value is logged), adds your daily activity and your
  logged training averaged over 28 days (or a planned week until two weeks are logged), and gives your daily total.
  Pick a goal (maintain, lose by a weekly rate or a deficit, recomposition, lean bulk, bulk or a custom target) to see
  its calories, the expected weekly change and macros, with warnings for unsafe targets, then **Set as my target**.
  Exercise calories count only what's above resting (MET − 1, from the 2024 Compendium of Physical Activities), with
  effort from RPE and the ACSM equations for walking and running; each exercise can also choose its own **Activity**.
  **Daily activity from my steps** (Settings → Profile) counts steps with the phone's own step counter, kept on the
  phone, and uses them for the daily activity once a few days are counted. A walk or run you log as a workout isn't
  counted a second time: its distance, at your stride, comes off that day's steps.
  A session shows its calories both ways, "X kcal (Y above resting)", on the day log and in the Calories sheet; the
  TDEE uses the part above resting. A **Calories** card on the day log shows what each day burned and needed, the same every day or following that
  day's training, and opens the breakdown. Every figure is an estimate.
- **What you ate, and a measured TDEE:** tap the Calories card on any day to type that day's calories, plus protein,
  fat and carbs if you track them; the card then reads what you ate against what the day needed. They're kept as body
  values (Calories eaten and so on), so the Body tracker graphs them and backups include them. Once you've logged
  calories on 14 of the last 28 days and weighed in three times at least two weeks apart, the TDEE calculator also
  shows your TDEE **measured** from what you ate and your bodyweight trend, and your goal can follow either figure.
- **Guided setup:** a fresh install opens a short, skippable setup: restore a FlexNotes backup, or choose your units,
  an automatic backup folder, a FitNotes import, your progress photos, the starter exercise library and the standard
  body measurements.
  **Settings → Show Setup Again** opens it any time.
- **Settings:** open it from the **⋮** menu on the day log. As in FitNotes, it's one list. **SETTINGS** has Theme,
  Unit System, Calendar Week Start, Default Weight Increment, Home Screen Settings, Track Personal Records, Mark Sets
  Complete, Auto-Select Next Set and Keep Screen On, in FitNotes's order, then FlexNotes's own: your units, your
  **Profile**, the rest timer, how new sets fill in, set types, effort, warm-ups, the workout timer, the
  estimated 1RM formula and settings, and **Progress Photos & Media** (the pose for new photos, how the gallery is
  grouped, remembered slideshow and video options, and the PDF report's page style and photos per day). **DATA**
  has Backup, Restore, Automatic Backup, Spreadsheet Export, Calculate Personal Records, Delete Workout History,
  **Reset Settings to Defaults**, Import From FitNotes and **Health Connect**, and **OTHER** has Help (short guides inside the app),
  Feedback, Change Log (this update's notes, which also appear once after each update), Show Setup Again, Privacy
  Policy and About (version, privacy, licences, and **Save speed**: how long saving a set takes on your phone).
  On/off settings are checkboxes, and the search field filters the list so a setting can be changed right there.
  Matches on the pages Settings opens (Backups, Rest Timer, Progress Photos & Media and so on) are listed below with
  their current value, and tapping one opens that page at the setting, outlined in gold for a moment. A number
  setting can be stepped with − and + or typed in exactly. **Automatic Backup** shows On or Off, with a warning sign
  when a backup failed, is overdue or its folder can't be reached. TalkBack reads each row as one sentence (its title,
  value or state, then its explanation), a setting that can't be used yet stays in place, dimmed, and says why, and
  with a keyboard or switch access every row shows a gold outline when it has focus. Warnings always have a sign and
  words, never colour alone.
  **Unit System** switches weight, distance and body measurements to metric or imperial together, with the separate
  units below it for any mix (stored values never change, and the plate calculator's plates convert too). Any
  exercise can keep its own weight unit, and any body measurement its own unit. Settings that belong to the phone,
  such as backup folders and schedules, stay on the phone and are never replaced by a restore. Preferences such as
  your units, chart types, comparisons, pinned graphs and photo and media options travel with your backups.
- **Health Connect** (in **Settings → Health Connect**): for each kind of data, choose FlexNotes
  or Health Connect as its source. Steps, food and macros (from apps such as MacrosFirst), active calories,
  bodyweight and body fat readings, resting heart rate and sleep come in as body values marked Health Connect, and
  runs, rides, swims and other sessions from your watch or fitness app become cardio sets in the day log, each added
  once and left as you edit it. FlexNotes syncs when it opens, from **Sync now**, and hourly in the background if
  you allow it. Health Connect lets apps read the last 30 days; **Older data** asks, with an explanation first, to
  read everything before that once. Values you type are never replaced. Under **Write to Health Connect** you can
  switch on writing your bodyweight, body fat and workouts (each logged day with a start and end, as a strength
  training session) so other apps see them: each is written once, kept up to date as you edit it, and deleted there
  when you delete it in FlexNotes. Nothing read from Health Connect is written back.
- **Reminders and widget:** **Settings › Reminders** has reminders to weigh in, measure, take progress photos and
  train, each at your own time and days, and quiet when that day's already done; tapping one opens the right screen.
  The **FlexNotes today** home-screen widget lists today's doses with **Taken**, as-needed medications with **Take**,
  and today's workout with its training block.
- **Data tools** (in Settings):
  - **CSV export:** workouts (with set and exercise comments) or body data for any date range, in kilograms or
    pounds, saved to a file or shared, for spreadsheets. The columns are listed on the page, with a custom metric's
    name, value and unit at the end. A CSV can't be restored; that's what backups are for.
  - **Delete workout history** by date range, by exercise or both, after a preview of what will go. Exercises,
    categories, photos and body data are kept, personal records are worked out again, and deleted FitNotes sets stay
    deleted on the next import. A safety copy is taken first, so it can be undone.
  - **Reset settings to defaults:** puts units, the rest timer, logging, display, photo and report options back as
    they were when FlexNotes was new, with **Undo** straight afterwards. Workouts, photos, measurements, backups,
    backup folders and schedules, pinned graphs, your profile and your calorie target are kept. Home Screen Settings, Rest Timer
    and Progress Photos & Media can also be reset on their own, from **Reset this section** in each page's ⋮ menu.
- **Getting around:** as in FitNotes, there's no tab bar. The **day log** is home, and its top bar has **Calendar**,
  a **camera** (Take photo or Choose from phone, for that day), **+** (the exercise library and workout switcher), the rest countdown while you rest, and a **⋮** menu with the
  day's workout actions, then Analysis, Body tracker, Medications, Photos and Settings. Each of those slides in on top of the log,
  and Back returns you to it. Rotating the phone, or Android closing FlexNotes in the background, keeps your place:
  the screen you were on and the ones under it, with their open sheets, selections and filters. Choices such as a
  period, a pose or a sort order sit in compact dropdowns, so the data gets the screen; the controls you use
  mid-workout stay in view. Tapping an exercise card on the day log opens it out into the exercise screen, and Back
  shrinks it into the card again. With Android's **Remove animations** on, screens change at once. Screens with
  nothing to show yet say so and offer one way forward, and a photo whose file can't be read shows a warning mark.
- **Views:**
  - **Day log (home):** laid out as FitNotes's. It opens on today, under a flat ‹ TODAY › bar: swipe or use the
    arrows to move one day at a time, empty days included, tap the day to jump to any date, and long-press it to come
    back to today. The day's body values come first, in one card with each name and its value, and the change since
    the previous value (amount, date and the value it moved from) on its own line. Then come the day's progress photos
    and a card for each exercise: its name, a gold tick once every set is done, and its sets as bold figures in
    columns. Tap an exercise to log its sets, or long-press it for its options (comment, history, records, superset,
    swap, remove); tap its comment to edit it. The workout's time, totals and comment follow: tap the time to change
    it. An empty day reads "Workout log empty" and offers **Add exercise**, **Add workout**, **Copy previous workout**
    and **Log body value**.
  - **All days** (from the Calendar's top bar): a timeline of every day, with that day's photos, measurements and
    workout summary.
  - **Calendar:** as in FitNotes, a month grid (swipe between months) with category dots, photo and measurement
    dots, today in gold and the selected day in deep gold. The selected day's workout shows below the grid (beside it
    on wide screens and unfolded foldables); **Open day**
    (or a second tap) opens its log, and tapping one of its exercises opens that exercise's **overview** (history,
    graph, records, stats and goals in one sheet). The search button **filters** the calendar by exercise or
    category and weight, reps, distance or time, fading the days that don't match and counting those that do.
  - **Body Tracker:** as in FitNotes, one screen with **Track**, **History** and **Graph** tabs for all your
    measurements. **Track** lists each one with how long ago it was logged, its latest value and the change since the
    value before (coloured by your goal), or "Tap to record a value". **History** shows every value, newest day first,
    for all measurements or one. **Graph** shows one measurement with its range, trend line, goal line, photo days and
    stats. Tap a measurement to log a value: the day, the value with − and + buttons, the time and a comment, with its
    own History and Graph (Body fat adds **Calculate from measurements**, and a new Body fat value starts from the
    figure your latest neck, waist, hips and height give, with the measurements it used listed underneath). **+** logs any measurement, the pencil
    edits the measurements in place (each with its unit, goal and a tracking checkbox), and **Reorder** drags them
    into the order every screen uses.
  - **Exercise library:** as in FitNotes, it opens on **All exercises** with the search field always at the top,
    then your categories, then a category's exercises. Tap an exercise to log it, or long-press to choose several and go through them in turn.
    Each category's ⋮ edits or deletes it, **⋮ → New category** adds one, and **Reorder** drags the categories.
    The exercise form's **Your types** makes exercise types of your own: pick what each set records (weight, reps,
    distance, time, or a metric of your own with its name and unit, such as jump height in cm). Set entry, history,
    graphs (**Best** and **Total** for the metric), records, predefined workout sets and CSV export follow the type.
  - **Sports and sessions:** the **Sports & martial arts** category holds 16 ready-made Session exercises (Gym
    Session, BJJ, Muay Thai, Boxing, Kickboxing, MMA, Wrestling, Judo, Karate, Tennis, Football, Basketball, Squash /
    Padel, Cardio, Swimming and Yoga / Mobility). Each session records its length and how hard it felt (0 to 10),
    and where the sport suits them, hard or live minutes, intensity, kind of session and cardio zone (the ⓘ explains
    each zone, with your heart rate ranges from **Profile**). To add your own sport, choose **Session** as the type
    and its kind of sport. FlexNotes suggests what to record from the sports of the same kind, and you can change
    any of it. Sessions can be predefined in workouts and have Session load, rating, length and hard-minute graphs.
    **Rounds** logs sparring, bag work or rolls as rounds, round length, rest between rounds and hard rounds, with
    the total and hard time worked out for you. **Add sport exercises** in the library offers more ready-made sports
    and Rounds starters, added only when you pick them. A Session or Rounds exercise can count up to two things of
    your own (takedowns, submissions, points): set them in the exercise form, log them with each set, plan them in a
    workout, graph them and export them to CSV. Each sport can have its own **Activity** (13 sports, with an easy and a
    hard figure) so its calories follow its intensity and hard minutes.
  - **Exercise screen:** **Track** (log sets: each field under an uppercase heading, its value between square − and
    + buttons, straight under the tabs, with the day's sets below as a plain list), **History** (each day under a
    FitNotes heading such as **MONDAY, SEPTEMBER 28**, every day you've done it, with its totals, that day's
    exercise comment and **Copy to today**; tap a set to correct or delete it) and **Graph** tabs. The Graph tab has
    FitNotes's list for weight exercises: **Estimated 1RM**, **Max weight**, **Workout volume**, **Workout reps**,
    **Max reps**, **Max volume** (the best single set), **Max weight for reps** (the heaviest set at a rep count you
    choose) and **Personal records** (a line through each day you set a record). Timed and distance exercises graph
    their longest set, total time and distance, and distance-and-time exercises add **Max distance**, **Max speed**
    and **Max pace**. Weight-and-reps exercises end the list with **Relative Strength**: each day's best estimated
    1RM divided by the bodyweight logged nearest that day (within 14 days; days without one are left out and marked).
    Its menu button opens the **workout drawer**: the day's exercises in order,
    to jump between or reorder, with **Add exercise** and **Add to superset** at the bottom. As in FitNotes,
    the top bar then has the rest timer, a trophy that opens **Records**, **Stats** (Max weight, Estimated 1RM, Max reps, Max volume, Workout reps and Workout volume, each with
    its date, which a tap opens, plus totals and first and last logged, for a period or any date range), **Goals** and
    a **1RM calculator**.
    Its **(i)** button shows the exercise's notes and settings, with **Edit**. Its ⋮ menu has a **set calculator** (percentages of your 1RM, or a warm-up ramp) and a **plate calculator**
    (plates per side for your bar and plates); both fill in the set.
    Rep-max records run from 1RM to 15RM, actual and estimated, for the last workout, week, month, year or all time. A heavier or equal lift for more reps counts as the record for every
    lower rep count too. By default, estimated maxes follow the validation studies: from 2 to 10 reps the mean of the
    Mayhew and Wathan formulas (the two found most accurate there), from 11 to 15 reps Wathan alone, marked as
    approximate (≈), and nothing above 15 reps; **Settings → Estimated 1RM Formula** can switch to Epley, Brzycki, Lombardi,
    O'Conner, Mayhew or Wathan instead, with a worked example of each, and **Estimated 1RM Settings** (also the gear
    beside the calculator) sets the most reps a set can have to count. Pounds convert with the exact definition
    (0.45359237 kg). Records can also cover a date range you choose.
  - **Analysis:** **Overview**, the first tab, shows every graph you've starred, Workouts or exercise graphs, as
    compact cards with the latest value and the change over its range; tap one to open it, or drag or use its menu to
    reorder or unpin it (Analysis opens there once something is pinned). **Workouts** shows your workouts, volume, sets, reps or duration per week, month or year, for all
    training, a category or an exercise; duration can be a total or an average per workout, with a graph of every
    workout's length, and its ⋮ menu can overlay a body measurement's average for each period. **Breakdown** splits your training by category or exercise in a donut chart,
    for any week, month or year with training, with arrows to step through the slices and a comparison to the period
    before. **Exercises** shows any exercise's graphs, with the same options as its Graph tab. **Goals** lists every exercise goal with its progress; **+** adds one for any exercise. **Records** puts
    1RM to 15RM for many exercises side by side, for all training, a category or the exercises you choose.
  - **Graphs** (Body tracker, exercise screen and Analysis) are sized from your screen, with their graph type, range,
    **chart type** and options in one compact row of dropdowns. Each graph can be drawn as a **line**, **bars**, an
    **area** or **steps**, and remembers your choice (it travels in backups too). They can add a dashed **trend line**:
    a smoothed curve through your values from eight points on (so a plateau, cut or bulk shows as a bend), otherwise
    the straight best-fit line, and none from fewer than three days. Its figures are written under the graph: the rate
    in the graph's unit per week, month or year, the fitted start and end, and the number of points and R², with a
    note when the trend rests on few points or explains little. Graphs can start their scale **from zero** (bars
    always do), and a line never breaks between points. Tapping a point on an exercise graph or the workout-length
    graph shows the **progress photo nearest that date** (within 14 days), which opens in the viewer, or compares it
    with a second point's photo. An exercise graph's ⋮ can **overlay a body measurement** (bodyweight, body fat or any
    measurement) on its own scale at the right; a shared graph includes body values only if you choose. Every graph opens **full screen** (the expand button or a double
    tap), where pinching across zooms the timeline and pinching up and down zooms the values, dragging moves around, and the range and options can change. TalkBack reads each graph's range and
    values and offers zoom and move actions for both. A graph's ⋮ menu can **share it as an image** in the FlexNotes
    look. An exercise graph's ⋮ can **compare** up to five exercises on one graph, each with its own colour and
    marker (tap a name in the legend to hide it, tap a date for every value on it), as values or as a percentage of
    each one's first value in the range. The **star** beside full screen pins a graph to Analysis → Overview.
  - **Photos:** a gallery grouped by day, week, month, year or pose (remembered), with pose filters and counts.
    Long-press to select photos: the top bar then shows the count, with compare, slideshow and delete, and the ⋮ menu
    sets the pose or date of all of them. The viewer shows each photo edge to edge on black; tap it to fade the
    controls away, swipe for the next, and open the details for the pose, the date and that day's body values. The
    **before/after compare** sits side by side on black, with the change in each body value, and can be shared or
    saved as an image. **⋮ → Export as image files** (or Export in the selection menu) saves copies of all your
    photos, a date range or the selection into a folder you choose, named by date and pose (such as
    `2026-10-16_front.jpg`), with the FlexNotes date and time, pose and comment in each file's photo details. Nothing
    in the folder is replaced or deleted.
- **Slideshow and video:** plays your photos in date order with overlays: the date, a day or week counter, the pose,
  chosen measurements (with the change since the start) and a moving progress chart. Every option is in one options
  sheet, and FlexNotes remembers them for next time (the dates always start at all photos; **Settings → Progress Photos
  & Media** can switch this off or reset them). It exports an **MP4 video**, rendered on the phone, in Portrait HD,
  Full HD or Square. Videos are saved to *Movies/FlexNotes* and can be shared.
- **PDF report:** a readable report with your photos, measurement charts, training summary and a daily log, in dark
  (as in the app) or light (for printing). The page style and photos per day start from your Settings choice.
- **Backups, all on your phone** (in **Settings → Backups**):
  - **Backup file:** save everything, photos included, as one `.flexnotes` file, or **share** it with an app you
    already use (email, Drive, Dropbox and so on; FlexNotes itself never uploads anything). Restore it after
    reinstalling or on a new phone, or open it straight from a file manager. The date and time in the file name can
    be switched off. Backups made before the rename (`.fitlens` files) still restore.
  - **Automatic backups:** daily or weekly to a folder you choose (for example Documents or an SD card), so they
    survive uninstalling. You choose how many to keep (the newest 3, 5 or 10). They run in the background through Android's job scheduler,
    even when FlexNotes is closed, whenever the battery isn't low. Optionally, **Back up after changes** saves a backup
    when you leave FlexNotes after changing something, at most once an hour. **Copy photos as image files** (off
    until you turn it on) also saves new or changed photos as ordinary image files in a *Photos* folder inside the
    backup folder, named and labelled as in the photo export.
  - **Status and alerts:** a status card shows the last successful backup, the next scheduled one, the folder and
    its free space, each with a tick or a warning sign. Folder rows show the folder's name, or Not set, with Choose
    or Change. If the folder can't be reached (the SD card was removed or access was lost), the row says so and a
    notification explains how to fix it.
  - **Safety copy with Undo:** before a restore, a FitNotes import, merging exercises or deleting workout history, FlexNotes keeps a copy
    of your current data on the phone. For 7 days, **Settings → Backups → Safety copy → Undo** puts it back.
  - **Phone-to-phone transfer** (Android 12+) carries FlexNotes data across when you set up a new phone with a cable or
    a direct transfer.

FlexNotes only *reads* FitNotes backups and never changes your FitNotes data. FlexNotes backups (`.flexnotes`) are for
FlexNotes only.

**Permissions:** FlexNotes asks for notifications (Android 13+) only when you set up automatic backups or start a
timer, physical activity only when you turn on step counting, the camera only when you open FlexNotes's camera, and
Health Connect's read permissions only for the kinds of data you switch to it, and its write permissions only for
what you switch on to be written (older data and background reading are asked for separately). Switching on a setting that needs them gives a one-line reason first, and if notifications
are off, the setting still works in the app and offers a way to turn them on. It also uses vibration and a foreground service for the timers, which Android grants
without asking. Photos and folders are accessed through Android's file pickers, and only the ones you choose.

## FitNotes imports and your FlexNotes data

Every category, exercise, set, workout comment and workout time records who owns it: **FitNotes** (imported) or
**FlexNotes** (created or edited in FlexNotes). FlexNotes gives every row its own id and keeps the FitNotes id only for
reference, so the two never collide. Importing a FitNotes backup follows these rules:

1. An import only **adds**. It never deletes, edits or overwrites anything already in FlexNotes, whoever created it.
2. Categories and exercises are matched **by name**, ignoring upper and lower case, so imported history and history
   logged in FlexNotes join up under one exercise. If both exist, the FlexNotes version is kept as it is.
3. A set counts as already present when FlexNotes has a set on the same date, for the same exercise, with the same
   weight, reps, distance and time. Identical sets are counted, so 3 × 5 × 100 kg in the backup matches three such
   sets in FlexNotes. Importing the same backup twice changes nothing.
4. Workout comments and times are skipped when the same comment, or the same start and end, is already on that date.
   Body measurements are skipped when the same measurement, date, time and value exists, or when you entered the same
   value by hand that day, whatever unit either was kept in.
6. **Imported values arrive in your units.** The import reads the unit each FitNotes measurement was logged in and
   compares it with the unit you use for that measurement in FlexNotes. When they differ (inches and centimetres,
   pounds and kilograms) the value is converted before it's recorded; when they match it's recorded as it is. An
   import never changes your units. Importing a backup again converts values an import before 1.0.136 brought in
   without their unit. Set weights were always correct: FitNotes and FlexNotes both keep them in kilograms and show
   them in your unit. Cardio distances (from 1.0.141) are read in the unit each set was logged in (kilometres, miles,
   metres or yards) and converted to the exercise's distance unit, or yours; importing again converts runs an
   earlier import brought in as logged.
5. **Your changes in FlexNotes win.** After you rename an exercise or category, the FitNotes name still maps to it.
   After you delete imported data, or edit an imported set, comment, time or body value, the next import doesn't bring
   the original back. Re-creating a deleted exercise with the same name lets its FitNotes history import again.

The rules are also documented in the code (`data/Workouts.kt`).

---

## Download and install

1. On your Android phone (Android 10 or newer), open the
   [latest release](https://github.com/Flameingskull/FlexNotes/releases/latest) and download `FlexNotes-1.0.N.apk`.
   No GitHub account is needed.
2. Open the downloaded file. Android will ask you to allow installs from your browser or file manager. Allow it once.
3. New releases install **over** the old one and keep all your data.

Each release includes the APK, the full source code, SHA-256 checksums and professionally written notes on what
changed. The version number goes up with every release (`1.0.<build>`). A number can be skipped: a build that fails
publishes nothing but still uses up its number (as with 1.0.54, 1.0.56, 1.0.95, 1.0.115 and 1.0.116), and earlier releases skipped some
(1.0.8 was followed by 1.0.13) because pull request checks shared the release build counter
([#78](https://github.com/Flameingskull/FlexNotes/issues/78)). A higher number is
always the newer build.

## First-time setup (in the app)

The first time FlexNotes opens, a short guided setup walks through the steps below. Every step can be skipped, and
**Settings → Show Setup Again** brings it back. To do it by hand:

0. **Just want to start logging?** FlexNotes opens on today's log: tap **+**, pick a category and an exercise, and
   record your sets. Save a day you like as a workout day (**⋮ → Save as a workout day**), or build a workout from
   the library's title, to add a whole day in one go next time. You don't need FitNotes at all: steps 1 and 2 are only for bringing an existing FitNotes history across.
1. **Settings → Import From FitNotes → Import a backup file**, then choose your latest `FitNotes_Backup_….fitnotes`.
2. Optional, if you keep using FitNotes for a while: **Settings → Import From FitNotes → FitNotes backup folder** and pick the folder where FitNotes saves its backups. Tap **Sync now** after making a backup in FitNotes, or turn on
   automatic sync so FlexNotes imports the newest backup each time it opens.
3. **⋮ → Photos → + → Import a whole folder** (for example your camera folder or a "Progress" album), or choose
   photos. FlexNotes dates each photo and places it on the right day.
4. If any photos had no camera date, a banner says **"N photos need their date checked"**. Tap it to accept or fix
   the dates.
5. Tag poses (Front/Side/Back) while importing, in the photo viewer, or with multi-select, or choose a pose for every
   new photo in **Settings → Progress Photos & Media**. Slideshows and comparisons can then use one pose.
6. **Settings → Backups:** choose a backup folder and turn on automatic backups.

## Limits

- Sets are reordered with buttons (and TalkBack actions), not by dragging. Routines from FitNotes backups aren't
  imported.
- Calories are estimates from your profile and what you log. Intake is typed as each day's totals (there's no food
  database or barcode scanning). Steps come from the phone's own step counter or from Health Connect.
- FlexNotes writes only bodyweight, body fat and workouts to Health Connect, each switched on separately. Without the
  **Older data** permission, only the last 30 days are read.
- FlexNotes never gives drug interaction or dosing advice: that would need an online, licensed database. The
  emergency card shows only what you entered and logged; it isn't a medical record. Readiness and training load are
  guides, not medical advice.
- A training block's change to the planned sets applies when a workout day is logged from **Add workout** or **Log
  all**; sets typed one by one are logged as typed. Reminders follow the days and times you set, not a training
  plan's rest days.
- A Session or Rounds exercise counts at most two things of your own per set. The timer in the status bar as a Live
  Update needs Android 16; earlier versions show it in the notification and on the lock screen.
- Some storage providers don't let apps set a file's modified date, so exported photo files can show the export time
  there; their photo details always carry the date taken. Formats whose details can't be written, such as HEIC, are
  exported unchanged.
- For FitNotes cardio distances, the metres unit is confirmed from a real backup; kilometres, miles and yards follow
  FitNotes's order of units. Distances on exercises that aren't cardio in FitNotes (a loaded carry) stay as logged.
- Graphs use compact dropdowns rather than FitNotes's 1m / 3m / 6m / 1y / all buttons, so the graph gets the room
  ([#125](https://github.com/Flameingskull/FlexNotes/issues/125)).
- Planned rests apply to days logged from a workout from 1.0.73 on; earlier days use each exercise's own rest or
  your usual length.
- Android doesn't let one app read another app's private data, and FitNotes has no interface for other apps. So
  FlexNotes can't pull data out of FitNotes directly or make FitNotes create a backup. Folder sync is the closest to
  automatic that Android allows. It works best if FitNotes saves its backups to one folder on your phone.
- If a set you already imported is later edited or deleted in FitNotes, the next import adds the changed version as a
  new set.
- Background backups follow Android's battery rules, so a scheduled backup can run a few hours after it's due.
- A backup can be restored into the same or a newer FlexNotes. Backups made with 1.0.113 or later can't be restored
  into 1.0.112 or earlier, so update the app before restoring.
- Photos are copied into FlexNotes, so deleting a photo in your gallery doesn't remove it from FlexNotes, and the reverse.
  Save a backup, or turn on automatic backups, before changing phones.

---

## Report a bug or request a feature

Use the [Issues](https://github.com/Flameingskull/FlexNotes/issues/new/choose) tab and choose **Bug report** or
**Feature request**. You need a free GitHub account. Every report is triaged, and the planned work is visible in the
open issues. Fixes and features arrive in the next release.

## Contribute code

See [CONTRIBUTING.md](CONTRIBUTING.md). In short: fork the repository, make your change and open a pull request.
GitHub builds a test APK for every pull request.

## Build it yourself

The app is native Android: **Kotlin** and **Jetpack Compose** (Material 3), with a local SQLite database managed by **Room**. Every query is checked against the
schema when the app is built, and the schema itself is recorded in `app/schemas/`. It targets
Android 16 (API 36) and runs on Android 10 (API 29) or newer.

Open the project in **Android Studio** and click **Run**, or run `./gradlew assembleDebug`. Your own builds are signed
with your debug key, so Android won't install them over the official release. Test on an emulator or a spare phone.
Alternatively, save a backup first (**Settings → Backups → Save backup**) and uninstall the official app.

Unit tests run on the JVM with Robolectric, no emulator needed: `./gradlew testDebugUnitTest`. They build databases
the way older FlexNotes versions left them and check that every upgrade keeps every row and ends in exactly the
recorded Room schema, run the database queries on a real database, import a made-up FitNotes
backup to check the merge rules, save and restore `.flexnotes` backups, and check the 1RM formulas and unit
conversions, graph zoom, the remembered chart types, pinned graphs and comparisons, and the in-memory data
updates. Screenshot tests draw the shared components at two phone widths and at double text size and compare them
with saved images, so layout slips fail the build.

Every push to `main` runs the unit tests, then is built and signed by GitHub Actions and published as a release; a
failing test stops the release. Pull requests run the tests and get a debug build only.

## Maintenance

Dependencies and Actions are kept current automatically by **Dependabot** (`.github/dependabot.yml`): Gradle
dependencies weekly, GitHub Actions monthly. Kotlin, the Compose compiler plugin and KSP arrive as one grouped pull
request, because their versions are locked to one another and updating them separately breaks the build. Dependabot
pull requests run the `check` job only — a debug build with no secrets — so nothing is published until a merge.

**Every year, before 31 August**, raise `compileSdk` and `targetSdk` to the API level Google Play then requires,
raising AGP and the Gradle wrapper if that level needs it, and review the behaviour changes for that Android version:
permissions, the photo picker, the foreground services and notifications used by the rest timer and automatic
backups, and edge-to-edge. Confirm the exact level against Google's current policy page at the time.

Every build also runs the unit tests in CI, including a set save on four years of history that checks how many
set rows it reads (`SaveSpeedTest`), screenshot tests of the design components, and phone-size checks at 320dp wide with the largest
font (`PhoneSizeTest`: 48dp touch targets, no text cut off or overlapping).

Updates never change `applicationId` (`com.fitlens.companion`), the signing setup, the signing secrets or
`BUILD_OFFSET`. Every build stays an update that installs over the previous one and keeps its data.

## License

[MIT](LICENSE) © 2026 Flameingskull

The bundled Manrope typeface is © 2018 The Manrope Project Authors, used under the
[SIL Open Font License 1.1](LICENSES/Manrope-OFL.txt).
