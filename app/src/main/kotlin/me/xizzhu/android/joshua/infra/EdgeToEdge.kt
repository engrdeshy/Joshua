/*
 * Copyright (C) 2026 Bible Flourish contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package me.xizzhu.android.joshua.infra

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.drawerlayout.widget.DrawerLayout

internal fun AppCompatActivity.applySystemBarInsets(root: View) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val insetTargets = if (root is DrawerLayout) root.children.toList() else listOf(root)
    val initialPadding = insetTargets.associateWith { view ->
        Padding(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
    }
    ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
        val insets = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime()
        )
        insetTargets.forEach { target ->
            val padding = checkNotNull(initialPadding[target])
            target.setPadding(
                padding.left + insets.left,
                padding.top + insets.top,
                padding.right + insets.right,
                padding.bottom + insets.bottom,
            )
        }
        windowInsets
    }
    ViewCompat.requestApplyInsets(root)
}

private data class Padding(val left: Int, val top: Int, val right: Int, val bottom: Int)
