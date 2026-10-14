// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.ime.switches

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SwitchOptionLayoutTest : StringSpec({
    "shorter keyboard uses five menu columns when touch targets still fit" {
        val columns = SwitchOptionLayout.columns(208, 28, 10, 400, 72, 72)
        columns shouldBe 5
        (SwitchOptionLayout.rowHeight(208, 28, 10, 72, 96, columns) * 2 <= 180) shouldBe true
        SwitchOptionLayout.columns(208, 28, 10, 320, 72, 72) shouldBe 4
    }
    "three menu rows fit above the system navigation area" {
        val height = SwitchOptionLayout.rowHeight(260, 28, 10, 72, 96)
        (height * 3 <= 260 - 28) shouldBe true
    }
    "small windows preserve usable targets and allow scrolling" {
        SwitchOptionLayout.rowHeight(150, 30, 10, 72, 96) shouldBe 72
    }
    "additional schema options fit or remain scrollable" {
        SwitchOptionLayout.rowHeight(260, 28, 17, 72, 96) shouldBe 72
        SwitchOptionLayout.rowHeight(600, 28, 10, 72, 96) shouldBe 96
    }
})
