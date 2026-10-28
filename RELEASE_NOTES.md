# FlexNotes 1.0.191

## Overview

This update fixes five problems found in a full review of FlexNotes's code, and moves the app onto the current
Android toolchain and libraries. Your workouts and photos are handled more safely: records are marked correctly when
you log an earlier day, imports don't add values twice, typed values stay put, and saves tell you when they fail.

## Fixed

- Logging a heavier set on an earlier date now moves the PR mark correctly. Later sets it beats no longer show as
  records.
- Importing a FitNotes Body Tracker CSV in a different unit (pounds over kilograms, say) now converts each value to
  your measurement's unit, so values you already have aren't added twice.
- A weight or reps you've typed on the exercise screen now stays put when you tick a set done or add a comment.
  Before, it was reset before you could save it.
- Saving a progress video or a comparison image now tells you when the gallery couldn't save it, instead of saying it
  was saved. A failed or stopped video no longer leaves a half-made file behind.
- Comparing two photos now colours each body change by your goal for that measurement, so a smaller waist shows green
  rather than red.

## Improved

- Analysis keeps your category or exercise filter when you open a workout day and come back.
- The slideshow keeps its options when you turn the phone.
- The day log's volume now counts sets the same way Analysis does. A set with an assisted (negative) weight no longer
  lowers it.
- Secondary buttons keep their gold text. The new Android libraries had turned it grey.
- Deleting photos is now all or nothing: a delete that stops part-way never leaves a photo listed without its image.
- Progress videos and dark PDF reports take their colours straight from the app's palette. Categories without a
  colour no longer get a stray dot in the PDF, and poses print by name.
- FlexNotes now builds with Gradle 9.8, the Android Gradle Plugin 9 (which compiles the Kotlin itself), Kotlin 2.4,
  the 2026.09 Compose libraries, Room 2.8 and the current AndroidX libraries.
- Behind the scenes: more database upgrade tests, stricter build checks (unused code and the no-internet rule can no
  longer be skipped silently), and screenshot checks for settings rows that were never compared before.

## Your data

- No database change. Your workouts, measurements, photos, medications and settings are kept, and older backups
  still restore.

## Known limitations

- Turning the phone still closes the workout, schedule and exercise editors' unsaved changes and some sheets on the
  exercise screen. That's next (#304).
- Photo tiles, the photo viewer and the medication report still show some dates without the weekday (#308).
- Some controls (chips, switches, sliders) still use Material's default colours. They're being pinned to the brand
  look with screenshot checks (#306).
