// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.ime.switches

internal object SwitchOptionLayout {
    fun rowHeight(viewport: Int, bottomInset: Int, count: Int, minHeight: Int, maxHeight: Int): Int {
        val rows = ((count + 3) / 4).coerceAtLeast(1)
        return ((viewport - bottomInset).coerceAtLeast(0) / rows).coerceIn(minHeight, maxHeight)
    }
}
