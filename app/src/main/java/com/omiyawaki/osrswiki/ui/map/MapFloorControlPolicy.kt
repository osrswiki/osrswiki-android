package com.omiyawaki.osrswiki.ui.map

object MapFloorControlPolicy {
    data class ButtonState(
        val isActionable: Boolean,
        val alpha: Float
    )

    data class State(
        val floorLabel: String,
        val up: ButtonState,
        val down: ButtonState
    )

    fun state(currentFloor: Int, maxFloor: Int): State {
        val boundedMaxFloor = maxFloor.coerceAtLeast(0)
        val boundedFloor = currentFloor.coerceIn(0, boundedMaxFloor)
        val canMoveUp = boundedFloor < boundedMaxFloor
        val canMoveDown = boundedFloor > 0
        return State(
            floorLabel = boundedFloor.toString(),
            up = ButtonState(
                isActionable = canMoveUp,
                alpha = alphaFor(canMoveUp)
            ),
            down = ButtonState(
                isActionable = canMoveDown,
                alpha = alphaFor(canMoveDown)
            )
        )
    }

    private fun alphaFor(isActionable: Boolean): Float {
        return if (isActionable) 1.0f else 0.4f
    }
}
