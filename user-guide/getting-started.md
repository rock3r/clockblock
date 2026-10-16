# Getting started

Clockblock has five setup steps. Your answers set your starting body clock estimate and the plan's adjustment rate.
Setup takes about two minutes. You can change these answers in Settings.

## What you need

- Use a phone or tablet with Android 17 or later.
- Clockblock works in flight mode without an account or internet connection.

Clockblock isn't in an app store yet. To install it, build it from the source code (see the
[project README](../README.md#build-and-test)).

## The five setup steps

From step 2, the top shows "Step N of 5". Tap **Back** to return to the previous step.
Before the last step, **Skip** takes you to the final step. It keeps your answers and uses defaults for unanswered questions.

### 1. Welcome

<img src="images/welcome.png" alt="Welcome screen: Jet lag is two clocks disagreeing. Badges say Works offline, Free and open source, No account. A Get started button is at the bottom." width="280" />

Jet lag happens when your body clock has not adjusted to local time. Tap **Get started**.

### 2. Where's home?

<img src="images/home-zone.png" alt="Step 2 of 5, Where's home: the current home time zone is Rome, marked Phone's time zone. The search field contains lis and lists Lisbon Humberto Delgado first, then Lismore, Budapest Liszt Ferenc and more, each with its local time and GMT offset." width="280" />

Clockblock selects your phone's time zone and labels it "Phone's time zone".
If you live elsewhere, search by city or three-letter airport code. Select your home location.
Settings lets you change this choice later.

By default, plans start your body clock in the first departure airport's time zone.
If that city's time differs from home, the trip editor offers both starting zones.
See [Starting away from home](planning-a-trip.md#starting-away-from-home).

### 3. When do you usually sleep?

<img src="images/sleep.png" alt="Step 3 of 5, When do you usually sleep: a round dial with a moon at 23:00 and a sun at 07:00 on the inner ring, and a thinner, paler ring outside it for days off, from about half past midnight to half past nine. In the middle, 8h of sleep. Beside the dial, Work days and Days off buttons, one above the other, with Work days selected. Below the dial, two buttons: Bedtime 23:00 and Wake 07:00." width="280" />

Drag the moon to your usual bedtime and the sun to your usual wake-up time. The dial holds two schedules:

- **Work days**: your usual nights before work or other early commitments. Plans start from these.
- **Days off**: the nights you wake up without an alarm.

Tap **Work days**, **Days off**, or the thin outer ring to switch schedules.
The outer ring shows the schedule you are not editing.
On narrow screens or with large text, the schedule buttons move above the dial.
On landscape phones, they sit beside the dial with the Bedtime and Wake buttons.

Days off initially copies Work days and shows "Same as work days for now".
Move a Days off handle to set a separate schedule. Keep the copy if you sleep at the same times every day.

The middle of the dial shows how long you sleep, for example 8h 30m. A very short or very long night gets a note
under the Bedtime and Wake buttons.

The **Bedtime** and **Wake** buttons show the selected schedule's times and update during a drag.
Tap either button to open a time picker. Tap its keyboard button to type a time.
If your choice leaves less than one hour of sleep, Clockblock moves the other time to keep a one-hour minimum.

#### Your chronotype

<img src="images/chronotype.png" alt="Still step 3 of 5: a sky over layered hills with the sun high on its path between two clouds, Your chronotype, Somewhere in between, Neither a lark nor an owl. Most people are here. Below: Your usual nights are centred on 03:00. Set your days off on the previous step for a closer estimate, and a Doesn't sound like me link." width="280" />

Tap **Next** to estimate your chronotype, which describes your usual sleep timing.
Clockblock shows one of five categories, from earliest to latest.
It uses the midpoint of days-off sleep and corrects for extra sleep after work days.
This follows the Munich ChronoType Questionnaire method.
If both schedules match, Clockblock reports that it used your usual nights.
The screen suggests setting days-off times for a closer estimate.

On the first visit, the card expands from the dial and its illustration appears in stages.
Tap anywhere to finish the animation immediately.
Clockblock skips the animation with Reduce motion, Remove animations or TalkBack enabled.

To change the result, tap **Doesn't sound like me** and choose a category.
Clockblock marks the category calculated from your sleep times.
Your manual choice shows **Your pick** and stays selected when you change your sleep times.

### 4. Your tools

<img src="images/tools.png" alt="Step 4 of 5, Your tools: switches for I drink caffeine, I can sleep on planes and Naps are OK are on, each row tinted in its advice colour with its icon inside its shape; Head start has None, 1 day, 2 days and 3 days (selected); I use melatonin is off. Below, How hard should the plan push? with Gentle, Balanced (selected) and Max." width="280" />

Choose which tools your plan can use. Each row shows the corresponding advice shape and icon.
An enabled row uses that advice's colour.

| Setting | What it changes |
|---|---|
| I drink caffeine | Suggests little-and-often coffee to stay sharp, and a cut-off before sleep. If you don't drink coffee, turn it off. |
| I can sleep on planes | When off, sleep on the plane becomes "rest in the dark" instead. |
| Naps are OK | Adds optional short naps ("Nap if you're tired") on days you might be tired. Turn it off if you'd rather not nap. One nap stays even then: after a day that keeps you awake at least an hour longer than usual, the plan still suggests it, because that one matters. |
| Head start | Shifts your clock a little on the days before departure (**None**, **1 day**, **2 days** or **3 days**), so you land partly adapted. |
| I use melatonin | Off by default. See below. |

Choose the adjustment rate:

- **Gentle** uses smaller daily shifts and fewer early starts. Adjustment takes longer.
- **Balanced** uses the default research-based shift rates.
- **Max** uses Balanced rates and compares both directions for eastward shifts of 8 to 12 hours.
  It selects a direction if the model predicts an advantage greater than one day.
  Otherwise, it keeps the usual direction.

#### Melatonin

<img src="images/melatonin-note.png" alt="The melatonin safety note, Before you use melatonin, listing the 0.5 mg dose, who should check with a doctor first, not driving afterwards, and different rules by country. A checkbox says I've read this and I'll check what applies to me." width="280" />

Melatonin stays off until you read the safety note and tick **I've read this and I'll check what applies to me**.
The plan suggests 0.5 mg of fast-release melatonin only when shifting your clock earlier.
Many countries regulate melatonin as a medicine. Consult a doctor or pharmacist before taking it.

### 5. Reminders that actually arrive

<img src="images/reminders.png" alt="Step 5 of 5, Reminders that actually arrive: a sample reminder as it looks in the notification shade, See bright light until 18:00 (10:00 in Lisbon) with a progress bar and the time left counting down, Next: Caffeine OK at 15:30, and the Done, Can't do this and Snooze 15 min actions. Under it, an Allow notifications button. At the bottom, Back and Maybe later." width="280" />

Clockblock can send reminders before each plan change.
During planned sleep, it sends only the wake-up reminder.

The card shows a sample of the reminder that stays in your notification shade while a plan runs. Tap **Allow
notifications** under it to let the app show reminders. Android asks you to confirm.

After you allow notifications, the bottom button becomes **Finish**.
If Clockblock still needs exact-alarm permission, an **Exact timing** row appears.
This permission lets reminders arrive on the minute. Tap **Open settings** to allow it, or skip the request.
Without permission, reminders may arrive up to 10 minutes late.
During Android's idle mode, Doze, delays usually reach about an hour. Android gives no maximum delay guarantee.

If you installed the app from outside Google Play, exact timing is allowed when you install it, so the row never
appears.

Tap **Maybe later** to finish setup without a permission request.
If you deny Android's request, the card offers **Open settings** and the bottom button becomes **Finish**.
You can grant permissions later through Settings → **What Android allows**.

## What's next

After setup, the app opens the Trips screen. [Plan your first trip](planning-a-trip.md), or tap **Try a demo
trip** to explore with an example.
