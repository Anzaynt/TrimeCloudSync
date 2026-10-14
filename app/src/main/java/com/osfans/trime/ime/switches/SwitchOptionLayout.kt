// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.ime.switches

internal object SwitchOptionLayout {
    fun columns(viewport: Int, bottomInset: Int, count: Int, width: Int, minCellWidth: Int, minHeight: Int): Int =
        if (((count + 3) / 4) * minHeight > viewport - bottomInset && width >= 5 * minCellWidth) 5 else 4

    fun rowHeight(viewport: Int, bottomInset: Int, count: Int, minHeight: Int, maxHeight: Int, columns: Int = 4): Int {
        val rows = ((count + columns - 1) / columns).coerceAtLeast(1)
        return ((viewport - bottomInset).coerceAtLeast(0) / rows).coerceIn(minHeight, maxHeight)
    }
}
