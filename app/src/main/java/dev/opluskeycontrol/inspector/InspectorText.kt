package dev.opluskeycontrol.inspector

internal object InspectorText {
    fun field(value: CharSequence?, password: Boolean): String = if (password) "[password]" else
        value?.toString()?.replace("\n", "\\n")?.replace("\r", "\\r")?.replace("\t", "\\t")?.take(600).orEmpty()
    fun activity(tasks: String?, pkg: String): String? = tasks?.lineSequence()
        ?.filter { it.contains("topResumedActivity") || it.contains("mResumedActivity") }
        ?.mapNotNull { Regex("([A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+)").find(it)?.value }
        ?.firstOrNull { it.substringBefore('/') == pkg }
}
