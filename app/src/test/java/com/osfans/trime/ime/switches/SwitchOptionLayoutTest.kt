// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.ime.switches

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SwitchOptionLayoutTest : StringSpec({
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
