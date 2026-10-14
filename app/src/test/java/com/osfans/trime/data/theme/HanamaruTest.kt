// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.data.theme

import com.osfans.trime.cloudsync.ThemeArchive
import io.kotest.core.spec.style.StringSpec
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class HanamaruTest : StringSpec({
    "classic personal layout preserves frequent keys and swipes with readable toolbar" {
        val theme = ThemeTestSupport.decodeBuiltinTheme("classic.trime.yaml")
        theme.name shouldBe "经典·顺手版"
        theme.style.keyboardHeight shouldBe 208
        theme.style.keyboardPaddingBottom shouldBe 0
        theme.style.candidateViewHeight shouldBe 34
        theme.presetColorSchemes.containsKey("wechat_dark") shouldBe true
        val keys = theme.presetKeyboards.getValue("default").keys
        theme.style.horizontalGap shouldBe 1
        keys[11].width shouldBe 11f
        keys[22].width shouldBe 10.5f
        listOf(0..9, 10..20, 21..29, 30..34).forEach { row ->
            row.sumOf { keys[it].width.takeIf { value -> value > 0 }?.toDouble() ?: 10.0 } shouldBe 100.0
        }
        val space = keys.first { it.click == com.osfans.trime.data.theme.model.KeyActionToken.Plain("space") }
        space.width shouldBe 40f
        space.longClick shouldBe null
        space.label shouldBe "桉 泽"
        space.labelSymbol shouldBe ""
        theme.presetKeyboards.getValue("letter").keys.first { it.click == com.osfans.trime.data.theme.model.KeyActionToken.Plain("space") }.longClick shouldBe null
        space.swipeLeft shouldBe com.osfans.trime.data.theme.model.KeyActionToken.Plain("Page_Up")
        val enter = keys.first { it.click == com.osfans.trime.data.theme.model.KeyActionToken.Plain("Return") }
        enter.width shouldBe 20f
        keys.any { it.click == com.osfans.trime.data.theme.model.KeyActionToken.Plain(",") } shouldBe true
        keys.any { it.click == com.osfans.trime.data.theme.model.KeyActionToken.Plain(".") } shouldBe true
        theme.toolBar.buttons.map { it.action } shouldBe listOf("Hide", "Mode_switch", "clipboard_window", "Keyboard_numberb", "undo")
        theme.toolBar.buttons.filter { it.foreground.style.startsWith("ic@") }.forEach {
            it.foreground.fontSize shouldBe 22f
            it.foreground.padding shouldBe 4
        }
        theme.presetKeyboards.getValue("numberb").keys.isNotEmpty() shouldBe true
        theme.presetKeys.getValue("Mode_switch").toggle shouldBe "ascii_mode"
    }
    "real GitHub archive imports themes fonts and backgrounds without scripts or schema files" {
        val dir = Files.createTempDirectory("github-theme-test").toFile()
        try {
            val files = ThemeArchive.extract(File("../cloud-sync-tests/hanamaru-upstream.zip"), dir)
            files.count { it.endsWith(".trime.yaml") } shouldBe 4
            files.contains("fonts/FiraCode-Regular.ttf") shouldBe true
            files.contains("backgrounds/hanamaru/yuki.png") shouldBe true
            files.any { it.endsWith(".ncl") || it.startsWith("images/") } shouldBe false
            for (name in files.filter { it.endsWith(".trime.yaml") }) {
                ThemeTestSupport.decodeThemeFile(File(dir, name).absolutePath).presetKeyboards.containsKey("default") shouldBe true
            }
        } finally { dir.deleteRecursively() }
    }
    "untrusted URL and archive paths cannot access credentials or runtime dictionaries" {
        ThemeArchive.repository("https://github.com/ENOA-REIAH-UION/my-trime-theme/") shouldBe "ENOA-REIAH-UION/my-trime-theme"
        for (url in listOf("http://github.com/a/b", "https://github.com.evil.test/a/b", "https://user@github.com/a/b", "https://github.com/a/b/tree/main", "https://github.com/a/b?token=secret")) shouldThrowAny { ThemeArchive.repository(url) }
        for (path in listOf("root/../installation.yaml", "/root/theme.yaml", "root/fonts/../../secret.dpapi", "root\\fonts\\a.ttf")) shouldThrowAny { ThemeArchive.destination(path) }
        for (path in listOf("root/installation.yaml", "root/default.custom.yaml", "root/rime_ice.custom.yaml", "root/rime_ice.schema.yaml", "root/x.userdb/a", "root/lua/script.lua")) ThemeArchive.destination(path) shouldBe null
        val dir = Files.createTempDirectory("github-zip-traversal").toFile()
        try {
            val zip = File(dir, "bad.zip")
            ZipOutputStream(zip.outputStream()).use { out -> out.putNextEntry(ZipEntry("root/../escaped.trime.yaml")); out.write("name: bad".toByteArray()); out.closeEntry() }
            shouldThrowAny { ThemeArchive.extract(zip, File(dir, "stage")) }
            File(dir, "escaped.trime.yaml").exists() shouldBe false
        } finally { dir.deleteRecursively() }
    }
    "oversized compressed files and colliding destinations stop extraction" {
        val dir = Files.createTempDirectory("github-zip-limits").toFile()
        try {
            val zip = File(dir, "large.zip")
            ZipOutputStream(zip.outputStream()).use { out ->
                out.putNextEntry(ZipEntry("root/images/ignored.dat"))
                val block = ByteArray(1024 * 1024)
                repeat(33) { out.write(block) }
                out.closeEntry()
            }
            shouldThrowAny { ThemeArchive.extract(zip, File(dir, "stage")) }
            ZipOutputStream(zip.outputStream()).use { out ->
                for (path in listOf("root/a/theme.trime.yaml", "root/b/theme.trime.yaml")) {
                    out.putNextEntry(ZipEntry(path)); out.write("name: test".toByteArray()); out.closeEntry()
                }
            }
            shouldThrowAny { ThemeArchive.extract(zip, File(dir, "stage2")) }
        } finally { dir.deleteRecursively() }
    }
})
