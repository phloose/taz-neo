package de.taz.app.android.ui.feed

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.viewpager.widget.ViewPager
import de.taz.app.android.util.Log


/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

class VerticalViewPager @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null
) : ViewPager(context!!, attrs) {

    val log by Log

    init {
        setPageTransformer(true, VerticalPageTransformer())
        // Get rid of the overscroll drawing that happens on the left and right (the ripple)
        overScrollMode = View.OVER_SCROLL_NEVER
    }

    /**
     * @return `false` since a vertical view pager can never be scrolled horizontally
     */
    override fun canScrollHorizontally(direction: Int): Boolean {
        return true
    }

    /**
     * @return `true` iff a normal view pager would support horizontal scrolling at this time
     */
    override fun canScrollVertically(direction: Int): Boolean {
        return super.canScrollHorizontally(direction)
    }


    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val toIntercept = super.onInterceptTouchEvent(flipXY(ev))
        // Return MotionEvent to normal
        flipXY(ev)

        return toIntercept
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val toHandle = super.onTouchEvent(flipXY(ev))
        // Return MotionEvent to normal
        flipXY(ev)

        return toHandle
    }

    private fun flipXY(ev: MotionEvent): MotionEvent {
        val width = width.toFloat()
        val height = height.toFloat()
        val x = ev.y / height * width
        val y = ev.x / width * height
        ev.setLocation(x, y)
        return ev
    }

    private class VerticalPageTransformer : PageTransformer {
        override fun transformPage(
            view: View,
            position: Float
        ) {
            val pageWidth = view.width
            val pageHeight = view.height

            when {
                position < -1 -> view.alpha = 0f
                position <= 1 -> {
                    view.alpha = 1f
                    // Counteract the default slide transition
                    view.translationX = pageWidth * -position
                    // set Y position to swipe in from top
                    val yPosition = position * pageHeight
                    view.translationY = yPosition
                }
                else -> view.alpha = 0f
            }
        }
    }

}