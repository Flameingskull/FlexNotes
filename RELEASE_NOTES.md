# FlexNotes 1.0.184

## Overview

This build changes the slideshow's progress stamp to plain words. It also clears out code left behind by earlier versions. Apart from the slideshow, the app looks and works as before. It's a little leaner underneath.

## Improved

- **Slideshow: time since your first photo.** The video's old "Day 12 · Week 2" counter is gone. Instead you can show how far you've come in plain words: "First photo", "12 days in", "5 weeks in", "4 months in". It's off unless you turn it on: in the slideshow options, under Overlay, choose "Time since the first photo". The date stamp is still there as before.
- **A cleaner app underneath.** Code, text and chart options left over from removed features and older screens have been taken out. Nothing you use has changed.

## Your data

- No database change. Your workouts, measurements, photos, medications and settings are kept, and older backups still restore.
- The tables and records older versions relied on are kept on purpose, so updating from any earlier version and restoring an old backup still work.

## Known limitations

- The slideshow's time stamp now starts off for everyone, including anyone who had the old counter turned on. Turn it on again in the slideshow options if you want it.
- The screen-by-screen reviews (issues #251 to #258) stay open until they've been checked on a phone at a large font size.
- Two clean-up checks are still to come: a build check that stops unused code from piling up again (#260), and listing what the new unused-resource check finds (#261).
