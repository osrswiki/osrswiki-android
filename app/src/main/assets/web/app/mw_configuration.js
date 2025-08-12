/**
 * MediaWiki ResourceLoader Configuration Objects
 * 
 * Extracted from live OSRS Wiki server.
 * These configuration objects are essential for proper module loading.
 */

// Set configuration
window.RLCONF = {"wgBreakFrames":false,"wgSeparatorTransformTable":["",""],"wgDigitTransformTable":["",""],"wgDefaultDateFormat":"dmy","wgMonthNames":["","January","February","March","April","May","June","July","August","September","October","November","December"],"wgRequestId":"dc1fa1a3bf81187bb0ea714ae7c1f7de","wgCanonicalNamespace":"Exchange","wgCanonicalSpecialPageName":false,"wgNamespaceNumber":114,"wgPageName":"Exchange:Logs","wgTitle":"Logs","wgCurRevisionId":369727,"wgRevisionId":369727,"wgArticleId":63487,"wgIsArticle":true,"wgIsRedirect":false,"wgAction":"view","wgUserName":null,"wgUserGroups":["*"],"wgCategories":["Grand Exchange by date updated","Grand Exchange"],"wgPageViewLanguage":"en-gb","wgPageContentLanguage":"en-gb","wgPageContentModel":"wikitext","wgRelevantPageName":"Exchange:Logs","wgRelevantArticleId":63487,"wgIsProbablyEditable":true,"wgRelevantPageIsProbablyEditable":true,"wgRestrictionEdit":[],"wgRestrictionMove":[],"simpleBatchUploadMaxFilesPerBatch":{"*":1000},"wgMediaViewerOnClick":true,"wgMediaViewerEnabledByDefault":true,"wgCiteReferencePreviewsActive":false,"wgVisualEditor":{"pageLanguageCode":"en-GB","pageLanguageDir":"ltr","pageVariantFallbacks":"en-gb"},"wgMFDisplayWikibaseDescriptions":{"search":false,"watchlist":false,"tagline":false},"wgPopupsFlags":0,"wgEditSubmitButtonLabelPublish":false,"wgCheckUserClientHintsHeadersJsApi":["architecture","bitness","brands","fullVersionList","mobile","model","platform","platformVersion"]};

// Set module states
window.RLSTATE = {"ext.gadget.switch-infobox-styles":"ready","ext.gadget.articlefeedback-styles":"ready","ext.gadget.stickyTableHeaders":"ready","ext.gadget.falseSubpage":"ready","ext.gadget.headerTargetHighlight":"ready","site.styles":"ready","user.styles":"ready","user":"ready","user.options":"loading","skins.vector.styles.legacy":"ready","skins.vector.search.codex.styles":"ready","ext.visualEditor.desktopArticleTarget.noscript":"ready","ext.embedVideo.styles":"ready"};

// Set page modules
window.RLPAGEMODULES = ["site","mediawiki.page.ready","skins.vector.legacy.js","mmv.bootstrap","ext.gadget.rsw-util","ext.gadget.switch-infobox","ext.gadget.exchangePages","ext.gadget.GECharts","ext.gadget.compare","ext.gadget.autosort","ext.gadget.highlightTable","ext.gadget.titleparenthesis","ext.gadget.tooltips","ext.gadget.topIcons","ext.gadget.Username","ext.gadget.countdown","ext.gadget.autocollapse","ext.gadget.checkboxList","ext.gadget.Charts","ext.gadget.navbox-tracking","ext.gadget.sidebar-tracking","ext.gadget.wikisync","ext.gadget.smwlistsfull","ext.gadget.jsonDoc","ext.gadget.articlefeedback","ext.gadget.calc","ext.gadget.infoboxQty","ext.gadget.calculatorNS","ext.gadget.dropDisplay","ext.gadget.mmgkc","ext.gadget.fightcaverotations","ext.gadget.livePricesMMG","ext.gadget.contributions","ext.gadget.skinTogglesNew","ext.gadget.utcclock","ext.gadget.relativetime","ext.gadget.sectionAnchors","ext.gadget.audioplayer","ext.gadget.musicmap","ext.gadget.equipment","ext.gadget.dropdown","ext.gadget.newPage","ext.gadget.purge","ext.gadget.ReferenceTooltips","ext.gadget.fileDownload","ext.gadget.oswf","ext.gadget.searchfocus","ext.gadget.tilemarkers","ext.gadget.loadout","ext.gadget.leaguefilter","ext.visualEditor.desktopArticleTarget.init","ext.visualEditor.targetLoader","ext.embedVideo.overlay","ext.checkUser.clientHints","ext.popups","ext.smw.purge"];

// Apply configuration to MediaWiki
if (window.mw && window.mw.config) {
    window.mw.config.set(window.RLCONF);
}

if (window.mw && window.mw.loader) {
    window.mw.loader.state(window.RLSTATE);
    if (window.RLPAGEMODULES.length > 0) {
        window.mw.loader.load(window.RLPAGEMODULES);
    }
}
