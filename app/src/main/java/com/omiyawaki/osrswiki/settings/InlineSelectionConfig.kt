package com.omiyawaki.osrswiki.settings

/**
 * Configuration for inline selection components (themes, tables, etc.).
 * Defines responsive behavior, layout constraints, and appearance settings.
 */
data class InlineSelectionConfig(
    /**
     * Whether to enable responsive layout switching based on screen width.
     * If false, uses fixed layout mode.
     */
    val enableResponsiveLayout: Boolean = true,
    
    /**
     * Force horizontal layout regardless of screen size.
     * Only applies when enableResponsiveLayout is false.
     */
    val forceHorizontalLayout: Boolean = false,
    
    /**
     * Minimum screen width (in dp) required to enable horizontal layout.
     * Only applies when enableResponsiveLayout is true.
     */
    val horizontalThreshold: Int = 600,
    
    /**
     * Minimum card width (in dp) for horizontal layout calculations.
     * Used to determine optimal column count.
     */
    val minCardWidth: Int = 140,
    
    /**
     * Maximum number of columns in horizontal layout.
     */
    val maxColumns: Int = 3,
    
    /**
     * Fixed span count override. If set, always uses this value instead of calculations.
     * Useful for components that always want a specific number of columns (e.g., 2 for table preview).
     */
    val fixedSpanCount: Int? = null
) {
    companion object {
        /**
         * Configuration for theme switcher - iOS-style always horizontal.
         * Always horizontal with exactly 3 columns for Light/Dark/Auto.
         */
        val THEME_CONFIG = InlineSelectionConfig(
            enableResponsiveLayout = false,
            forceHorizontalLayout = true,
            fixedSpanCount = 3
        )
        
        /**
         * Configuration for table collapse switcher.
         * Always horizontal with exactly 2 columns for 2 options.
         */
        val TABLE_CONFIG = InlineSelectionConfig(
            enableResponsiveLayout = false,
            forceHorizontalLayout = true,
            fixedSpanCount = 2
        )
    }
}