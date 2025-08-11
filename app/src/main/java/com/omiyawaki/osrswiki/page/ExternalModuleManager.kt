package com.omiyawaki.osrswiki.page

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Manages external JavaScript modules extracted from MediaWiki.
 * 
 * This class automatically loads deployment configuration and determines
 * which external modules are needed based on HTML content analysis.
 * 
 * External modules are third-party code extracted from OSRS Wiki that
 * enhance functionality like charts, tabbed interfaces, and citations.
 */
object ExternalModuleManager {
    
    private const val TAG = "ExternalModuleManager"
    private const val DEPLOYMENT_REPORT_PATH = "deployment_report.json"
    private const val EXTERNAL_ASSETS_PREFIX = "web/external"
    
    data class ModuleInfo(
        val deployedName: String,
        val conditionalSelectors: List<String>,
        val description: String,
        val priority: String,
        val assetPath: String
    )
    
    private var cachedModules: List<ModuleInfo>? = null
    
    /**
     * Get list of external JS modules required for the given HTML content.
     */
    fun getRequiredModules(context: Context, htmlContent: String): List<String> {
        val modules = loadModuleConfiguration(context)
        val requiredModules = mutableListOf<String>()
        val allExternalDependencies = mutableSetOf<String>()
        
        for (module in modules) {
            if (isModuleRequired(htmlContent, module.conditionalSelectors)) {
                requiredModules.add(module.assetPath)
                Log.d(TAG, "Module required: ${module.deployedName} (${module.description})")
                
                // Collect external dependencies for this module
                val dependencies = getModuleExternalDependencies(context, module.deployedName)
                allExternalDependencies.addAll(dependencies)
            }
        }
        
        // Add all external dependencies at the beginning for proper load order
        val dependencyPaths = allExternalDependencies.map { "web/external/$it" }
        val finalModules = dependencyPaths + requiredModules
        
        if (dependencyPaths.isNotEmpty()) {
            Log.d(TAG, "Added external dependencies: ${dependencyPaths.joinToString()}")
        }
        
        return finalModules
    }
    
    /**
     * Get external dependencies for a specific module from deployment config.
     */
    private fun getModuleExternalDependencies(context: Context, deployedModuleName: String): List<String> {
        try {
            val jsonString = context.assets.open(DEPLOYMENT_REPORT_PATH).bufferedReader().use { it.readText() }
            val deploymentReport = JSONObject(jsonString)
            val deployedModules = deploymentReport.getJSONArray("deployed_modules")
            
            for (i in 0 until deployedModules.length()) {
                val moduleJson = deployedModules.getJSONObject(i)
                val deployedName = moduleJson.getString("deployed_name")
                
                if (deployedName == deployedModuleName) {
                    val analysis = moduleJson.optJSONObject("analysis")
                    if (analysis != null) {
                        val dependenciesArray = analysis.optJSONArray("external_dependencies")
                        if (dependenciesArray != null) {
                            val dependencies = mutableListOf<String>()
                            for (j in 0 until dependenciesArray.length()) {
                                dependencies.add(dependenciesArray.getString(j))
                            }
                            Log.d(TAG, "Found ${dependencies.size} external dependencies for $deployedModuleName: $dependencies")
                            return dependencies
                        }
                    }
                    break
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load external dependencies for $deployedModuleName", e)
        }
        
        return emptyList()
    }
    
    /**
     * Load module configuration from deployment report.
     */
    private fun loadModuleConfiguration(context: Context): List<ModuleInfo> {
        // Use cached configuration if available
        cachedModules?.let { return it }
        
        try {
            val jsonString = context.assets.open(DEPLOYMENT_REPORT_PATH).bufferedReader().use { it.readText() }
            val deploymentReport = JSONObject(jsonString)
            val deployedModules = deploymentReport.getJSONArray("deployed_modules")
            
            val modules = mutableListOf<ModuleInfo>()
            
            for (i in 0 until deployedModules.length()) {
                val moduleJson = deployedModules.getJSONObject(i)
                val config = moduleJson.getJSONObject("config")
                
                val deployedName = moduleJson.getString("deployed_name")
                val conditional = config.getString("conditional")
                val description = config.getString("description")
                val priority = config.getString("priority")
                
                // Parse CSS selectors from conditional string
                val selectors = conditional.split(",").map { it.trim() }
                val assetPath = "$EXTERNAL_ASSETS_PREFIX/$deployedName"
                
                modules.add(ModuleInfo(
                    deployedName = deployedName,
                    conditionalSelectors = selectors,
                    description = description,
                    priority = priority,
                    assetPath = assetPath
                ))
            }
            
            // Sort by priority (high priority modules loaded first)
            modules.sortedBy { if (it.priority == "high") 0 else 1 }.also {
                cachedModules = it
                Log.d(TAG, "Loaded ${it.size} external module configurations")
            }
            
            return cachedModules!!
            
        } catch (e: IOException) {
            Log.w(TAG, "Deployment report not found: $DEPLOYMENT_REPORT_PATH. No external modules available.")
            return emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse deployment report", e)
            return emptyList()
        }
    }
    
    /**
     * Check if a module is required based on CSS selector presence in HTML content.
     */
    private fun isModuleRequired(htmlContent: String, conditionalSelectors: List<String>): Boolean {
        return conditionalSelectors.any { selector ->
            ModuleContentAnalyzer.containsSelector(htmlContent, selector)
        }
    }
    
    /**
     * Clear cached module configuration (for testing).
     */
    internal fun clearCache() {
        cachedModules = null
    }
}

/**
 * Analyzes HTML content to detect CSS selector patterns.
 */
object ModuleContentAnalyzer {
    
    /**
     * Check if HTML content contains elements matching the given CSS selector.
     * 
     * Currently supports:
     * - Class selectors (.classname)
     * - ID selectors (#idname)
     * - Element selectors (tagname)
     */
    fun containsSelector(htmlContent: String, cssSelector: String): Boolean {
        val selector = cssSelector.trim()

        // Basic attribute selector support: [attr] and [attr="value"], commonly used with data-*
        if (selector.startsWith("[") && selector.endsWith("]")) {
            val inner = selector.substring(1, selector.length - 1)
            val parts = inner.split("=").map { it.trim().trim('"', '\'') }
            return when (parts.size) {
                1 -> containsAttribute(htmlContent, parts[0])
                2 -> containsAttributeWithValue(htmlContent, parts[0], parts[1])
                else -> false
            }
        }

        return when {
            selector.startsWith(".") -> {
                // Class selector: .classname
                val className = selector.substring(1)
                containsClass(htmlContent, className)
            }
            selector.startsWith("#") -> {
                // ID selector: #idname
                val idName = selector.substring(1)
                containsId(htmlContent, idName)
            }
            else -> {
                // Element selector: tagname
                containsElement(htmlContent, selector)
            }
        }
    }
    
    /**
     * Check if HTML contains elements with the specified class.
     */
    private fun containsClass(htmlContent: String, className: String): Boolean {
        // Look for class="className" or class="otherClass className otherClass"
        val classPattern = Regex("""class\s*=\s*["'][^"']*\b$className\b[^"']*["']""", RegexOption.IGNORE_CASE)
        return classPattern.containsMatchIn(htmlContent)
    }
    
    /**
     * Check if HTML contains elements with the specified ID.
     */
    private fun containsId(htmlContent: String, idName: String): Boolean {
        // Look for id="idName"
        val idPattern = Regex("""id\s*=\s*["']$idName["']""", RegexOption.IGNORE_CASE)
        return idPattern.containsMatchIn(htmlContent)
    }
    
    /**
     * Check if HTML contains the specified element tag.
     */
    private fun containsElement(htmlContent: String, elementName: String): Boolean {
        // Look for <elementName or </elementName
        val elementPattern = Regex("""</?$elementName\b""", RegexOption.IGNORE_CASE)
        return elementPattern.containsMatchIn(htmlContent)
    }

    private fun containsAttribute(htmlContent: String, attrName: String): Boolean {
        // Look for attrName present in any tag: <... attrName[=| |>] ...>
        val attrPattern = Regex("""<[^>]*\b${Regex.escape(attrName)}\b(?:\s*=|\s|>)""", RegexOption.IGNORE_CASE)
        return attrPattern.containsMatchIn(htmlContent)
    }

    private fun containsAttributeWithValue(htmlContent: String, attrName: String, value: String): Boolean {
        // Look for attrName="value" or 'value'
        val attrValPattern = Regex(
            """<[^>]*\b${Regex.escape(attrName)}\s*=\s*[\"']${Regex.escape(value)}[\"']""",
            RegexOption.IGNORE_CASE
        )
        return attrValPattern.containsMatchIn(htmlContent)
    }
}
