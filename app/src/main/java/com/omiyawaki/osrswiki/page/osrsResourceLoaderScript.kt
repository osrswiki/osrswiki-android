package com.omiyawaki.osrswiki.page

/**
 * Wiki `load.php?modules=oojs&only=scripts` is a UMD bundle plus a ResourceLoader
 * trailer `window.OO=module.exports`. In a WebView there is no CommonJS `module`,
 * so that trailer throws and OOUI never becomes ready (Barrows stays on the JS stub).
 */
object osrsResourceLoaderScript {
    private const val BROKEN_OOJS_TRAILER = "window.OO=module.exports;"
    private const val SAFE_OOJS_TRAILER =
        "window.OO=(typeof module!=='undefined'&&module.exports)?module.exports:window.OO;"

    fun sanitize(source: String): String {
        return source.replace(BROKEN_OOJS_TRAILER, SAFE_OOJS_TRAILER)
    }
}
