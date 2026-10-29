package com.fitlens.companion.data

import android.content.res.Resources
import com.fitlens.companion.R

/*
 * Words for stored values that the data layer itself writes into notifications, reports and exports (#309), so `data/`
 * never reaches up into `ui/`. Screens use them too.
 */

/** "Front", "Side", "Back", "Other"; any other stored pose is shown as it is. */
internal fun poseText(res: Resources, pose: String): String = when (pose) {
    "Front" -> res.getString(R.string.pose_front)
    "Side" -> res.getString(R.string.pose_side)
    "Back" -> res.getString(R.string.pose_back)
    "Other" -> res.getString(R.string.pose_other)
    "" -> res.getString(R.string.pose_not_set)
    else -> pose
}

/** A set's counts as they read on its line (#266): "3 Takedowns". A count that is unset or 0 is left out. */
internal fun countParts(names: List<String>, a: Int?, b: Int?): List<String> =
    listOf(a, b).mapIndexedNotNull { i, v ->
        val name = names.getOrNull(i)
        if (name == null || v == null || v <= 0) null else "$v $name"
    }

/** "Abdomen, upper left", an injection site's full name (#210). */
internal fun siteName(res: Resources, site: InjectionSite): String = res.getString(
    when (site) {
        InjectionSite.ABDOMEN_UPPER_LEFT -> R.string.inj_abd_ul
        InjectionSite.ABDOMEN_UPPER_RIGHT -> R.string.inj_abd_ur
        InjectionSite.ABDOMEN_LOWER_RIGHT -> R.string.inj_abd_lr
        InjectionSite.ABDOMEN_LOWER_LEFT -> R.string.inj_abd_ll
        InjectionSite.THIGH_LEFT -> R.string.inj_thigh_l
        InjectionSite.THIGH_RIGHT -> R.string.inj_thigh_r
        InjectionSite.ARM_LEFT -> R.string.inj_arm_l
        InjectionSite.ARM_RIGHT -> R.string.inj_arm_r
        InjectionSite.GLUTE_LEFT -> R.string.inj_glute_l
        InjectionSite.GLUTE_RIGHT -> R.string.inj_glute_r
    }
)
