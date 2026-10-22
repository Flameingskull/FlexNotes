## Overview

This update finishes the photo export: your automatic backups can now also keep your progress photos as ordinary
image files. FlexNotes can also read your older Health Connect data, not only the last 30 days.

Install this update over the current app as usual. Everything you've logged is kept, and the database doesn't change.

## What's new

- **Copy photos as image files with your backups.** In Settings › Backups, turn on **Copy photos as image files**.
  Each automatic backup, and **Back up now**, then also saves your progress photos as ordinary image files in a
  Photos folder inside your backup folder. They have the same names and photo details as Photos › Export as image
  files: named by date and pose, such as 2026-10-16_front.jpg, with the FlexNotes date and time, pose and comment
  in each file. The setting is off until you turn it on.
- **Only new or changed photos are copied.** The first backup copies every photo. After that, each backup copies
  only photos you've added, or whose date, pose or comment you've changed. A changed photo replaces its own earlier
  copy when the file name still fits. If you move it to another date or pose, it gets a new file and the old one
  stays. A long first copy that's interrupted carries on at the next backup.
- **Older Health Connect data.** Health Connect lets apps read the last 30 days. In Settings › Health Connect,
  under Sync, **Older data** explains what it does, then Health Connect asks whether FlexNotes may read further
  back. If you allow it, FlexNotes reads the whole history of each kind of data you take from Health Connect once,
  in the background, and then keeps it up to date. If you don't, nothing changes.

## Improved

- The backup message says how many photos were copied, and how many couldn't be. A photo that couldn't be copied is
  tried again at the next backup, and a problem with the photos never stops the backup itself.
- If you take back the older-data permission in Health Connect and allow it again later, FlexNotes reads the history
  again.

## Known limitations

- Photos are copied only into the automatic backup folder. Files you delete there are copied again only if the photo
  changes in FlexNotes, and nothing in that folder is ever deleted, even when you delete a photo in FlexNotes.
- Some storage providers don't let apps set a file's modified date, so a copied file may show the time it was
  copied. Its photo details always carry the date it was taken. Photos in formats whose details can't be written,
  such as HEIC, are copied unchanged.
- Older Health Connect data is read back to 2010. Not every phone's Health Connect offers older data. Where it
  isn't offered, the last 30 days are still read.
- FlexNotes reads Health Connect but doesn't write to it yet (#191).
