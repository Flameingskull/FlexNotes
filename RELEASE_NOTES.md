# FlexNotes 1.0.202

## Overview

This update makes body value colours agree everywhere. The same value can no longer be green on one screen and red on another, a move that stays as far from your target is no longer called worse, and Photo Compare always reads the change from the earlier photo to the later one.

## What's new

- **Choose what a change is compared with:** a new setting, **Compare a body value with**, decides where a body value's change is measured from: **the last value on an earlier day** (the default) or **the value just before it**. Find it in Settings (search "previous" or "compare"), or on a measurement's "Which way is good?" sheet.

## Fixed

- **One meaning of "previous" everywhere:** the Track tab, History and the day log now measure a body value's change from the same earlier value, so the same value gets the same colour on every screen. If you log more than one value in a day, the Track row and History now compare with the last value on an earlier day by default, as the day log always did. Medication doses still compare with the dose just before.
- **Equal distance from a target:** with "A specific value" as the goal, a move that ends exactly as far from your target as it started is no longer shown in red. It gets no colour.
- **Photo Compare reads the right way:** the change and its colour now run from the earlier photo to the later one, whichever side each photo is on, and the screen says so. The image you share or save from Compare puts the change beside the later photo's value.
- **The "Best" star on a target graph:** a graph whose target is exactly half way between two points (for example a waist-to-height ratio of 0.5) shows its "Best" star again.

## Improved

- **Steadier colours:** each screen now reads the "Colour changes with no goal" choice once, so every row on it always agrees.
- **More reliable releases:** the automated checks that run before each update now wait for any save still in progress before they finish, so a slow save can't make an update fail at random.

## Known limitations

- The progress PDF still measures each body value's change from the last value on an earlier day, whatever you choose in **Compare a body value with** (#377).
- Inside the app's code, a graph's good/bad rule and its key still aren't forced to travel together, so a future change could separate them. Nothing does today (#370).
- Some automated screen checks don't yet wait for saves in progress (#378).
