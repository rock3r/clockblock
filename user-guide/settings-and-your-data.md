# Settings and your data

Open **Settings** from the navigation bar. Every answer from setup can be
changed here, and any change updates your plans straight away. This page also explains where your data lives and
how to back it up.

## Your profile and tools

<img src="images/settings-profile.png" alt="Settings, scrolled a little: Home time zone Los Angeles GMT−7, Usual sleep 23:00 to 07:00, 8 h, and Chronotype Somewhere in between. Below, Tools: Caffeine, I can sleep on planes and Adjust before I leave are on; Melatonin is off." width="300" />

| Setting | What it does |
|---|---|
| Home time zone | Where home is. Search for a city or airport. When a trip leaves from a city on a different time, the trip editor lets you start its plan from home time instead ([Starting away from home](planning-a-trip.md#starting-away-from-home)). |
| Usual sleep | Your normal bedtime and wake-up time, on the same dial as setup. See [Getting started](getting-started.md#3-when-do-you-usually-sleep). |
| Chronotype | Lark, owl or in between. |
| Tools | Caffeine, sleeping on planes, adjusting before you leave, and melatonin. See [Getting started](getting-started.md#5-your-tools). |
| How hard the plan pushes | Gentle, Balanced or Max. |

## Appearance and reminders

<img src="images/settings-reminders.png" alt="Settings: Theme with System, Light and Dark; Dynamic colour off; Reduce motion off; Night-safe automatically on. Below, Remind me before each change on, How early set to 15 min, Hide details on the lock screen off, and Send a test reminder." width="300" />

| Setting | What it does |
|---|---|
| Theme | System (follows your phone), Light or Dark. |
| Dynamic colour | Takes the app's colours from your wallpaper. The colours of the advice stay the same. |
| Reduce motion | Calmer transitions and still pictures, on top of your phone's own setting. |
| Night-safe automatically | Makes the plan screen dark and dim while your plan says avoid light or sleep, or during your body's night when no light is planned. The widgets turn dark while your plan says avoid light or sleep. |
| Remind me before each change | Turns all reminders and the Now notification on or off. Widgets keep working. |
| How early | When reminders arrive: on time, or 5, 10, 15 or 30 minutes before a block starts. |
| Hide details on the lock screen | Keeps places, flight numbers and supplement names off the lock screen whenever Android hides sensitive notification content, and always off widgets placed on the lock screen. Times and the kind of block stay. Off by default. See [On the lock screen](widgets-and-notifications.md#on-the-lock-screen). |
| Send a test reminder | Sends a sample reminder so you can check that reminders arrive. |
| What Android allows | A one-line summary of whether reminders can arrive on time. Tap it to see each permission: notifications, exact timing, Live Updates and battery optimisation, and tap **Allow** or **Open settings** to change one. It opens by itself when notifications or exact timing are missing. Live Updates and battery optimisation are optional extras. |
| Widgets | A preview of each home-screen widget with an **Add** button that places it on your home screen. Only shown if your home screen app supports it. |

On a tablet, a foldable or a large window, Settings shows two columns: your profile, tools, appearance and More
on the left, reminders, widgets and your data on the right.

## Your data

All your data stays on your phone: your profile, settings, trips and the blocks you marked as done or skipped.
The app has no internet access, so it can't send your data anywhere itself. Your data leaves the phone only when
you export it, when you share a plan summary to another app, when Android's own backup copies it to your backup
account (if you have that turned on in your phone's settings), or when you transfer it to a new phone (see
[Moving to a new phone](#moving-to-a-new-phone)).

### Back up and restore

**Export a backup** saves your profile, settings, trips and their check-ins as a JSON file. Check-ins of trips
you have deleted are not included. You choose where
to save it, for example your Downloads folder or a cloud drive app. The file is named like
`clockblock-backup-2026-06-01.json`.

**Import a backup** reads a file made by this app. Before anything changes, it tells you what's in the file and
asks how to import it:

<img src="images/import-confirm.png" alt="Import this backup? 3 trips, exported Jun 1, 2026. Replace makes this phone match the backup exactly: trips that aren't in it are deleted. Merge adds the backup's trips and keeps everything else. Buttons: Cancel, Replace (outlined in red), Merge (filled)." width="400" />

- **Replace** deletes the trips on this phone that aren't in the backup, then takes the backup's profile, settings,
  trips and check-ins. Each trip ends up with exactly the backup's check-ins: ones you made on this phone that
  aren't in the backup are removed. If the backup has no profile, this phone keeps its own.
- **Merge** only adds. It adds the backup's trips that aren't on this phone, and the backup's check-ins for
  blocks you haven't checked in here. It deletes and changes nothing: this phone keeps its settings, its
  profile, its trips (a trip in both places keeps this phone's version) and its check-ins. The backup's profile
  is used only if this phone doesn't have one yet. A check-in belongs to the plan it was made on, so Merge adds
  a trip's check-ins only when the trip and the profile on this phone end up the same as in the backup.

If the file isn't a valid backup, or it comes from a newer version of the app, nothing is changed and the app
tells you why.

### Moving to a new phone

Android's own backup and the phone-to-phone transfer you get when setting up a new phone bring your profile,
settings, trips and check-ins across, and the options of any widgets your home screen app puts back. Cloud backups are made only when your phone
has a screen lock, so the backup is end-to-end encrypted. A running snooze stays behind.

Open the app once on the new phone: that sets up its reminders again. Android doesn't carry over permissions, so
check **What Android allows** in Settings and allow notifications and exact timing again.

If you don't use Android backup, or want a copy you control, export a backup on the old phone, copy the file
across, and import it on the new one.

### Export a plan to your calendar

In a plan, open the three-dot menu and choose **Export to calendar**. The app saves a calendar file (`.ics`) with
every block of the plan, in the right time zones. Open it with your calendar app to add the events. In most
calendar apps, exporting again updates the events whose block kept its place in the plan. Blocks that were
removed may stay in your calendar, so delete the old events after a big change.

## More

- **Replay setup** walks you through the first-run questions again.
- **About Clockblock** explains how the plan works, lists the research it's based on, and shows the version
  and the open-source licences. Tap a licence to open the project's web page.

Next: [Questions and answers](faq.md).
