package com.omiyawaki.osrswiki.page

/**
 * Centralized registry for MediaWiki module configurations.
 * Replaces hardcoded per-page module lists with template-based approach.
 */
object WikiModuleRegistry {

    data class ModuleConfig(
        val dependencies: List<String> = emptyList(),
        val cssMarkers: List<String> = emptyList(),
        val priority: Priority = Priority.MEDIUM
    )

    enum class Priority {
        HIGH,    // Critical modules (GE charts, essential functionality)
        MEDIUM,  // Standard modules (tabber, citations)
        LOW      // Optional modules (tooltips, minor enhancements)
    }

    /**
     * Module definitions extracted from tools/js discovery.
     * Maps module name to configuration for smart loading.
     */
    val modules = mapOf(
        // High priority modules (always load)
        "ext.gadget.GECharts" to ModuleConfig(
            dependencies = listOf("ext.gadget.GECharts-core", "jquery", "mediawiki.api"),
            cssMarkers = listOf("GEdatachart", "GEChartBox", "GEdataprices"),
            priority = Priority.HIGH
        ),
        "ext.gadget.GECharts-core" to ModuleConfig(
            dependencies = listOf("jquery", "mediawiki.api"),
            priority = Priority.HIGH
        ),
        
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
        ),
        
        // Low priority modules (optional)
        "ext.gadget.tooltips" to ModuleConfig(
            dependencies = listOf("jquery"),
            cssMarkers = listOf("tooltip"),
            priority = Priority.LOW
        )
    )

    /**
     * Page templates that define common module combinations.
     * Reduces duplication and enables easy updates.
     */
    val pageTemplates = mapOf(
        "item_pages" to listOf(
            "ext.gadget.GECharts",
            "ext.cite.ux-enhancements",
            "ext.gadget.tooltips"
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
        
        // Always include high priority modules
        modules.filterValues { it.priority == Priority.HIGH }
            .keys
            .forEach { detectedModules.add(it) }
        
        // Detect modules based on HTML content markers
        for ((moduleName, config) in modules) {
            if (config.cssMarkers.any { marker -> htmlContent.contains(marker, ignoreCase = true) }) {
                detectedModules.add(moduleName)
            }
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
            "ext.gadget.rsw-util",
            "ext.gadget.switch-infobox",
            "mobile.init",
            "ext.checkUser.clientHints",
            "ext.popups",
            "ext.smw.purge"
        )
        
        return (standardModules + requiredModules).distinct()
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
        
        return resolved.toList()
    }
}