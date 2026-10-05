# Getting started

The first time you open Opus Clockblock, it asks six short questions. Your answers set where your body clock
starts and how hard the plan pushes. It takes about two minutes, and you can change every answer later in
Settings.

## What you need

- An Android phone or tablet with Android 10 or later.
- No account and no internet connection. The app works the same in flight mode.

Opus Clockblock isn't in an app store yet. To install it, build it from the source code (see the
[project README](../README.md#build-and-test)).

## The six setup steps

From step 2 on, the top shows "Step N of 6". Use **Back** to go to the previous step. **Skip** (not on the last
step) keeps your answers so far and the defaults for the rest, and jumps to the last step.

### 1. Welcome

<img src="images/welcome.png" alt="Welcome screen: Jet lag is two clocks disagreeing. Badges say Works offline, Free and open source, No account. A Get started button is at the bottom." width="280" />

The first screen explains the idea: jet lag is two clocks disagreeing, the one on the wall and the one in your
body. Tap **Get started**.

### 2. Where's home?

<img src="images/home-zone.png" alt="Step 2 of 6, Where's home: the current home time zone is Rome, marked Phone's time zone. The search field contains lis and shows Lisbon Humberto Delgado as a result." width="280" />

The app picks your phone's time zone and marks it "Phone's time zone". If home is somewhere else, search for a
city or a three-letter airport code and pick the right place. You can change it later in Settings.

Plans don't use this setting yet: each plan assumes your body clock starts in the time zone of the trip's first
departure airport. If you're already away from home, enter the trip from where your body clock is now.

### 3. When do you usually sleep?

<img src="images/sleep.png" alt="Step 3 of 6, When do you usually sleep: a round dial with a moon at bedtime 23:00 and a sun at wake 07:00, 8 hours of sleep in the middle." width="280" />

Drag the moon to your usual bedtime and the sun to your usual wake-up time. Use your normal nights, not your best
ones. The app uses this to estimate where your body clock is today.

### 4. Lark, owl, or in between?

<img src="images/chronotype.png" alt="Step 4 of 6, Lark, owl, or in between: five options from Early bird to Night owl, with Morning person selected, and a link Not sure? Work it out from your free days." width="280" />

Choose the option that matches you on a free day with nothing planned: Early bird, Morning person, Somewhere in
between, Evening person or Night owl. If you're not sure, tap **Not sure? Work it out from your free days**.
The app asks when you fall asleep and wake up with no alarm, and suggests an answer.

### 5. Your tools

<img src="images/tools.png" alt="Step 5 of 6, Your tools: switches for Caffeine, I can sleep on planes and Start adjusting before I leave are on; Melatonin is off. Below, How hard should the plan push? with Gentle, Balanced (selected) and Max." width="280" />

Pick what you're happy to use:

| Switch | What it changes |
|---|---|
| Caffeine | Suggests little-and-often coffee to stay sharp, and a cut-off before sleep. If you don't drink coffee, turn it off. |
| I can sleep on planes | When off, sleep on the plane becomes "rest in the dark" instead. |
| Start adjusting before I leave | Shifts your clock a little on the days before departure, so you land partly adapted. |
| Melatonin | Off by default. See below. |

Then choose how hard the plan should push:

- **Gentle**: smaller daily shifts and fewer early starts. It takes a little longer.
- **Balanced**: the pace the research supports. A good default for most trips.
- **Max**: the Balanced pace, plus a smarter choice of direction. For eastward shifts of 8 to 12 hours, it
  simulates both shifting earlier and shifting later. If one adapts you more than a day faster, it picks that one.
  Otherwise it keeps the usual direction.

#### Melatonin

<img src="images/melatonin-note.png" alt="The melatonin safety note, Before you use melatonin, listing the 0.5 mg dose, who should check with a doctor first, not driving afterwards, and different rules by country. A checkbox says I've read this and I'll check what applies to me." width="280" />

Melatonin suggestions stay off until you read the safety note and tick **I've read this and I'll check what
applies to me**. The plan only suggests a small dose (0.5 mg of fast-release melatonin), and only to shift your
clock earlier. Melatonin is a medicine in many countries. Check with a doctor or pharmacist first.

### 6. Reminders that actually arrive

<img src="images/reminders.png" alt="Step 6 of 6, Reminders that actually arrive: a Notifications row with an Allow button and an Exact timing row with an Open settings button. Buttons at the bottom: Maybe later and Allow and finish." width="280" />

The app can remind you just before each change in your plan. It never sends a reminder while you're meant to be
asleep, except the one that wakes you.

- **Notifications** lets the app show reminders at all.
- **Exact timing** lets reminders arrive on the minute. Without it, Android may deliver them several minutes
  late, and much later while the phone is idle (Doze).

Tap **Allow and finish** (or **Finish** if notifications are already allowed), or **Maybe later**. You can grant
the permissions any time from Settings → **What Android allows**.

## What's next

After setup, the app opens the Trips screen. [Plan your first trip](planning-a-trip.md), or tap **Try a demo
trip** to explore with an example.
