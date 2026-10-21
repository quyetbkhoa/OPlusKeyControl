package dev.opluskeycontrol.domain

import java.time.DayOfWeek
import java.time.ZonedDateTime

enum class Button { PLUS, VOLUME_UP, VOLUME_DOWN, POWER }
enum class Gesture { SINGLE, DOUBLE, LONG, CHORD }
enum class Tier { BASIC, SHIZUKU, ROOT }
enum class ActionKind { TORCH_TOGGLE, TORCH_ON, TORCH_OFF, APP, ACTIVITY, STOCK_CAMERA_CAPTURE, RECORD_TOGGLE }

data class Trigger(val button: Button, val gesture: Gesture, val partner: Button? = null) {
    init {
        require((gesture == Gesture.CHORD) == (partner != null))
        require(partner != button)
        require(button != Button.POWER || gesture in setOf(Gesture.DOUBLE, Gesture.CHORD))
        // OEM screenshot, volume/emergency and unverified Power chords are reserved.
        require(gesture != Gesture.CHORD || Button.POWER !in setOf(button, partner))
        require(gesture != Gesture.CHORD || setOf(button, partner) != setOf(Button.VOLUME_UP, Button.VOLUME_DOWN))
    }
    fun sameAs(other: Trigger) = if (gesture == Gesture.CHORD && other.gesture == Gesture.CHORD)
        setOf(button, partner) == setOf(other.button, other.partner) else this == other
}

/** Days refer to the day on which the window STARTS. start==end is invalid. */
data class Schedule(val startMinute: Int, val endMinute: Int, val days: Set<DayOfWeek> = DayOfWeek.entries.toSet()) {
    init {
        require(startMinute in 0..1439 && endMinute in 0..1439 && startMinute != endMinute)
        require(days.isNotEmpty())
    }
    fun matches(now: ZonedDateTime): Boolean {
        val minute = now.hour * 60 + now.minute
        return if (startMinute < endMinute) now.dayOfWeek in days && minute in startMinute until endMinute
        else (minute >= startMinute && now.dayOfWeek in days) ||
            (minute < endMinute && now.minusDays(1).dayOfWeek in days)
    }
    private fun weekMinutes(): Set<Int> = buildSet {
        for (day in days) {
            val base = (day.value - 1) * 1440
            val length = (endMinute - startMinute + 1440) % 1440
            repeat(length) { add((base + startMinute + it) % 10080) }
        }
    }
    fun overlaps(other: Schedule): Boolean {
        val otherMinutes = other.weekMinutes()
        return weekMinutes().any { it in otherMinutes }
    }
}

data class Action(val kind: ActionKind, val packageName: String? = null, val activity: String? = null, val durationSeconds: Int = 300) {
    init {
        require(durationSeconds in 10..7200)
        if (kind in setOf(ActionKind.APP, ActionKind.ACTIVITY)) require(!packageName.isNullOrBlank())
        if (kind == ActionKind.ACTIVITY) require(!activity.isNullOrBlank())
        require(packageName == null || packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")))
        require(activity == null || activity.matches(Regex("[A-Za-z_][A-Za-z0-9_.$]*")))
    }
}
data class Rule(val id: String, val trigger: Trigger, val action: Action, val schedule: Schedule? = null, val priority: Int = 0, val enabled: Boolean = true) {
    init { require(id.isNotBlank() && id.length <= 64); require(priority in 0..100) }
}
sealed interface Resolution {
    data class Match(val rule: Rule) : Resolution
    data object PassThrough : Resolution
    data class Conflict(val rules: List<Rule>) : Resolution
}
object RuleResolver {
    fun resolve(rules: List<Rule>, trigger: Trigger, now: ZonedDateTime): Resolution {
        val candidates = rules.filter { it.enabled && it.trigger.sameAs(trigger) && (it.schedule?.matches(now) != false) }
        if (candidates.isEmpty()) return Resolution.PassThrough
        val specific = candidates.filter { it.schedule != null }.ifEmpty { candidates }
        val priority = specific.maxOf { it.priority }
        val winners = specific.filter { it.priority == priority }
        return if (winners.size == 1) Resolution.Match(winners.single()) else Resolution.Conflict(winners)
    }
    fun conflicts(candidate: Rule, rules: List<Rule>): Boolean = candidate.enabled && rules.any {
        it.id != candidate.id && it.enabled && it.trigger.sameAs(candidate.trigger) && it.priority == candidate.priority &&
            when { it.schedule == null && candidate.schedule == null -> true
                it.schedule != null && candidate.schedule != null -> it.schedule.overlaps(candidate.schedule)
                else -> false }
    }
}
