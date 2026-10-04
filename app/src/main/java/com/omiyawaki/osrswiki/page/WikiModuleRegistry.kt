package com.omiyawaki.osrswiki.page

/**
 * Centralized registry for MediaWiki module configurations.
 * Replaces hardcoded per-page module lists with template-based approach.
 *
 * Wiki gadget modules are never selected. Article reading uses app-owned
 * JavaScript instead of MediaWiki:Gadget-*.js.
 */
object WikiModuleRegistry {

    data class ModuleConfig(
        val dependencies: List<String> = emptyList(),
        val cssMarkers: List<String> = emptyList(),
        val priority: Priority = Priority.MEDIUM
    )

    enum class Priority {
        HIGH,    // Critical modules (essential functionality)
        MEDIUM,  // Standard modules (tabber, citations)
        LOW      // Optional modules (minor enhancements)
    }

    /**
     * Module definitions extracted from tools/js discovery.
     * Maps module name to configuration for smart loading.
     */
    val modules = mapOf(
        // Medium priority modules (load when detected)
        "ext.Tabber" to ModuleConfig(
            dependencies = listOf("jquery"),
            cssMarkers = listOf("tabber"),
            priority = Priority.MEDIUM
        ),
        "ext.cite.ux-enhancements" to ModuleConfig(
            dependencies = listOf("jquery", "mediawiki.util"),
            cssMarkers = listOf("reference"),
            priority = Priority.MEDIUM
        )
    )

    /**
     * Page templates that define common module combinations.
     * Reduces duplication and enables easy updates.
     */
    val pageTemplates = mapOf(
        "item_pages" to listOf(
            "ext.cite.ux-enhancements"
        ),
        "skill_guides" to listOf(
            "ext.Tabber",
            "ext.cite.ux-enhancements"
        ),
        "general_content" to listOf(
            "ext.cite.ux-enhancements"
        )
    )

    /**
     * Detect required modules based on page content analysis.
     * Uses CSS markers to intelligently determine needed modules.
     */
    fun detectRequiredModules(htmlContent: String, pageTitle: String? = null): List<String> {
        val detectedModules = mutableSetOf<String>()
        
        // Detect modules based on HTML content markers
        for ((moduleName, config) in modules) {
            if (config.cssMarkers.any { marker -> htmlContent.contains(marker, ignoreCase = true) }) {
                detectedModules.add(moduleName)
            }
        }
        
        if (htmlContent.contains("jcConfig", ignoreCase = true) ||
            pageTitle?.startsWith("Calculator:") == true
        ) {
            detectedModules.add("oojs-ui-core")
            detectedModules.add("oojs-ui-widgets")
            detectedModules.add("mediawiki.widgets")
        }

        // Add template-based modules if page matches pattern
        pageTitle?.let { title ->
            val lowerTitle = title.lowercase()
            when {
                isItemPage(lowerTitle) -> detectedModules.addAll(pageTemplates["item_pages"] ?: emptyList())
                isSkillPage(lowerTitle) -> detectedModules.addAll(pageTemplates["skill_guides"] ?: emptyList())
                else -> detectedModules.addAll(pageTemplates["general_content"] ?: emptyList())
            }
        }
        
        // Resolve dependencies
        return resolveDependencies(detectedModules.toList())
    }

    /**
     * Generate MediaWiki RLPAGEMODULES array based on detected modules.
     * Maintains compatibility with existing MediaWiki loading system.
     */
    fun generateRLPAGEMODULES(htmlContent: String, pageTitle: String? = null): List<String> {
        val requiredModules = detectRequiredModules(htmlContent, pageTitle)
        
        // Add standard MediaWiki modules that are always needed
        val standardModules = listOf(
            "ext.kartographer.link",
            "ext.scribunto.logs",
            "site",
            "mediawiki.page.ready",
            "jquery.tablesorter",
            "skins.minerva.scripts",
            "mobile.init",
            "ext.checkUser.clientHints",
            "ext.popups",
            "ext.smw.purge"
        )
        
        return osrsWikiWebViewUrl.withoutMediaWikiGadgets(standardModules + requiredModules).distinct()
    }

    private fun isItemPage(title: String): Boolean {
        // Common item page patterns
        return title.contains("sword") || title.contains("armor") || 
               title.contains("weapon") || title.contains("shield") ||
               title.contains("ring") || title.contains("amulet")
    }

    private fun isSkillPage(title: String): Boolean {
        val skills = listOf("attack", "strength", "defence", "ranged", "prayer",
                           "magic", "cooking", "woodcutting", "fletching", "fishing",
                           "firemaking", "crafting", "smithing", "mining", "herblore",
                           "agility", "thieving", "slayer", "farming", "runecraft",
                           "hunter", "construction")
        return skills.any { skill -> title.contains(skill) }
    }

    private fun resolveDependencies(moduleList: List<String>): List<String> {
        val resolved = mutableSetOf<String>()
        val toProcess = moduleList.toMutableList()
        
        while (toProcess.isNotEmpty()) {
            val module = toProcess.removeAt(0)  // Compatible with older Android versions
            if (module in resolved) continue
            
            resolved.add(module)
            
            // Add dependencies
            modules[module]?.dependencies?.forEach { dep ->
                if (dep !in resolved) {
                    toProcess.add(dep)
                }
            }
        }
        
        return osrsWikiWebViewUrl.withoutMediaWikiGadgets(resolved)
    }
}
