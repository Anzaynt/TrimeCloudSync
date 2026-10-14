// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.cloudsync

import java.net.URI

internal object AppUpdatePolicy {
    const val REPOSITORY = "Anzaynt/TrimeCloudSync"
    const val MAX_APK_BYTES = 128L * 1024 * 1024
    const val CERTIFICATE = "42013e1d2a3e28df6e04da621fcf3ec45261115d90b5cc51af462478683fa59f"

    fun version(value: String): List<Int> {
        val normalized = value.substringAfter("-cloud", value).removePrefix("v")
        require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(normalized)) { "版本号格式不支持" }
        return normalized.split('.').map { it.toInt() }
    }

    fun newer(candidate: String, current: String): Boolean {
        val a = version(candidate)
        val b = version(current)
        for (i in a.indices) if (a[i] != b[i]) return a[i] > b[i]
        return false
    }

    fun assetName(tag: String): String = "TrimeCloudSync-${version(tag).joinToString(".")}-universal.apk"

    fun downloadUrl(value: String, initial: Boolean): Boolean = runCatching {
        val uri = URI(value)
        if (uri.scheme != "https" || uri.userInfo != null || uri.port !in listOf(-1, 443) || uri.fragment != null) return false
        if (initial) {
            uri.host == "github.com" && uri.rawPath.startsWith("/$REPOSITORY/releases/download/") &&
                uri.normalize().rawPath == uri.rawPath && uri.query == null && !uri.rawPath.contains('%')
        } else {
            uri.host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
        }
    }.getOrDefault(false)
}
