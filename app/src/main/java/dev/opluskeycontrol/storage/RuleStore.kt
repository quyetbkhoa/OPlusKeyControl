package dev.opluskeycontrol.storage

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.opluskeycontrol.domain.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.ZonedDateTime

private val configurationKey = stringPreferencesKey("configuration_v1")
private val Context.dataStore by preferencesDataStore("rules", produceMigrations = {
    listOf(object : DataMigration<Preferences> {
        override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData[configurationKey]?.let { text ->
            runCatching {
                val rules = JSONObject(text).getJSONArray("rules")
                (0 until rules.length()).any {
                    val r = rules.getJSONObject(it)
                    !r.isNull("schedule") || (r.optString("gesture") == "CHORD" &&
                        setOf(r.optString("button"), r.optString("partner")).let { keys ->
                            "POWER" in keys && "PLUS" !in keys
                        })
                }
            }.getOrDefault(false)
        } ?: false
        override suspend fun migrate(currentData: Preferences): Preferences = currentData.toMutablePreferences().apply {
            this[configurationKey] = RuleCodec.encode(RuleCodec.decode(requireNotNull(currentData[configurationKey])))
        }
        override suspend fun cleanUp() = Unit
    })
})
data class Configuration(val rules: List<Rule> = emptyList(), val enabled: Boolean = false, val tier: Tier = Tier.BASIC, val timing: Timing = Timing(), val hapticFeedback: Boolean = true)

object RuleCodec {
    fun encode(c: Configuration): String = JSONObject().apply {
        require(c.rules.none { RuleResolver.conflicts(it, c.rules) })
        put("version", 1); put("enabled", c.enabled); put("tier", c.tier.name)
        put("hapticFeedback", c.hapticFeedback)
        put("timing", JSONObject().put("double", c.timing.doubleMs).put("long", c.timing.longMs).put("chord", c.timing.chordMs).put("debounce", c.timing.debounceMs))
        put("rules", JSONArray().apply { withoutSchedules(c.rules.filterNot { it.trigger.isPowerVolumeChord() }, ZonedDateTime.now()).forEach { r -> put(JSONObject().apply {
            put("id", r.id); put("button", r.trigger.button.name); put("gesture", r.trigger.gesture.name)
            put("partner", r.trigger.partner?.name); put("kind", r.action.kind.name)
            put("package", r.action.packageName); put("activity", r.action.activity); put("duration", r.action.durationSeconds)
            put("priority", r.priority); put("enabled", r.enabled)
        }) } })
    }.toString(2)
    fun decode(text: String, now: ZonedDateTime = ZonedDateTime.now()): Configuration {
        require(text.length <= 1_000_000)
        val obj = JSONObject(text)
        require(obj.getInt("version") == 1)
        val list = obj.getJSONArray("rules")
        require(list.length() <= 500)
        val rules = (0 until list.length()).map { i ->
            val r = list.getJSONObject(i)
            val s = r.optJSONObject("schedule")?.let { v ->
                val days = v.getJSONArray("days")
                Schedule(v.getInt("start"), v.getInt("end"), (0 until days.length()).map { DayOfWeek.of(days.getInt(it)) }.toSet())
            }
            fun optional(key: String) = if (r.isNull(key)) null else r.getString(key)
            Rule(r.getString("id"), Trigger(Button.valueOf(r.getString("button")), Gesture.valueOf(r.getString("gesture")), optional("partner")?.let(Button::valueOf)),
                Action(ActionKind.valueOf(r.getString("kind")), optional("package"), optional("activity"), r.getInt("duration")), s, r.getInt("priority"), r.getBoolean("enabled"))
        }
        require(rules.map { it.id }.distinct().size == rules.size)
        require(rules.none { RuleResolver.conflicts(it, rules) })
        val t = obj.getJSONObject("timing")
        return Configuration(withoutSchedules(rules.filterNot { it.trigger.isPowerVolumeChord() }, now), obj.getBoolean("enabled"), Tier.valueOf(obj.getString("tier")), Timing(t.getLong("double"), t.getLong("long"), t.getLong("chord"), t.getLong("debounce")), obj.optBoolean("hapticFeedback", true))
    }

    // Legacy backups may contain several time windows for the same trigger.
    // Keep the current winner enabled and retain other actions as disabled mappings.
    // Never drop the entire configuration or create ambiguous always-on mappings.
    private fun withoutSchedules(rules: List<Rule>, now: ZonedDateTime): List<Rule> = rules.map { rule ->
        val peers = rules.filter { it.enabled && it.trigger.sameAs(rule.trigger) }
        val winner = if (peers.any { it.schedule != null })
            (RuleResolver.resolve(peers, rule.trigger, now) as? Resolution.Match)?.rule ?: peers.maxByOrNull { it.priority }
        else null
        rule.copy(schedule = null, enabled = rule.enabled && (winner == null || winner.id == rule.id))
    }
}
class RuleStore(private val context: Context, private val scope: CoroutineScope) {
    private val key = configurationKey
    val error = MutableStateFlow(false)
    // Synchronous safety latch: emergency disable takes effect before disk IO completes.
    val emergency = MutableStateFlow(false)
    val config = context.dataStore.data.map { prefs ->
        runCatching { prefs[key]?.let(RuleCodec::decode) ?: Configuration() }.getOrElse { error.value = true; Configuration() }
    }.catch { error.value = true; emit(Configuration()) }.stateIn(scope, SharingStarted.Eagerly, Configuration())
    fun mutate(transform: (Configuration) -> Configuration) = scope.launch {
        runCatching { context.dataStore.edit { prefs ->
            val current = prefs[key]?.let(RuleCodec::decode) ?: Configuration()
            val updated = transform(current)
            require(updated.rules.none { RuleResolver.conflicts(it, updated.rules) })
            prefs[key] = RuleCodec.encode(updated)
        } }.onFailure { error.value = true }
    }
    fun disable() { emergency.value = true; mutate { it.copy(enabled = false) } }
    fun setEnabled(enabled: Boolean) { if (enabled) emergency.value = false else emergency.value = true; mutate { it.copy(enabled = enabled) } }
    fun restore(text: String) { val safe = RuleCodec.decode(text).copy(enabled = false); emergency.value = true; mutate { safe } }
}
