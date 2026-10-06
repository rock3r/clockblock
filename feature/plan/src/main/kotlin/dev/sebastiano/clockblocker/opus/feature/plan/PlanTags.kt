package dev.sebastiano.clockblocker.opus.feature.plan

/** Stable test tags for the plan screen (used by Robolectric tests and the e2e suite). */
object PlanTags {
    const val Screen = "plan_screen"
    const val Loading = "plan_loading"
    const val Empty = "plan_empty"
    const val NewTrip = "plan_new_trip"
    const val Header = "plan_header"
    const val Back = "plan_back"
    const val Overflow = "plan_overflow"
    const val MenuEdit = "plan_menu_edit"
    const val MenuReturn = "plan_menu_return"
    const val MenuExport = "plan_menu_export"
    const val MenuShare = "plan_menu_share"
    const val NightSafeChip = "plan_night_safe"
    const val Moon = "plan_moon"
    const val Dial = "plan_dial"
    const val NowCard = "plan_now_card"
    const val NowWhy = "plan_now_why"
    const val Done = "plan_done"
    const val MoreOutcomes = "plan_more_outcomes"
    const val Skipped = "plan_skipped"
    const val CantDo = "plan_cant_do"
    const val Snooze = "plan_snooze"
    const val UpNext = "plan_up_next"
    const val Adaptation = "plan_adaptation"
    const val Status = "plan_status"
    const val Rail = "plan_rail"
    const val NowMarker = "plan_now_marker"
    const val EarlierDays = "plan_earlier_days"
    const val WhySheet = "plan_why_sheet"
    const val Toolbar = "plan_toolbar"
    const val ToolbarNow = "plan_toolbar_now"
    const val ToolbarDay = "plan_toolbar_day"
    const val ToolbarWhy = "plan_toolbar_why"
    const val Celebration = "plan_celebration"
    const val CelebrationDismiss = "plan_celebration_dismiss"
    const val EmptyPane = "plan_empty_pane"
    const val DayStrip = "plan_day_strip"

    /** One advice block on the rail. */
    fun block(adviceId: String) = "plan_block_$adviceId"

    /** A day header on the rail ([index] = `PlanDay.index`, e.g. -1, 0, 2). */
    fun day(index: Int) = "plan_day_$index"

    /** The divider where the rail's times switch zone, before the day with [index]. */
    fun zoneSwitch(index: Int) = "plan_zone_switch_$index"

    /** A pill in the day strip above the dial ([index] = `PlanDay.index`). */
    fun dayPill(index: Int) = "plan_day_pill_$index"

    /** An entry in the toolbar's day picker. */
    fun dayPick(index: Int) = "plan_day_pick_$index"

    /** An "Up next" row. */
    fun upNext(adviceId: String) = "plan_up_next_$adviceId"
}
