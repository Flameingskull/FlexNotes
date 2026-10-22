## Overview

A small update for people who use FlexNotes with TalkBack, Android's screen reader. Number settings now read in a
clearer order, and every build now checks automatically what FlexNotes's shared controls tell TalkBack, so those
readings can't quietly break in a later update.

Install this update over the current app as usual. Everything you've logged is kept, and the database doesn't change.

## Improved

- **Number settings read as one sentence.** On a setting with − and + buttons, such as **Photos per day**, TalkBack
  now reads the setting's name and its explanation together, then the −, the value and the +. Before, the name and
  the explanation were two separate stops.

## Behind the scenes

- **TalkBack checks on every build.** Each build now fails if a shared control has nothing to say in words (only
  "−" or "+"), if two controls on the same screen say the same thing, or if a checkbox, radio button or tab doesn't
  announce whether it's on or chosen. The checks also confirm that section labels are headings TalkBack can jump
  between, that each settings row reads as one sentence, that a number's new value is announced, and that a set's
  **Mark done** action stays reachable.

## Known limitations

- These checks cover what FlexNotes hands to TalkBack, not how it sounds. Listening with TalkBack, the largest font
  size, a 320dp-wide phone and switch access on a real phone is still to do (#157).
