package com.fitlens.companion.ui

import androidx.compose.ui.unit.dp
import com.fitlens.companion.ui.design.iconsThatFit
import org.junit.Assert.assertEquals
import org.junit.Test

/** The top bar's icons that leave the title room (#182); the rest move into the ⋮ menu. */
class TopBarFitTest {

    @Test
    fun theDayLogKeepsItsThreeIconsOnACommonPhone() {
        // Brand mark, Calendar, Add photos, Add exercise and ⋮ at 360dp and the standard font.
        assertEquals(3, iconsThatFit(360.dp, 1f, others = 1, actions = 3, menu = true))
        assertEquals(3, iconsThatFit(411.dp, 2f, others = 1, actions = 3, menu = true))
    }

    @Test
    fun aNarrowPhoneAtALargeFontMovesOneIntoTheMenu() {
        assertEquals(2, iconsThatFit(320.dp, 2f, others = 0, actions = 3, menu = true))
    }

    @Test
    fun movingAnIconCountsTheMenuButtonItAdds() {
        // No ⋮ yet: two icons fit without one, but a third has to move, and the ⋮ it needs leaves room for one.
        assertEquals(2, iconsThatFit(260.dp, 2f, others = 0, actions = 2, menu = false))
        assertEquals(1, iconsThatFit(260.dp, 2f, others = 0, actions = 3, menu = false))
    }

    @Test
    fun alwaysAtLeastOneAndAtMostThree() {
        assertEquals(1, iconsThatFit(200.dp, 2f, others = 2, actions = 3, menu = true))
        assertEquals(3, iconsThatFit(800.dp, 1f, others = 0, actions = 5, menu = false))
    }
}
