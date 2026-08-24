package com.omiyawaki.osrswiki.page

import java.util.regex.Pattern

object osrsNativeCalcDefinition {
    val spikeNativeTitles: Set<String> = setOf("Calculator:Agility")

    enum class ParamType(val token: String) {
        STRING("string"),
        INT("int"),
        NUMBER("number"),
        SELECT("select"),
        BUTTON_SELECT("buttonselect"),
        CHECK("check"),
        TOGGLE_SWITCH("toggleswitch"),
        TOGGLE_BUTTON("togglebutton"),
        HS("hs"),
        RSN("rsn"),
        HIDDEN("hidden"),
        FIXED("fixed"),
        SEMI_HIDDEN("semihidden"),
        UNKNOWN("");

        companion object {
            fun from(raw: String): ParamType =
                entries.firstOrNull { it.token == raw } ?: UNKNOWN
        }
    }

    enum class InvokeKind { TEMPLATE, MODULE }

    enum class FallbackReason {
        MISSING_CONFIG,
        UNKNOWN_PARAM_TYPE,
        PARSE_ERROR,
        UNSUPPORTED_TITLE
    }

    data class Invoke(
        val kind: InvokeKind,
        val template: String? = null,
        val module: String? = null,
        val moduleFunc: String? = null
    )

    data class Ui(
        val name: String,
        val formId: String,
        val resultId: String,
        val autosubmit: String
    )

    data class Input(
        val name: String,
        val label: String,
        val defaultValue: String,
        val type: ParamType,
        val range: String,
        val options: List<String>,
        val toggles: Map<String, List<String>>,
        val toggleOff: Map<String, List<String>>,
        val minValue: Int? = null,
        val maxValue: Int? = null
    )

    data class Model(
        val schemaVersion: Int,
        val id: String,
        val pageId: Int?,
        val revId: Int?,
        val wikiOrigin: String,
        val family: String,
        val ui: Ui,
        val invoke: Invoke,
        val inputs: List<Input>,
        val unknownTypes: List<String>
    )

    private val kitTypes = ParamType.entries.filter { it != ParamType.UNKNOWN }.toSet()
    private val prePattern = Pattern.compile(
        """(?is)<pre[^>]*class="[^"]*jcConfig[^"]*"[^>]*>(.*?)</pre>"""
    )
    private val loosePattern = Pattern.compile(
        """(?is)(?:^|\n)\s*(?:template|module)\s*=.+?(?=\n\s*(?:\{\||----|<pre|$))"""
    )

    fun parse(
        text: String,
        title: String? = null,
        pageId: Int? = null,
        revId: Int? = null
    ): Model? {
        val config = firstConfig(text) ?: return null
        var name = "Calculator"
        var formId = ""
        var resultId = ""
        var autosubmit = "off"
        var invokeKind: InvokeKind? = null
        var template: String? = null
        var module: String? = null
        var moduleFunc: String? = null
        val inputs = mutableListOf<Input>()
        val unknownTypes = mutableListOf<String>()
        for (rawLine in config.split('\n')) {
            val parsed = splitConfigLine(rawLine) ?: continue
            val (key, value) = parsed
            if (key != "param") {
                when (key) {
                    "form" -> formId = value
                    "result" -> resultId = value
                    "name" -> if (value.isNotEmpty()) name = value
                    "autosubmit" -> autosubmit = value.ifEmpty { "off" }
                    "template" -> {
                        invokeKind = InvokeKind.TEMPLATE
                        template = value
                    }
                    "module" -> {
                        invokeKind = InvokeKind.MODULE
                        module = value
                    }
                    "modulefunc" -> moduleFunc = value.ifEmpty { "main" }
                }
                continue
            }
            val fields = value.split(Regex("""\s*\|\s*""")).toMutableList()
            while (fields.size < 6) fields.add("")
            val inputName = fields[0]
            if (inputName.isEmpty()) continue
            val label = fields[1].ifEmpty { inputName }
            var defaultValue = fields[2]
            val rawType = fields[3].lowercase()
            val range = fields[4]
            val rawToggles = fields[5]
            val type = ParamType.from(rawType)
            if (type == ParamType.UNKNOWN && rawType.isNotEmpty()) {
                unknownTypes.add(rawType)
            }
            val toggleDefault = when {
                defaultValue.isNotEmpty() -> defaultValue
                type == ParamType.TOGGLE_SWITCH ||
                    type == ParamType.TOGGLE_BUTTON ||
                    type == ParamType.CHECK -> "true"
                else -> inputName
            }
            if (type == ParamType.TOGGLE_SWITCH && defaultValue.isEmpty()) {
                defaultValue = "false"
            }
            val toggles = parseToggles(rawToggles, toggleDefault)
            val bounds = intRange(range, type)
            inputs.add(
                Input(
                    name = inputName,
                    label = label,
                    defaultValue = defaultValue,
                    type = type,
                    range = range,
                    options = optionsFor(type, range),
                    toggles = toggles.first,
                    toggleOff = toggles.second,
                    minValue = bounds.first,
                    maxValue = bounds.second
                )
            )
        }
        val kind = invokeKind ?: return null
        if (kind == InvokeKind.MODULE && moduleFunc.isNullOrEmpty()) {
            moduleFunc = "main"
        }
        val calcId = title ?: name
        val family = if ((template ?: "").startsWith("Calculator:Skill calc/")) {
            "skill-calc-shared-template"
        } else {
            "jcconfig"
        }
        return Model(
            schemaVersion = 1,
            id = calcId,
            pageId = pageId,
            revId = revId,
            wikiOrigin = osrsWikiWebViewUrl.WIKI_ORIGIN,
            family = family,
            ui = Ui(name, formId, resultId, autosubmit),
            invoke = Invoke(kind, template, module, moduleFunc),
            inputs = inputs,
            unknownTypes = unknownTypes
        )
    }

    fun isNativeChromeEligible(definition: Model?): Boolean {
        if (definition == null) return false
        if (definition.id !in spikeNativeTitles) return false
        when (definition.invoke.kind) {
            InvokeKind.TEMPLATE -> if (definition.invoke.template.isNullOrEmpty()) return false
            InvokeKind.MODULE -> if (definition.invoke.module.isNullOrEmpty()) return false
        }
        if (definition.unknownTypes.isNotEmpty()) return false
        if (definition.inputs.isEmpty()) return false
        return definition.inputs.all { it.type in kitTypes }
    }

    fun invokeWikitext(definition: Model?, values: Map<String, String> = emptyMap()): String? {
        if (definition == null) return null
        val parts = mutableListOf<String>()
        when (definition.invoke.kind) {
            InvokeKind.MODULE -> {
                val module = definition.invoke.module?.trim().orEmpty()
                if (module.isEmpty()) return null
                val func = definition.invoke.moduleFunc?.trim().orEmpty().ifEmpty { "main" }
                parts.add("{{#invoke:$module|$func")
            }
            InvokeKind.TEMPLATE -> {
                val template = definition.invoke.template?.trim().orEmpty()
                if (template.isEmpty()) return null
                parts.add("{{$template")
            }
        }
        val merged = linkedMapOf<String, String>()
        definition.inputs.forEach { merged[it.name] = it.defaultValue }
        values.forEach { (key, value) -> merged[key] = value }
        val visible = visibleInputNames(definition, merged)
        for (input in definition.inputs) {
            if (input.type == ParamType.UNKNOWN) continue
            val always = input.type == ParamType.HIDDEN || input.type == ParamType.FIXED
            if (!always && input.name !in visible) continue
            var value = merged[input.name].orEmpty()
            if ((input.type == ParamType.HS || input.type == ParamType.RSN) && value.isEmpty()) continue
            if (input.type == ParamType.TOGGLE_SWITCH) {
                value = boolToken(value)
            }
            parts.add("|${input.name}=$value")
        }
        parts.add("}}")
        return parts.joinToString("")
    }

    fun parseResultIsError(html: String?): Boolean {
        val body = html ?: ""
        if (body.isBlank()) return true
        val lowered = body.lowercase()
        return "scribunto-error" in lowered || "lua error" in lowered
    }

    fun fallbackReason(
        title: String? = null,
        definition: Model? = null,
        html: String? = null
    ): FallbackReason? {
        if (html != null && parseResultIsError(html)) return FallbackReason.PARSE_ERROR
        if (title != null && title !in spikeNativeTitles) return FallbackReason.UNSUPPORTED_TITLE
        if (definition == null) return FallbackReason.MISSING_CONFIG
        if (definition.unknownTypes.isNotEmpty()) return FallbackReason.UNKNOWN_PARAM_TYPE
        if (!isNativeChromeEligible(definition)) return FallbackReason.UNSUPPORTED_TITLE
        return null
    }

    fun firstConfig(text: String): String? {
        val pre = prePattern.matcher(text)
        if (pre.find()) return pre.group(1)
        val loose = loosePattern.matcher(text)
        if (loose.find()) return loose.group(0)
        return null
    }

    private fun splitConfigLine(line: String): Pair<String, String>? {
        val stripped = line.trim()
        if (stripped.isEmpty() || stripped.startsWith("#") || !stripped.contains("=")) return null
        val index = stripped.indexOf("=")
        val key = stripped.substring(0, index).trim().lowercase()
        val value = stripped.substring(index + 1).trim()
        return key to value
    }

    private fun parseToggles(
        raw: String,
        defaultKey: String
    ): Pair<Map<String, List<String>>, Map<String, List<String>>> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyMap<String, List<String>>() to emptyMap()
        val on = linkedMapOf<String, List<String>>()
        val allKeys = mutableListOf<String>()
        val allVals = mutableListOf<String>()
        for (piece in trimmed.split(";")) {
            val item = piece.trim()
            if (item.isEmpty()) continue
            val keys: List<String>
            val vals: List<String>
            if (item.contains("=")) {
                val eq = item.indexOf("=")
                keys = item.substring(0, eq).split(",").map { it.trim() }.filter { it.isNotEmpty() }
                vals = item.substring(eq + 1).split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                keys = listOf(defaultKey)
                vals = item.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
            for (key in keys) {
                on[key] = vals
                allKeys.add(key)
            }
            allVals.addAll(vals)
        }
        val uniqueVals = allVals.distinct()
        val off = linkedMapOf<String, List<String>>()
        for (key in allKeys.distinct()) {
            val shown = on[key].orEmpty()
            off[key] = uniqueVals.filter { it !in shown }
        }
        return on to off
    }

    private fun optionsFor(type: ParamType, range: String): List<String> {
        if (type != ParamType.SELECT && type != ParamType.BUTTON_SELECT && type != ParamType.CHECK) {
            return emptyList()
        }
        if (range.isEmpty()) return emptyList()
        return range.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun intRange(range: String, type: ParamType): Pair<Int?, Int?> {
        if (type != ParamType.INT && type != ParamType.NUMBER) return null to null
        val dash = range.indexOf("-")
        if (dash < 0) return null to null
        return range.substring(0, dash).trim().toIntOrNull() to
            range.substring(dash + 1).trim().toIntOrNull()
    }

    private fun visibleInputNames(definition: Model, values: Map<String, String>): Set<String> {
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
        return visible
    }

    private fun boolToken(value: String): String {
        return when (value.lowercase()) {
            "1", "true", "yes", "on" -> "true"
            else -> "false"
        }
    }
}
