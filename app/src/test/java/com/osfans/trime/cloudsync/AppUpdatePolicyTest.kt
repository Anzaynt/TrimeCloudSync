// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.cloudsync

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrowAny

class AppUpdatePolicyTest : StringSpec({
    "update versions compare numerically and never offer current or older versions" {
        AppUpdatePolicy.newer("v0.2.10", "3.3.12-cloud0.2.9") shouldBe true
        AppUpdatePolicy.newer("v0.3.0", "3.3.12-cloud0.2.10") shouldBe true
        AppUpdatePolicy.newer("v0.2.8", "3.3.12-cloud0.2.8") shouldBe false
        AppUpdatePolicy.newer("v0.2.7", "3.3.12-cloud0.2.8") shouldBe false
        shouldThrowAny { AppUpdatePolicy.version("v0.2.9-rc1") }
        AppUpdatePolicy.assetName("v0.2.8") shouldBe "TrimeCloudSync-0.2.8-universal.apk"
    }
    "APK sources must belong to this repository over HTTPS" {
        AppUpdatePolicy.downloadUrl("https://github.com/Anzaynt/TrimeCloudSync/releases/download/v0.2.8/TrimeCloudSync-0.2.8-universal.apk", true) shouldBe true
        listOf(
            "http://github.com/Anzaynt/TrimeCloudSync/releases/download/v0.2.8/x.apk",
            "https://github.com.evil.test/Anzaynt/TrimeCloudSync/releases/download/v0.2.8/x.apk",
            "https://github.com/other/repo/releases/download/v0.2.8/x.apk",
            "https://user@github.com/Anzaynt/TrimeCloudSync/releases/download/v0.2.8/x.apk",
            "https://github.com/Anzaynt/TrimeCloudSync/releases/download/../x.apk",
        ).forEach { AppUpdatePolicy.downloadUrl(it, true) shouldBe false }
    }
    "redirects reject untrusted hosts and protocols" {
        AppUpdatePolicy.downloadUrl("https://release-assets.githubusercontent.com/github-production-release-asset/123/a?token=x", false) shouldBe true
        AppUpdatePolicy.downloadUrl("https://release-assets.githubusercontent.com.evil.test/x", false) shouldBe false
        AppUpdatePolicy.downloadUrl("file:///sdcard/update.apk", false) shouldBe false
        AppUpdatePolicy.downloadUrl("https://github.com:444/x", false) shouldBe false
    }
})
