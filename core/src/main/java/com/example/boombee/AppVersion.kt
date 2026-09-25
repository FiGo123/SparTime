package com.example.boombee

/**
 * Single source of truth for the version watermark shown on the phone and
 * watch Setup screens, so you can always tell which build you're running
 * (e.g. when comparing against a tagged checkpoint like v1). Bump this
 * alongside `versionName` in app/build.gradle and wear/build.gradle.
 */
object AppVersion {
    const val VERSION = "2.9.6"
}
