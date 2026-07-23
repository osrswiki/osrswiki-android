package com.omiyawaki.osrswiki.settings

/**
 * DEPRECATED: This class has been removed to eliminate technical debt.
 * 
 * Background preview generation now uses the same methods as on-demand generation:
 * - ThemePreviewRenderer.getPreview() for theme previews
 * - TablePreviewRenderer.getPreview() for table previews
 * 
 * This ensures consistent WebView configurations and eliminates base URL mismatches
 * that caused font loading failures.
 * 
 * See PreviewGenerationManager.generateAllPreviewsUnified() for the new streamlined approach.
 */

// This file should be deleted but is left as a placeholder to avoid build errors during transition.
// All functionality has been moved to existing renderers.