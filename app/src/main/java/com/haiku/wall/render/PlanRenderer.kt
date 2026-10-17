package com.haiku.wall.render

import android.graphics.Bitmap

object PlanRenderer {
    /** Final frame of [plan], drawn by the same Player code in one pass. The caller owns the returned bitmap. */
    fun renderFinal(plan: Plan, outW: Int, outH: Int): Bitmap {
        val p = Player(plan, outW, outH)
        p.advance(plan.total)
        return p.bitmap() // the Player is dropped; its tiles were already freed when it finished
    }
}
