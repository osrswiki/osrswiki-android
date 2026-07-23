package com.omiyawaki.osrswiki.accessibility

import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import java.util.concurrent.TimeoutException

class AccessibilityTraversalHarness(
    private val packageName: String
) {
    data class Node(
        val label: String,
        val viewId: String?,
        val className: String?,
        val packageName: String?,
        val bounds: Rect,
        val enabled: Boolean,
        val clickable: Boolean,
        val focusable: Boolean,
        val visibleToUser: Boolean,
        val heading: Boolean,
        val actionIds: Set<Int>
    ) {
        val nonEmptyBounds: Boolean
            get() = bounds.width() > 0 && bounds.height() > 0

        val isTraversalTarget: Boolean
            get() = visibleToUser && nonEmptyBounds && enabled && label.isNotBlank()
    }

    data class Snapshot(
        val rootBounds: Rect,
        val nodes: List<Node>
    ) {
        val traversalNodes: List<Node>
            get() = nodes.filter { it.isTraversalTarget }

        fun firstByIdSuffix(suffix: String): Node? {
            return nodes.firstOrNull { it.viewId?.endsWith(":id/$suffix") == true }
        }

        fun requiredByIdSuffix(suffix: String): Node {
            return firstByIdSuffix(suffix)
                ?: error("Missing accessibility node ending with :id/$suffix. Nodes:\n${debugDump()}")
        }

        fun traversalTargetsByIdSuffix(suffix: String): List<Node> {
            return traversalNodes.filter { it.viewId?.endsWith(":id/$suffix") == true }
        }

        fun assertLabelsContainInOrder(vararg expectedSubstrings: String) {
            val labels = traversalNodes.map { it.label }
            var searchFrom = 0
            expectedSubstrings.forEach { expected ->
                val found = labels.drop(searchFrom).indexOfFirst { it.contains(expected) }
                assertTrue(
                    "Expected traversal label containing '$expected' after index $searchFrom.\nLabels:\n${labels.joinToString("\n")}",
                    found >= 0
                )
                searchFrom += found + 1
            }
        }

        fun assertIdsInOrder(vararg idSuffixes: String) {
            val ids = traversalNodes.map { it.viewId.orEmpty() }
            var searchFrom = 0
            idSuffixes.forEach { suffix ->
                val found = ids.drop(searchFrom).indexOfFirst { it.endsWith(":id/$suffix") }
                assertTrue(
                    "Expected traversal target id ending with '$suffix' after index $searchFrom.\nIds:\n${ids.joinToString("\n")}",
                    found >= 0
                )
                searchFrom += found + 1
            }
        }

        private fun debugDump(): String {
            return nodes.joinToString("\n") { node ->
                "${node.viewId.orEmpty()} ${node.className.orEmpty()} label='${node.label}' " +
                    "enabled=${node.enabled} clickable=${node.clickable} focusable=${node.focusable} " +
                    "visible=${node.visibleToUser} bounds=${node.bounds}"
            }
        }
    }

    fun waitForSnapshot(
        timeoutMs: Long = 10_000,
        predicate: (Snapshot) -> Boolean = { true }
    ): Snapshot {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var lastSnapshot: Snapshot? = null
        while (SystemClock.uptimeMillis() < deadline) {
            val snapshot = captureOrNull()
            if (snapshot != null) {
                lastSnapshot = snapshot
                if (predicate(snapshot)) return snapshot
            }
            SystemClock.sleep(100)
        }
        return lastSnapshot?.takeIf(predicate)
            ?: error("Timed out waiting for accessibility snapshot for package $packageName")
    }

    private fun captureOrNull(): Snapshot? {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        try {
            automation.waitForIdle(1_000, 5_000)
        } catch (_: TimeoutException) {
            // Some screens continue posting accessibility events while loading dynamic content.
            // The caller's predicate decides whether the captured tree is complete enough.
        }
        val root = automation.rootInActiveWindow ?: return null
        val rootBounds = Rect().also { root.getBoundsInScreen(it) }
        val nodes = mutableListOf<Node>()
        walk(root, nodes)
        return Snapshot(rootBounds, nodes.filter { it.packageName == packageName })
    }

    private fun walk(node: AccessibilityNodeInfo, nodes: MutableList<Node>) {
        nodes += node.toHarnessNode()
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            walk(child, nodes)
        }
    }

    private fun AccessibilityNodeInfo.toHarnessNode(): Node {
        val bounds = Rect().also { getBoundsInScreen(it) }
        return Node(
            label = spokenLabel().normalizeSpaces(),
            viewId = viewIdResourceName,
            className = className?.toString(),
            packageName = packageName?.toString(),
            bounds = Rect(bounds),
            enabled = isEnabled,
            clickable = isClickable,
            focusable = isFocusable,
            visibleToUser = isVisibleToUser,
            heading = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isHeading else false,
            actionIds = actionList.map { it.id }.toSet()
        )
    }

    private fun AccessibilityNodeInfo.spokenLabel(): String {
        val ownLabel = listOfNotNull(
            contentDescription?.toString(),
            text?.toString(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) stateDescription?.toString() else null
        ).firstOrNull { it.isNotBlank() }
        if (ownLabel != null) return ownLabel

        if (!isClickable && !isFocusable) return ""

        return (0 until childCount)
            .mapNotNull { getChild(it)?.spokenLabel()?.takeIf(String::isNotBlank) }
            .joinToString(" ")
    }

    private fun String.normalizeSpaces(): String {
        return replace(Regex("""\s+"""), " ").trim()
    }
}
