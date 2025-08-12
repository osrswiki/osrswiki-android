/**
 * MediaWiki ResourceLoader Queue (RLQ) Coordination System
 * 
 * Extracted from live OSRS Wiki server to ensure proper module loading coordination.
 * This system manages the timing and dependencies of ResourceLoader modules.
 */

RLQ=window.RLQ||[]
