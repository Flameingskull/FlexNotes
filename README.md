# FlexNotes

<p align="center"><img src="branding/flexnotes-icon-512.png" alt="FlexNotes icon" width="180"></p>

**Your workouts, body stats and progress photos in one private Android app.**

*Formerly FitLens. Renamed FlexNotes in 1.0.124; it installs over the old app as an update and keeps all your data.*

[![Latest release](https://img.shields.io/github/v/release/Flameingskull/FlexNotes?label=latest%20release)](https://github.com/Flameingskull/FlexNotes/releases/latest)
[![Build](https://github.com/Flameingskull/FlexNotes/actions/workflows/build.yml/badge.svg)](https://github.com/Flameingskull/FlexNotes/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

FlexNotes puts your training history, body measurements and progress photos together, day by day, so you can see what
your body looked like next to what you lifted and measured. Everything stays on your phone.

**Contents:** [Purpose and direction](#purpose-and-direction) · [What it does](#what-it-does) ·
[FitNotes imports](#fitnotes-imports-and-your-flexnotes-data) · [Data and privacy](#data-and-privacy) ·
[Limits](#limits) · [Download and install](#download-and-install) · [First-time setup](#first-time-setup) ·
[Help and contributing](#report-a-bug-or-request-a-feature) · [Build it yourself](#build-it-yourself) ·
[Maintenance](#maintenance) · [Licence](#licence)

---

## Purpose and direction

FlexNotes began as a companion to [FitNotes](https://www.fitnotesapp.com/): it read FitNotes backups and matched each
progress photo to that day's workout and measurements. **It is now a workout logger in its own right.** It records
workouts the way FitNotes does, and adds what FitNotes can't: photos, progress videos, PDF reports, custom metrics,
medications and training load.

- **Today (version 1.0.201):** a full workout logger, body tracker, photo journal and medication log, described under
  [What it does](#what-it-does). The latest change is **Which way is good?** for body measurements: each one can be set
  to "Up is good", "Down is good", "A specific value" or "Neither", and every green and red for it follows your choice.
- **FitNotes is the guide.** Any screen that does what a FitNotes screen does is made to feel familiar, so moving
  across is easy.
- **FitNotes stays supported as an import source**, during first-run setup or any time from Settings. Imports always
  merge and never overwrite FlexNotes data. There is no export back to FitNotes.
- **Next:** a check of every screen on a real phone at large font sizes, then more checks that each function works.
  The plan is `docs/plans/2026-10-24-roadmap.md`. Other ideas are on the
  [feature request list](https://github.com/Flameingskull/FlexNotes/issues?q=is%3Aissue+is%3Aopen+label%3Aenhancement).
  Each release's notes say what changed.

### Principles

- **Local only.** No accounts, no cloud services, no subscriptions and **no internet permission**. Google cloud backup
  is turned off. Backups go only to a folder you choose.
- **Work where the data is shown.** Anything you can see, you can change, move or delete right there, on any date,
  whether you typed it in or it came from FitNotes. Reordering is in place too: a **Reorder** button shows drag
  handles, and turns gold while it's on.
- **People first.** Dates read as real days ("Mon 27 Oct"), never counters like "day 12 of 56". Wording is plain, rows
  keep one layout however long a name is, and mistakes can be undone where you made them. FlexNotes sticks to what
  helps a workout, fitness and medication log, and doesn't copy what other apps already record.
- **You choose.** Where something could reasonably work more than one way, FlexNotes lets you pick, on the screen and
  in Settings, with a sensible default.
- **Every update installs over the last one** and keeps your data. Database changes are always migrated, never reset.
- **Android phones** (Android 10 or newer), across phone screen sizes. A self-hosted web version may come later.
- **The look:** black and vibrant gold, with smoked-glass cards and fine gold rims. Text is ivory or gold, never black,
  set in **Manrope**. Graphs start from a red line over a gold fill and use a wider, colour-blind-checked palette where
  several series need telling apart. Rises show in green and falls in red. The FlexNotes character stands faintly
  behind every screen.

### Exercises, workouts and routines

- An **exercise** is one movement, such as Bench Press, kept in the exercise library.
- A **workout** is one you make yourself (FitNotes calls it a routine): a name and days you name freely, such as
  "Monday" or "Push Day", each holding its exercises and how their sets are filled. FlexNotes suggests the next day each
  time you train.
- A **logged workout** is what you recorded on a date. Adding a workout adds a whole day of exercises at once. A
  control that adds one exercise always says **Add exercise**.

---

## What it does

### Getting around

- There is no tab bar. The **day log** is home. Its top bar has **Calendar**, a **camera** (Take photo or Choose from
  phone), **+** (the exercise library and workout switcher), the rest countdown while you rest, and a **⋮** menu.
- The ⋮ menu holds the day's workout actions, then **Analysis, Workouts, Body tracker, Medications, Photos** and
  **Settings**. Each opens on top of the log, and Back returns you to it.
- Rotating the phone, or Android closing FlexNotes in the background, keeps your place, your open sheets and anything
  you've typed but not saved.
- Screens with nothing to show say so and offer one way forward. With Android's **Remove animations** on, screens
  change at once.

### Day log and calendar

- **Day log.** Opens on today under a flat ‹ TODAY › bar. Swipe or use the arrows to move a day at a time, tap the day
  to jump to a date, and long-press it to return to today. It shows the day's body values, progress photos, an exercise
  card for each exercise (a gold tick once every set is done), and the workout's time, totals and comment.
- **Tap** an exercise to log its sets. **Long-press** it for its options: comment, history, records, superset, swap and
  remove. An empty day offers **Add exercise**, **Add workout**, **Copy previous workout** and **Log body value**.
- **Calendar.** A month grid with category, photo, measurement and medication dots. The selected day's workout shows
  below it; **Open day** opens the log, and tapping an exercise opens its overview. The search button filters by
  exercise or category and by weight, reps, distance or time. While Medications is locked, you choose what the
  calendar shows of your doses: nothing (the default), a count, or everything.
- **All days** (from the Calendar's top bar) is a timeline of every day with its photos, measurements and workout.

### Logging sets

- Set entry shows only the fields that exercise uses and fills them from your last workout. Each value has − and +
  buttons. Every set can have its own comment and a **done tick**, which can start the rest timer.
- **Set types and effort.** Mark a set as warm-up, drop set or to failure (a gold **W**, **D** or **F**). Warm-ups stay
  out of records unless you count them. You can record how hard each set felt, as **RPE** or **reps in reserve**.
- **Exercise comments.** A large box for detailed notes ("left shoulder tight"). The last note shows under the box
  next time, earlier ones are listed in its editor, and all of them appear in the exercise's History.
- **Undo** brings back a deleted set. A set that beats your best weight for that many reps is marked as a **personal
  record** straight away, with an optional vibration and message. **Settings › Calculate Personal Records** works every
  mark out again from your history.
- **Supersets** sit together with a gold bar, and saving a set moves you round the group. Ticking an exercise's last set
  opens the next one, and after the last, **Workout complete** offers to stop the workout timer.
- **Goals.** Each exercise has a **Goals** tab for targets such as max weight, estimated 1RM, reps or volume, with
  progress bars and an optional line on its graph. Body measurements can have a goal too.

### Exercise library

- All your categories and exercises in one place, with search, quick add, notes, editing and deletion. Star the ones you
  use most and they come first in every picker.
- **Exercise types:** Weight & reps, Distance & time, Weight & distance, Time, Weight & time, Reps & time, Reps &
  distance, or Weight, Reps or Distance alone. **Your types** lets you make others, including a metric of your own with
  its name and unit (such as jump height in cm). Each exercise can have its own weight step, rest time, weight unit and
  default graph.
- **Merge into…** joins a duplicate into one exercise with all its history, goals and comments. Later FitNotes imports
  follow the merge. A starter library of common exercises is added only when you ask.

### Workouts, timers and training blocks

- **Workouts.** The library's title switches between **All exercises**, each of your workouts and **Create new
  workout**. A workout shows its days as cards, with the next one marked in gold, and **Log all** adds a whole day. The
  **Workouts** screen in the ⋮ menu lists them all to view, edit, duplicate or delete, with Undo.
- **Planned sets.** Each exercise in a workout day fills its sets by **copy previous**, **predefined** sets or **none**,
  and can plan its rest per set and the break before the next exercise.
- **Day log actions.** **Add workout** adds a day from any workout. **Replace this workout** swaps the day's sets.
  **Save as a workout day** turns a logged day into a new workout. **Copy Workout** copies or moves a day, or copies a
  previous workout into today. **Share workout** sends text or an image card.
- **Timers.** The **rest timer** sits in the top bar as an alarm clock, and its countdown takes the clock's place while
  it runs. It takes 1 second to 60 minutes, can start after each set, and plays a sound and vibrates when rest is over.
  The **workout timer** records how long the workout took (**⋮ › Workout time**). Both keep running with the screen off,
  in a notification with its own buttons, and ring at the minute they are set for.
- **Training blocks.** A workout can be planned in blocks from a start date, such as three weeks of **Build** and a
  one-week **Deload**. A block can make planned weights lighter or heavier and add or remove sets. The day log shows the
  block you're in.
- **Sports and sessions.** The **Sports & martial arts** category has 16 ready-made **Session** exercises (BJJ, Muay
  Thai, Boxing, Tennis, Football, Swimming, Yoga and more). A session records its length and how hard it felt, and
  optionally hard minutes, intensity and cardio zone. **Rounds** logs sparring, bag work or rolls. A session or rounds
  exercise can count up to two things of your own, such as takedowns or points. You can add your own sport.

### Analysis and graphs

- **Analysis** has Overview (your starred graphs), Workouts (volume, sets, reps or duration per week, month or year),
  Breakdown (a donut by category or exercise), Exercises, Goals, Records and Readiness.
- **Exercise screen.** **Track**, **History** and **Graph** tabs. Graphs include estimated 1RM, max weight, volume, reps,
  personal records and relative strength (1RM against bodyweight), and time and distance exercises have their own. The
  top bar opens **Records**, **Stats**, **Goals**, a 1RM calculator, and a set and plate calculator.
- **Records and 1RM.** Rep-max records run from 1RM to 15RM for any period. Estimated maxes use the formula you choose
  in **Settings › Estimated 1RM Formula**.
- **Graphs.** Every graph can be a line, bars, an area or steps, and remembers your choice. A dashed trend line gives
  its rate per week, month or year. Ranges (1 month to all time) count back whole calendar months from today and the axis
  spans the full period. Tapping a point shows the nearest progress photo.
- **Full screen.** Any graph opens full screen (the expand button, a double tap, or turn the phone sideways) with pinch
  zoom. **Overlay** a body measurement on an exercise graph, **compare** up to five exercises, **share** a graph as an
  image, or **star** it to pin it to Analysis › Overview.
- **Readiness and training load.** A daily check-in (sleep, soreness, energy, stress, mood), combined with Health
  Connect data when you allow it, gives a score from 0 to 100. Analysis › Readiness graphs it with acute and chronic
  training load. Load can come from sets or from how hard each session felt. These are guides, not medical advice.

### Body tracker, profile and calories

- **Body tracker.** **Track**, **History** and **Graph** tabs for all your measurements. Track shows each latest value
  and the change since the one before. Log a value from the day log's body card, an empty day or the tracker. Tap any
  value to change or delete it, including values imported from FitNotes.
- **Which way is good?** Open any measurement and tap the line under its figures. Choose Up is good, Down is good, A
  specific value (with your target) or Neither. The Track row, History, the day log, graph points and bars, the Change
  and per-week figures, the trend readout and Photo Compare all follow that choice. A new setting, **Colour changes with
  no goal**, picks between rise green and fall red (the default) and plain with no green or red. Every change keeps its
  arrow and signed number, so colour is never the only signal.
- **Measurements.** The pencil edits measurements: an on/off switch, unit and goal for each, the standard set
  (bodyweight and eight tape measurements) and your own. **Reorder** sets the order every screen uses.
- **Body fat.** Enter it by hand, or tap **Calculate from measurements** (US Navy method from height, neck, waist and,
  for women, hips). Values over 14 days old are flagged, and impossible inputs are refused with the reason.
- **Profile.** **Settings › Profile** keeps your sex, date of birth, height, activity level and max heart rate once, for
  every calculation that needs them.
- **TDEE and calories.** Estimates your daily energy from your profile and logged training, with goals from maintain to
  bulk and macros. Exercise calories count only what is above resting. Type what you ate on the **Calories** card and
  FlexNotes shows a TDEE measured from your intake and weight trend once there is enough data. Steps come from the
  phone's step counter if you allow it. Every figure is an estimate.

### Photos, video and reports

- **Camera.** FlexNotes's own camera opens from the day log. Choose the pose, show a faint copy of your last photo in
  that pose with a white body outline traced from it (found on the phone, nothing uploaded), set a 3 or 10 second
  self-timer, and switch cameras. Kept photos go onto that day and into your backups, never your phone's gallery.
- **Import.** Choose many photos, a whole folder, or share from your gallery. Each photo is dated from, in order:
  camera metadata (EXIF "date taken"), the media library date, a date in the file name, the EXIF edit time (flagged)
  and the file's modified date (flagged). Duplicates are skipped, so re-importing a folder is safe. **Photos › ⋮ ›
  Re-read photo dates** runs the same order again and can be undone.
- **Gallery and viewer.** Group by day, week, month, year or pose. Long-press to select photos for compare, slideshow,
  delete, or setting the pose or date. The viewer shows each photo edge to edge, with its pose, date, that day's body
  values and a comment you can write.
- **Compare** puts two photos side by side with the change in each body value, and can share or save the result as an
  image.
- **Export as image files.** **Photos › ⋮ › Export as image files** saves copies named by date and pose (such as
  `2026-10-16_front.jpg`) to a folder you choose, with the date, pose and comment in each file's photo details. Nothing
  in the folder is replaced or deleted.
- **Slideshow and video.** Plays your photos in date order with overlays: date, pose, chosen measurements and a moving
  chart. FlexNotes remembers your options. It exports an **MP4**, made on the phone, in Portrait HD, Full HD or Square,
  saved to *Movies/FlexNotes*.
- **PDF report.** Photos, measurement charts, a training summary and a daily log, in dark or light for printing.

### Medications

- **Medications** has Today, Schedules, Medications, History and Graph tabs. Any measurement can be marked as a
  medication, and it then moves to this screen with its whole history (**Move to Body tracker** brings it back).
  Medications are kept out of the Body tracker, body graphs, energy estimates and exports.
- **Schedules** are built like a workout from **on** and **off** phases lasting days, weeks, months or years, each
  holding doses. A dose can be every day, on chosen weekdays or every few days, can taper down or step up, and the last
  phase can be ongoing. Start from any date, then pause, resume, end, change the start date or start again. A simple
  setup asks how you take it and builds the schedule for you.
- **As-needed** medications show the time since the last dose and a **Take** button, with an optional daily maximum.
- **Today** lists doses due with a done box, where each schedule is in its cycle in real dates ("On until Fri 7 Nov"),
  and missed or skipped doses. You choose when a dose counts as missed.
- **Reminders** arrive on time, one notification per dose, each with **Taken**, **Snooze** and **Skip**. The lock screen
  hides names unless you choose to show them. **Lock Medications** asks for your fingerprint or screen lock.
- **Supply, sites and symptoms.** Track supply with warnings before it runs out or expires, use a body map to suggest
  the next injection site, and rate symptoms from 0 to 10 on any day.
- **Reports.** A PDF for a doctor (schedules, adherence, graphs, symptoms, the dose log) or a CSV of the dose log, an
  appointment summary with body values, and an **Emergency card**. FlexNotes gives no dosing or interaction advice.
- **Vitals and lab markers.** Blood pressure, glucose, SpO2, HRV and temperature come from Health Connect or by hand.
  Any body value can have a **reference range**, and values outside it are flagged **▲ High** or **▼ Low**.

### Health Connect, reminders and widget

- **Health Connect** (Settings › Health Connect) is read only by default. For each kind of data, choose FlexNotes or
  Health Connect as its source: steps, food and macros, active calories, bodyweight, body fat, resting heart rate, sleep
  and workouts from other apps (as cardio sets). It syncs when FlexNotes opens, on **Sync now**, and hourly if you
  allow it. **Older data** reads beyond the last 30 days once you ask. Values you type are never replaced. You can
  switch on writing bodyweight, body fat and workouts back.
- **Logging reminders.** Settings › Reminders has reminders to weigh in, measure, take photos and train, each at your
  own time and days, and quiet when that day is already done.
- **Widget.** **FlexNotes today** lists today's doses with **Taken**, as-needed medications with **Take**, and today's
  workout.

### Settings, backups and data tools

- **Settings** is one list, as in FitNotes: SETTINGS (theme, units, week start, increments, personal records, rest timer,
  profile and more), DATA (backup, restore, export, import, Health Connect, reset) and OTHER (Help, Feedback, Change
  Log, Show Setup Again, Privacy Policy, About). The search field filters the list, and a match opens its page at the
  setting. **Unit System** switches weight, distance and body measurements together, and any exercise or measurement
  can keep its own unit. Stored values never change.
- **Backup file.** Save everything, photos included, as one `.flexnotes` file, or share it with an app you already use.
  FlexNotes itself never uploads anything. Restore it on a new phone, or open it from a file manager.
- **Automatic backups.** Daily or weekly to a folder you choose, with 3, 5 or 10 kept. They run in the background, and
  an optional **Back up after changes** saves when you leave the app. **Copy photos as image files** also saves photos
  as ordinary files. A status card shows the last and next backup, the folder and its free space, with a warning if it
  can't be reached.
- **Safety copy with Undo.** Before a restore, import, merge or delete of history, FlexNotes keeps a copy. For 7 days,
  **Settings › Backups › Safety copy › Undo** puts it back.
- **Phone-to-phone transfer** (Android 12+) carries FlexNotes data across when you set up a new phone.
- **CSV export** of workouts or body data for any date range, for spreadsheets. A CSV can't be restored.
- **Delete workout history** by date range, exercise or both, after a preview. Exercises, photos and body data are kept.
- **Reset settings to defaults** puts options back as when FlexNotes was new, with Undo. Your data is kept.
- **Guided setup.** A fresh install offers a short, skippable setup. **Settings › Show Setup Again** brings it back.
- **Accessibility.** TalkBack reads each setting and graph, rows keep 48dp touch targets, and layouts are checked at
  200% font.

---

## FitNotes imports and your FlexNotes data

FlexNotes imports FitNotes backups (`.fitnotes`) and Body Tracker CSV exports. It only *reads* them and never changes
your FitNotes data. Before importing, it shows what will be added and what is skipped, and offers a safety copy.

Every category, exercise, set, workout comment and workout time records who owns it: **FitNotes** (imported) or
**FlexNotes** (created or edited here). Each row has its own id, so the two never collide. An import follows these rules:

1. It only **adds**. It never deletes, edits or overwrites anything already in FlexNotes.
2. Categories and exercises are matched **by name**, ignoring case, so imported and logged history join under one
   exercise. If both exist, the FlexNotes version is kept.
3. A set counts as already present when FlexNotes has a set on the same date, exercise, weight, reps, distance and
   time. Identical sets are counted, so importing the same backup twice changes nothing.
4. Workout comments and times are skipped when the same one is already on that date. Body measurements are skipped when
   the same measurement, date, time and value exists, or when you typed the same value by hand that day.
5. **Your changes win.** After you rename an exercise, the FitNotes name still maps to it. After you delete imported
   data or edit an imported set, comment, time or body value, the next import doesn't bring the original back.
   Re-creating a deleted exercise with the same name lets its FitNotes history import again.
6. **Values arrive in your units.** The import reads the unit each measurement was logged in and converts it to the unit
   you use. Cardio distances are read in the unit each set was logged in. Set weights are always kept in kilograms and
   shown in your unit.

**FitNotes folder sync** is optional and off by default. Point FlexNotes at the folder where FitNotes saves backups,
then tap **Sync now**, or let it import the newest backup whenever FlexNotes opens. You can also share a backup from
FitNotes straight into FlexNotes.

The rules are also documented in the code (`data/Workouts.kt`).

---

## Data and privacy

- Everything stays on the phone. There are no accounts, no analytics and no internet permission.
- Automatic backups go only to a folder you choose. Photos and folders are reached through Android's file pickers, and
  only the ones you choose.
- FlexNotes asks for a permission only when you use what needs it, with a one-line reason first: notifications for
  backups and timers, physical activity for step counting, the camera for FlexNotes's camera, and Health Connect's read
  or write permissions for the data you switch on. Vibration and the timers' foreground service need no prompt.
- Settings that belong to the phone, such as backup folders and schedules, are never replaced by a restore.
  Preferences such as units, chart types and pinned graphs travel with your backups.
- Photos are copied into FlexNotes, so deleting a photo in your gallery doesn't remove it from FlexNotes, and the
  reverse. Save a backup, or turn on automatic backups, before changing phones.
- Backups made with 1.0.113 or later can't be restored into 1.0.112 or earlier, so update the app first.
  `.fitlens` backups from before the rename still restore.

## Limits

- Calories are estimates. Intake is typed as each day's totals, with no food database or barcode scanning.
- FlexNotes never gives drug interaction or dosing advice, and the emergency card shows only what you entered. It isn't
  a medical record. Readiness and training load are guides.
- Routines from FitNotes backups aren't imported.
- A training block's change to planned sets applies when a day is logged from **Add workout** or **Log all**, not to
  sets typed one by one. Reminders follow the days and times you set, not a plan's rest days.
- A session or rounds exercise counts at most two things of your own per set. The timer as a Live Update in the status
  bar needs Android 16. Earlier versions show it in the notification and on the lock screen.
- Some storage providers don't let apps set a file's modified date, so exported photos can show the export time there.
  Their photo details always carry the date taken. Formats whose details can't be written, such as HEIC, are exported
  unchanged.
- Graphs use compact dropdowns rather than FitNotes's 1m / 3m / 6m / 1y / all buttons ([#125](https://github.com/Flameingskull/FlexNotes/issues/125)).
- Android doesn't let one app read another's private data, and FitNotes has no interface for other apps. So FlexNotes
  can't pull data out of FitNotes or make it create a backup. Folder sync is the closest Android allows. If you edit or
  delete an already imported set in FitNotes, the next import adds the changed version as a new set.
- Background backups follow Android's battery rules, so a scheduled backup can run a few hours late.
- Known issues in the current release are listed in its release notes. Today they are small ones in the "Which way is
  good?" colours ([#371](https://github.com/Flameingskull/FlexNotes/issues/371),
  [#372](https://github.com/Flameingskull/FlexNotes/issues/372)).

---

## Download and install

1. On your Android phone (Android 10 or newer), open the
   [latest release](https://github.com/Flameingskull/FlexNotes/releases/latest) and download `FlexNotes-1.0.N.apk`.
   No GitHub account is needed.
2. Open the downloaded file. Android will ask you to allow installs from your browser or file manager. Allow it once.
3. New releases install **over** the old one and keep all your data.

Each release includes the APK, the full source code, SHA-256 checksums and notes on what changed. The version number
goes up with every build (`1.0.<build>`). A number can be skipped, because a failed build publishes nothing but still
uses its number. A higher number is always the newer build.

## First-time setup

The first time FlexNotes opens, a short guided setup walks through the steps below. Every step can be skipped, and
**Settings › Show Setup Again** brings it back.

1. **Just want to start logging?** FlexNotes opens on today's log. Tap **+**, pick a category and an exercise, and
   record your sets. You don't need FitNotes at all.
2. **Bring over FitNotes history (optional):** **Settings › Import From FitNotes › Import a backup file**, then choose
   your latest `FitNotes_Backup_….fitnotes`. To keep using FitNotes for a while, pick its backup folder too.
3. **Add photos:** **⋮ › Photos › + › Import a whole folder**, or choose photos. FlexNotes dates each photo and places it
   on the right day. If any had no camera date, a banner says "N photos need their date checked". Tap it to accept or
   fix the dates.
4. **Tag poses** (Front, Side, Back) while importing, in the viewer, or by multi-select. Or choose a pose for all new
   photos in **Settings › Progress Photos & Media**.
5. **Settings › Backups:** choose a backup folder and turn on automatic backups.

---

## Report a bug or request a feature

Use the [Issues](https://github.com/Flameingskull/FlexNotes/issues/new/choose) tab and choose **Bug report** or
**Feature request**. You need a free GitHub account. Every report is triaged, and the planned work is visible in the
open issues. Please don't attach FitNotes backups or photos you don't want to be public.

## Contribute code

See [CONTRIBUTING.md](CONTRIBUTING.md). In short: fork the repository, make your change and open a pull request.
GitHub builds a test APK for every pull request.

## Build it yourself

FlexNotes is a native Android app in **Kotlin** and **Jetpack Compose** (Material 3), with a local SQLite database
managed by **Room**. The schema is recorded in `app/schemas/`, and every query is checked against it at build time. It
targets Android 16 (API 36), compiles against API 37 and runs on Android 10 (API 29) or newer. It builds with Gradle 9
and Android Gradle Plugin 9, which compiles the Kotlin itself. `docs/CODEMAP.md` says which file owns what.

Open the project in **Android Studio** and click **Run**, or run `./gradlew assembleDebug`. Your own builds are signed
with your debug key, so Android won't install them over the official release. Test on an emulator or a spare phone, or
save a backup first (**Settings › Backups › Save backup**) and uninstall the official app.

**Tests** run on the JVM with Robolectric, no emulator needed: `./gradlew testDebugUnitTest`. They cover:

- database upgrades from older versions (every row kept, ending in exactly the recorded schema);
- the FitNotes import merge rules, and saving and restoring `.flexnotes` backups;
- calculations such as the 1RM formulas, unit conversions and the body-measurement colour rule;
- the screens, drawn at 320dp and 411dp wide and at 200% font and compared with saved images, with checks for touch
  size and cut-off text;
- the source itself: every Material control must go through the FlexNotes wrappers, and the data, PDF and video code
  must not use the screens. A script also fails the build on private code that nothing uses.

Every push to `main` runs the tests, then GitHub Actions builds, signs and publishes a release. A failing test stops
the release. Pull requests run the tests and get a debug build only.

## Maintenance

**Dependabot** (`.github/dependabot.yml`) keeps dependencies (weekly) and Actions (monthly) current. Kotlin, the Compose
compiler plugin and KSP arrive as one grouped pull request because their versions are locked together. Dependabot pull
requests run a debug build with no secrets, so nothing is published until a merge.

**Every year, before 31 August**, `compileSdk` and `targetSdk` are raised to the API level Google Play then requires,
with AGP and Gradle if needed, and that Android version's behaviour changes are reviewed.

Updates never change `applicationId` (`com.fitlens.companion`), the signing setup, the signing secrets or
`BUILD_OFFSET`, so every build installs over the previous one and keeps its data. No secret is ever committed.

## Licence

[MIT](LICENSE) © 2026 Flameingskull

The bundled Manrope typeface is © 2018 The Manrope Project Authors, used under the
[SIL Open Font License 1.1](LICENSES/Manrope-OFL.txt).
