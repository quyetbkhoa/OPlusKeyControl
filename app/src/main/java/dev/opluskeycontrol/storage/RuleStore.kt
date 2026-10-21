package dev.opluskeycontrol.storage

import android.content.Context
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

private val Context.dataStore by preferencesDataStore("rules")
data class Configuration(val rules: List<Rule> = emptyList(), val enabled: Boolean = false, val tier: Tier = Tier.BASIC, val timing: Timing = Timing())

object RuleCodec {
    fun encode(c: Configuration): String = JSONObject().apply {
        put("version", 1); put("enabled", c.enabled); put("tier", c.tier.name)
        put("timing", JSONObject().put("double", c.timing.doubleMs).put("long", c.timing.longMs).put("chord", c.timing.chordMs).put("debounce", c.timing.debounceMs))
        put("rules", JSONArray().apply { c.rules.forEach { r -> put(JSONObject().apply {
            put("id", r.id); put("button", r.trigger.button.name); put("gesture", r.trigger.gesture.name)
            put("partner", r.trigger.partner?.name); put("kind", r.action.kind.name)
            put("package", r.action.packageName); put("activity", r.action.activity); put("duration", r.action.durationSeconds)
            put("priority", r.priority); put("enabled", r.enabled)
            r.schedule?.let { s -> put("schedule", JSONObject().put("start", s.startMinute).put("end", s.endMinute).put("days", JSONArray(s.days.map { it.value }))) }
        }) } })
    }.toString(2)
    fun decode(text: String): Configuration {
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
        return Configuration(rules, obj.getBoolean("enabled"), Tier.valueOf(obj.getString("tier")), Timing(t.getLong("double"), t.getLong("long"), t.getLong("chord"), t.getLong("debounce")))
    }
}
class RuleStore(private val context: Context, private val scope: CoroutineScope) {
    private val key = stringPreferencesKey("configuration_v1")
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
