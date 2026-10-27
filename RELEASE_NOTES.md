# FlexNotes 1.0.185

## Overview

This is a behind-the-scenes build. It adds two automatic checks to how FlexNotes is built, so the app stays lean and leftovers from older versions don't pile up again. The app looks and works exactly as before.

## Improved

- **Unused code can't creep back in.** Every build now checks for code that nothing uses and stops if it finds any, so it's removed before a release goes out.
- **Unused resources are listed.** The build now shows which text, images and other resources nothing uses, so they can be cleared out in a later build.

## Your data

- No database change. Your workouts, measurements, photos, medications and settings are kept, and older backups still restore.

## Known limitations

- Any unused resources this build finds are listed for the next build, not removed in this one (#261).
- The screen-by-screen reviews (issues #251 to #258) stay open until they've been checked on a phone at a large font size.
