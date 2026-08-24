package com.omiyawaki.osrswiki.page

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.util.concurrent.Executors

class osrsNativeCalcSession(
    private val context: Context,
    private val onChange: () -> Unit
) {
    enum class Phase { IDLE, LOADING, NATIVE, SUBMITTING, FALLBACK }

    var phase: Phase = Phase.IDLE
        private set
    var definition: osrsNativeCalcDefinition.Model? = null
        private set
    var values: MutableMap<String, String> = linkedMapOf()
        private set
    var introCopy: String = ""
        private set
    var resultHtml: String = ""
        private set
    var resultDocument: String = ""
        private set
    var statusMessage: String = ""
        private set
    var hiscoresError: String? = null
        private set
    var formError: String? = null
        private set
    var fallbackReason: osrsNativeCalcDefinition.FallbackReason? = null
        private set
    var usesDarkTheme: Boolean = false

    val chromeTitle: String
        get() = osrsNativeCalcDefinition.chromeTitle(definition?.id ?: pageTitle)

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var submitToken = 0
    private var pageTitle: String = ""

    fun start(title: String, usesDarkTheme: Boolean, forceFallback: Boolean = false) {
        pageTitle = title
        this.usesDarkTheme = usesDarkTheme
        if (title !in osrsNativeCalcDefinition.spikeNativeTitles) {
            fallbackReason = osrsNativeCalcDefinition.FallbackReason.UNSUPPORTED_TITLE
            phase = Phase.FALLBACK
            onChange()
            return
        }
        if (forceFallback) {
            fallbackReason = osrsNativeCalcDefinition.FallbackReason.MISSING_CONFIG
            phase = Phase.FALLBACK
            onChange()
            return
        }
        phase = Phase.LOADING
        statusMessage = "Loading calculator form…"
        onChange()
        worker.execute { loadDefinition() }
    }

    fun setValue(name: String, value: String, submit: Boolean? = null) {
        values[name] = value
        val type = definition?.inputs?.firstOrNull { it.name == name }?.type
            ?: osrsNativeCalcDefinition.ParamType.STRING
        val shouldSubmit = submit ?: osrsNativeCalcDefinition.shouldAutosubmitOnEdit(type)
        if (shouldSubmit) {
            onChange()
            scheduleSubmit()
        }
    }

    fun step(name: String, delta: Int) {
        val current = values[name]?.toIntOrNull() ?: 0
        val input = definition?.inputs?.firstOrNull { it.name == name }
        var next = current + delta
        input?.minValue?.let { next = maxOf(it, next) }
        input?.maxValue?.let { next = minOf(it, next) }
        setValue(name, next.toString(), submit = true)
    }

    fun visibleInputs(): List<osrsNativeCalcDefinition.Input> {
        val definition = definition ?: return emptyList()
        return definition.inputs.filter { input ->
            input.type != osrsNativeCalcDefinition.ParamType.HIDDEN &&
                input.type != osrsNativeCalcDefinition.ParamType.FIXED &&
                isVisible(input.name)
        }
    }

    fun isVisible(name: String): Boolean {
        val definition = definition ?: return true
        val visible = definition.inputs.map { it.name }.toMutableSet()
        for (input in definition.inputs) {
            if (input.toggles.isEmpty()) continue
            val current = values[input.name] ?: input.defaultValue
            val on = input.toggles[current]
            if (on != null) {
                visible.addAll(on)
                visible.removeAll(input.toggleOff[current].orEmpty().toSet())
            } else {
                input.toggles.values.forEach { visible.removeAll(it.toSet()) }
            }
        }
        return name in visible
    }

    fun lookupHiscores() {
        val hs = definition?.inputs?.firstOrNull { it.type == osrsNativeCalcDefinition.ParamType.HS } ?: return
        val rawName = values[hs.name]?.trim().orEmpty()
        if (rawName.isEmpty()) {
            hiscoresError = "Enter a player name to look up."
            onChange()
            return
        }
        hiscoresError = null
        formError = null
        statusMessage = "Looking up hiscores…"
        onChange()
        worker.execute {
            val player = rawName.replace(" ", "_")
            val encoded = java.net.URLEncoder.encode(player, "UTF-8").replace("+", "%20")
            val result = try {
                osrsWikiWebViewProxy.request(
                    context,
                    "GET",
                    "/cors/m=hiscore_oldschool/index_lite.ws?player=$encoded",
                    null
                )
            } catch (error: Exception) {
                org.json.JSONObject()
                    .put("ok", false)
                    .put("error", error.message ?: "hiscores-lookup-failed")
                    .put("body", "")
            }
            val ok = result.optBoolean("ok")
            val body = result.optString("body")
            main.post {
                when (val lookup = osrsNativeCalcDefinition.interpretHiscoresLookup(ok, body, rawName, hs.range)) {
                    is osrsNativeCalcDefinition.HiscoresLookup.Failed -> {
                        hiscoresError = lookup.message
                        statusMessage = ""
                        onChange()
                    }
                    is osrsNativeCalcDefinition.HiscoresLookup.Applied -> {
                        lookup.values.forEach { (key, value) -> values[key] = value }
                        hiscoresError = null
                        statusMessage = ""
                        onChange()
                        scheduleSubmit()
                    }
                }
            }
        }
    }

    fun submitNow() {
        submitToken += 1
        worker.execute { submit() }
    }

    fun release() {
        worker.shutdownNow()
    }

    private fun loadDefinition() {
        val title = pageTitle
        val result = osrsWikiWebViewProxy.request(
            context,
            "GET",
            "/api.php",
            JSONObject()
                .put("action", "query")
                .put("prop", "revisions")
                .put("rvprop", "content|ids")
                .put("rvslots", "main")
                .put("titles", title)
                .put("format", "json")
        )
        val parsed = parseRevision(result.optString("body"), title)
        val eligible = osrsNativeCalcDefinition.isNativeChromeEligible(parsed?.first)
        main.post {
            if (parsed == null || !eligible) {
                fallbackReason = osrsNativeCalcDefinition.fallbackReason(title, parsed?.first, null)
                    ?: osrsNativeCalcDefinition.FallbackReason.MISSING_CONFIG
                phase = Phase.FALLBACK
                onChange()
                return@post
            }
            definition = parsed.first
            values = parsed.first.inputs.associate { it.name to it.defaultValue }.toMutableMap()
            introCopy = introCopy(parsed.second, title)
            phase = Phase.NATIVE
            statusMessage = ""
            onChange()
            worker.execute { submit() }
        }
    }

    private fun scheduleSubmit() {
        if (phase != Phase.NATIVE && phase != Phase.SUBMITTING) return
        val token = ++submitToken
        main.postDelayed({
            if (token != submitToken) return@postDelayed
            worker.execute { submit() }
        }, 500)
    }

    private fun submit() {
        val definition = definition
        val wikitext = osrsNativeCalcDefinition.invokeWikitext(definition, values)
        if (definition == null || wikitext == null) {
            main.post {
                fallbackReason = osrsNativeCalcDefinition.FallbackReason.MISSING_CONFIG
                phase = Phase.FALLBACK
                onChange()
            }
            return
        }
        main.post {
            if (phase == Phase.LOADING) phase = Phase.SUBMITTING
            statusMessage = "Calculating…"
            onChange()
        }
        val wikiTitle = osrsWikiWebViewUrl.mediaWikiPageConfig(pageTitle, pageTitle).pageName
        val result = osrsWikiWebViewProxy.request(
            context,
            "GET",
            "/api.php",
            JSONObject()
                .put("action", "parse")
                .put("text", wikitext)
                .put("prop", "text")
                .put("title", wikiTitle)
                .put("disablelimitreport", "true")
                .put("contentmodel", "wikitext")
                .put("format", "json")
        )
        val html = parseHtml(result)
        main.post {
            if (osrsNativeCalcDefinition.parseResultIsError(html)) {
                if (phase == Phase.NATIVE || phase == Phase.SUBMITTING) {
                    formError = osrsNativeCalcDefinition.parseFailureMessage(html)
                    phase = Phase.NATIVE
                    statusMessage = ""
                    onChange()
                    return@post
                }
                fallbackReason = osrsNativeCalcDefinition.FallbackReason.PARSE_ERROR
                phase = Phase.FALLBACK
                statusMessage = ""
                onChange()
                return@post
            }
            formError = null
            resultHtml = html
            resultDocument = wrapResultHtml(html, usesDarkTheme)
            phase = Phase.NATIVE
            statusMessage = ""
            onChange()
        }
    }

    private fun parseRevision(body: String, title: String): Pair<osrsNativeCalcDefinition.Model, String>? {
        if (body.isBlank()) return null
        return try {
            val json = JSONObject(body)
            val pages = json.optJSONObject("query")?.optJSONObject("pages") ?: return null
            val keys = pages.keys()
            while (keys.hasNext()) {
                val page = pages.optJSONObject(keys.next()) ?: continue
                val revisions = page.optJSONArray("revisions") ?: continue
                val first = revisions.optJSONObject(0) ?: continue
                val wikitext = first.optJSONObject("slots")
                    ?.optJSONObject("main")
                    ?.optString("*")
                    ?.ifEmpty { null }
                    ?: first.optString("*")
                if (wikitext.isNullOrEmpty()) continue
                val definition = osrsNativeCalcDefinition.parse(
                    wikitext,
                    page.optString("title").ifEmpty { title },
                    if (page.has("pageid")) page.optInt("pageid") else null,
                    if (first.has("revid")) first.optInt("revid") else null
                ) ?: continue
                return definition to wikitext
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseHtml(result: JSONObject): String {
        if (!result.optBoolean("ok")) return ""
        val body = result.optString("body")
        if (body.isBlank()) return ""
        return try {
            JSONObject(body).optJSONObject("parse")?.optJSONObject("text")?.optString("*").orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        fun introCopy(wikitext: String, title: String = ""): String {
            val lead = when (title) {
                "Calculator:Combat level" ->
                    "Enter your combat stats, or look them up from hiscores. The wiki returns your combat level. Formulas stay on the wiki, not in the app."
                "Calculator:Agility" ->
                    "Enter your current Agility level or XP and a goal. Methods come from the live wiki calculator, not formulas shipped in the app."
                else ->
                    "Fill the fields below. Results come from the live wiki calculator, not formulas shipped in the app."
            }
            val lines = mutableListOf(lead)
            val start = wikitext.indexOf("===Assumptions===")
            if (start >= 0) {
                val rest = wikitext.substring(start + "===Assumptions===".length)
                val end = rest.indexOf("===").let { if (it < 0) rest.length else it }
                val bullets = rest.substring(0, end).lineSequence()
                    .map { it.trim() }
                    .filter { it.startsWith("*") }
                    .map { "• " + it.drop(1).trim() }
                    .toList()
                if (bullets.isNotEmpty()) {
                    lines.add("")
                    lines.add("Assumptions")
                    lines.addAll(bullets)
                }
            }
            return lines.joinToString("\n")
        }

        fun wrapResultHtml(html: String, dark: Boolean): String {
            val bg = if (dark) "#28221d" else "#f4e0c8"
            val fg = if (dark) "#f4eaea" else "#3A2E1C"
            val border = if (dark) "#D2B48C" else "#4C3D2A"
            val link = if (dark) "#b79d7e" else "#744e2f"
            return """
                <!doctype html>
                <html>
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
                <style>
                html, body { background: $bg; color: $fg; margin: 0; padding: 8px; font-family: sans-serif; }
                table { width: 100%; border-collapse: collapse; color: $fg; background: transparent; }
                th, td { border: 1px solid $border; padding: 6px; color: $fg; }
                a { color: $link; }
                img { max-height: 28px; width: auto; }
                .scribunto-error { color: #B00020; }
                </style>
                </head>
                <body>$html</body>
                </html>
            """.trimIndent()
        }
    }
}
