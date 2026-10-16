// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.data.theme

import android.view.KeyEvent
import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.ime.keyboard.KeyAction
import com.osfans.trime.ime.keyboard.KeyCode
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ClassicInputModesTest : StringSpec({
    val names = listOf("default", "letter", "wanxiang", "wanxiang_pro")

    "Chinese starts lowercase and explicit language changes persist" {
        val theme = ThemeTestSupport.decodeBuiltinTheme("classic.trime.yaml")
        theme.style.autoCaps shouldBe false
        for (name in names) {
            val layout = theme.presetKeyboards.getValue(name)
            layout.labelTransform shouldBe TextKeyboard.LabelTransform.NONE
            layout.resetAsciiMode shouldBe false
            layout.asciiMode shouldBe if (name == "letter") 1 else 0
        }
    }

    "caps is a sticky modifier and becomes delimiter only in Chinese composition" {
        val theme = ThemeTestSupport.decodeBuiltinTheme("classic.trime.yaml")
        val action = KeyAction(KeyActionToken.Plain("Cloud_caps"), theme.presetKeys)
        action.code shouldBe KeyEvent.KEYCODE_SHIFT_LEFT
        action.shiftLock shouldBe "click"
        action.isShiftLock shouldBe true
        action.command shouldBe "cloud_caps"
        for (name in names) {
            val key = theme.presetKeyboards.getValue(name).keys[24]
            key.click shouldBe KeyActionToken.Plain("Cloud_caps")
            key.ascii shouldBe KeyActionToken.Plain("Cloud_caps")
            key.composing shouldBe KeyActionToken.Plain("delimiter")
            key.label shouldBe ""
            key.longClick shouldBe null
            key.doubleClick shouldBe null
        }
    }

    "language toggle resolves to the application handler and English punctuation bypasses Shift" {
        val oldLookup = KeyCode.androidKeyNameToCode
        try {
            KeyCode.androidKeyNameToCode = { if (it == "SWITCH_CHARSET") KeyEvent.KEYCODE_SWITCH_CHARSET else 0 }
            val theme = ThemeTestSupport.decodeBuiltinTheme("classic.trime.yaml")
            val toggle = KeyAction(KeyActionToken.Plain("Mode_switch"), theme.presetKeys)
            toggle.code shouldBe KeyEvent.KEYCODE_SWITCH_CHARSET
            toggle.toggle shouldBe "ascii_mode"
            for ((token, literal) in listOf("Cloud_ascii_comma" to ",", "Cloud_ascii_period" to ".")) {
                val action = KeyAction(KeyActionToken.Plain(token), theme.presetKeys)
                action.command shouldBe "cloud_symbol"
                action.option shouldBe literal
                action.code shouldBe KeyEvent.KEYCODE_FUNCTION
            }
            for (name in names) {
                val keys = theme.presetKeyboards.getValue(name).keys
                keys.first { it.click == KeyActionToken.Plain(",") }.ascii shouldBe KeyActionToken.Plain("Cloud_ascii_comma")
                keys.first { it.click == KeyActionToken.Plain(".") }.ascii shouldBe KeyActionToken.Plain("Cloud_ascii_period")
            }
        } finally {
            KeyCode.androidKeyNameToCode = oldLookup
        }
    }

    "each popup contains only its displayed literal symbol without old letter templates" {
        val theme = ThemeTestSupport.decodeBuiltinTheme("classic.trime.yaml")
        val letters = "qwertyuiopasdfghjklzxcvbnm,."
        val symbols = "1234567890~@#$%&*()'/-_:;`!?"
        for (name in names) {
            val keys = theme.presetKeyboards.getValue(name).keys
            for ((letter, symbol) in letters.zip(symbols)) {
                val key = keys.first { it.click == KeyActionToken.Plain(letter.toString()) }
                key.popup.size shouldBe 1
                val preset = theme.presetKeys.getValue(key.popup.single())
                preset.label shouldBe symbol.toString()
                preset.option shouldBe symbol.toString()
                preset.command shouldBe "cloud_symbol"
                key.labelSymbol shouldBe symbol.toString()
            }
        }
    }
})
