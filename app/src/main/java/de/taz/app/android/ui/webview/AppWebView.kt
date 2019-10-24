package de.taz.app.android.ui.webview

import android.view.MotionEvent
import android.view.GestureDetector
import android.view.animation.AccelerateDecelerateInterpolator
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.webkit.WebView
import androidx.core.view.NestedScrollingChild2
import androidx.core.view.NestedScrollingChildHelper
import de.taz.app.android.util.Log
import kotlin.math.round


class AppWebView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyle: Int = 0
) : WebView(context, attributeSet, defStyle), NestedScrollingChild2 {

    private val log by Log

    private var isScrolling: Boolean = false

    private var scrollCheckDelay = 100

    private var isAlreadyChecking = false
    private var lastCheckedY = 0
    private var lastCheckedX = 0
    private var checkY = 0
    private var checkX = 0
    private var scrollStopCheckerTask: Runnable = object : Runnable {

        override fun run() {
            if (checkY != lastCheckedY || checkX != lastCheckedX) {
                lastCheckedX = checkX
                lastCheckedY = checkY
                this@AppWebView.postDelayed(this, scrollCheckDelay.toLong())
            } else {
                isScrolling = false
                callback?.onScrollFinished()
                isAlreadyChecking = false
            }

        }
    }

    private var callback: AppWebViewCallback? = null
    private var gestureDetector: GestureDetector? = null


    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        log.debug("l: $l, t: $t, oldl: $oldl, oldt: $oldt")
        checkY = t
        checkX = l
        super.onScrollChanged(l, t, oldl, oldt)
        if (!isAlreadyChecking) {
            isScrolling = true
            callback!!.onScrollStarted()
            isAlreadyChecking = true
            this.postDelayed(scrollStopCheckerTask, scrollCheckDelay.toLong())
        }
    }

    fun smoothScrollToY(y: Int) {
        val density = resources.displayMetrics.density
        val scrollAnimation = ObjectAnimator.ofInt(this, "scrollY", round(y * density).toInt())
        scrollAnimation.duration = 500
        scrollAnimation.interpolator = AccelerateDecelerateInterpolator()
        scrollAnimation.start()
    }

    override fun loadUrl(url: String) {
        log.info("url: $url")
        super.loadUrl(url)
    }

    override fun loadDataWithBaseURL(
        baseUrl: String?,
        data: String,
        mimeType: String?,
        encoding: String?,
        failUrl: String?
    ) {
        log.info(
            "baseUrl: $baseUrl, mimeType: $mimeType, encoding: $encoding, failUrl: $failUrl"
        )
        log.debug("data: $data")
        super.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, failUrl)
    }

    fun setArticleWebViewCallback(listener: AppWebViewCallback) {
        callback = listener
        gestureDetector = GestureDetector(context, WebViewGestureListener(listener))
    }

    private var lastMotionY = 0
    private var nestedYOffset = 0
    private val scrollOffset = IntArray(2)
    private val scrollConsumed = IntArray(2)
    private val childHelper = NestedScrollingChildHelper(this)

    init {
        isNestedScrollingEnabled = true
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return false

        val motionEvent = MotionEvent.obtain(event)
        val currentY = event.y.toInt()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                nestedYOffset = 0
                lastMotionY = currentY
                startNestedScroll(View.SCROLL_AXIS_VERTICAL)
            }

            MotionEvent.ACTION_MOVE -> {
                var deltaY = lastMotionY - currentY

                if (dispatchNestedPreScroll(0, deltaY, scrollConsumed, scrollOffset)) {
                    deltaY -= scrollConsumed[1]
                    motionEvent.offsetLocation(0f, scrollOffset[1].toFloat())
                    nestedYOffset += scrollOffset[1]
                }

                lastMotionY = currentY - scrollOffset[1]

                val oldY = scrollY
                val newScrollY = Math.max(0, oldY + deltaY)
                val dyConsumed = newScrollY - oldY
                val dyUnconsumed = deltaY - dyConsumed

                if (dispatchNestedScroll(0, dyConsumed, 0, dyUnconsumed, scrollOffset)) {
                    lastMotionY -= scrollOffset[1]
                    motionEvent.offsetLocation(0f, scrollOffset[1].toFloat())
                    nestedYOffset += scrollOffset[1]
                }

                motionEvent.recycle()
            }

            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> stopNestedScroll()

            else -> { }
        }

        gestureDetector?.let {
            if (!isScrolling)
                return it.onTouchEvent(event) || super.onTouchEvent(event)
        }
        return super.onTouchEvent(event)
    }

    override fun startNestedScroll(axes: Int, type: Int) = childHelper.startNestedScroll(axes, type)

    override fun stopNestedScroll(type: Int) = childHelper.stopNestedScroll(type)

    override fun hasNestedScrollingParent(type: Int) = childHelper.hasNestedScrollingParent(type)

    override fun dispatchNestedPreScroll(dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?, type: Int) =
        childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)

    override fun dispatchNestedScroll(dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int, offsetInWindow: IntArray?, type: Int) =
        childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type)

}
