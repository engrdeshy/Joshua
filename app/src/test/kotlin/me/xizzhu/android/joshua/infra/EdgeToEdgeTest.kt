/*
 * Copyright (C) 2026 Bible Flourish contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package me.xizzhu.android.joshua.infra

import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EdgeToEdgeTest {
    @Test
    fun `drawer content and menu stay clear of system bars`() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val drawer = DrawerLayout(activity)
        val content = FrameLayout(activity).apply { setPadding(1, 2, 3, 4) }
        val menu = FrameLayout(activity).apply { setPadding(5, 6, 7, 8) }
        drawer.addView(
            content,
            DrawerLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        drawer.addView(
            menu,
            DrawerLayout.LayoutParams(200, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START),
        )
        activity.setContentView(drawer)

        activity.applySystemBarInsets(drawer)
        ViewCompat.dispatchApplyWindowInsets(
            drawer,
            WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(10, 20, 30, 40))
                .build(),
        )

        assertEquals(listOf(11, 22, 33, 44), content.paddingValues())
        assertEquals(listOf(15, 26, 37, 48), menu.paddingValues())
        assertEquals(listOf(0, 0, 0, 0), drawer.paddingValues())
    }

    @Test
    fun `ordinary activity root stays clear of system bars`() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val root = FrameLayout(activity).apply { setPadding(1, 2, 3, 4) }
        activity.setContentView(root)

        activity.applySystemBarInsets(root)
        ViewCompat.dispatchApplyWindowInsets(
            root,
            WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(10, 20, 30, 40))
                .build(),
        )

        assertEquals(listOf(11, 22, 33, 44), root.paddingValues())
    }

    private fun android.view.View.paddingValues(): List<Int> =
        listOf(paddingLeft, paddingTop, paddingRight, paddingBottom)
}
