# FlexNotes 1.0.188

## Overview

This is a behind-the-scenes update. FlexNotes now builds on the current Android platform: Kotlin 2.4, Android
Gradle Plugin 9, the Compose compiler and Compose BOM 2026.09, and Room 2.8 with the newer AndroidX libraries. The
app looks and works exactly as before.

The Dependabot pull requests that were meant to keep these current (#160, #196 and #197) each failed on their own,
because the update groups depend on each other, so this build lands them together.

## Improved

- The app now builds on Kotlin 2.4 and Android Gradle Plugin 9, with the Compose compiler, the 2026.09 Compose BOM and
  Room 2.8.

## Your data

- No database change. Your workouts, measurements, photos, medications and settings are kept, and older backups still
  restore.

## Known limitations

- The screenshot goldens are compared under Roborazzi 1.76, which changed how screenshots are captured, so the first
  build on the new toolchain may need a golden re-record.
