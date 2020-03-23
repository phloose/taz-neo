package de.taz.app.android.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import androidx.appcompat.app.AppCompatActivity
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.R
import de.taz.app.android.singletons.SETTINGS_FIRST_TIME_APP_STARTS
import de.taz.app.android.util.Log
import de.taz.app.android.util.SharedPreferenceBooleanLiveData
import kotlinx.android.synthetic.main.activity_welcome.*

class WelcomeActivity : AppCompatActivity() {

    private val log by Log

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)
        web_view_fullscreen_content.loadUrl("file:///android_asset/html/slide1.html")

        val ws: WebSettings = web_view_fullscreen_content.settings
        ws.javaScriptEnabled = true
        web_view_fullscreen_content.addJavascriptInterface(object : Any() {
            @JavascriptInterface
            fun performClick() {
                log.debug("data policy accepted")
                acceptDataPolicy()
                finish()
            }
        }, "ok")
    }

    private fun acceptDataPolicy() {
        val tazApiCssPreferences =
            applicationContext.getSharedPreferences(PREFERENCES_TAZAPICSS, Context.MODE_PRIVATE)

        SharedPreferenceBooleanLiveData(
            tazApiCssPreferences, SETTINGS_FIRST_TIME_APP_STARTS, true
        ).postValue(true)
    }
}
