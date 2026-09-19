package com.sentongoharuna.pulse

import android.content.Context
import android.widget.Toast

/**
 * V280/12 FIX3 shared synchronous UI-action guard.
 * Records a failing button/action and blocks that one action instead of allowing
 * a synchronous UI exception to close the application process.
 */
object DevelopUgandaV28012SafeActions {
    inline fun run(context: Context, area: String, block: () -> Unit): Boolean {
        return try {
            block()
            true
        } catch (t: Throwable) {
            DevelopUgandaV28012RuntimeGuard.recordUiFault(context, area, t)
            Toast.makeText(
                context,
                "ACTION BLOCKED • ${t.javaClass.simpleName}",
                Toast.LENGTH_SHORT
            ).show()
            false
        }
    }
}
