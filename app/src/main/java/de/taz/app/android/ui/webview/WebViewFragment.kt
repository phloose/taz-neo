package de.taz.app.android.ui.webview

import android.annotation.SuppressLint
import android.os.*
import android.view.*
import android.webkit.*
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import de.taz.app.android.R
import de.taz.app.android.api.models.Section
import de.taz.app.android.util.FileHelper
import kotlinx.android.synthetic.main.fragment_webview.*
import java.io.File
import de.taz.app.android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


open class WebViewFragment : Fragment(), ArticleWebViewCallback {

    private val log by Log

    private fun callTazApi(methodname: String, vararg params: Any) {

        val jsBuilder = StringBuilder()
        jsBuilder.append("tazApi")
            .append(".")
            .append(methodname)
            .append("(")
        for (i in params.indices) {
            val param = params[i]
            if (param is String) {
                jsBuilder.append("'")
                jsBuilder.append(param)
                jsBuilder.append("'")
            } else
                jsBuilder.append(param)
            if (i < params.size - 1) {
                jsBuilder.append(",")
            }
        }
        jsBuilder.append(");")
        val call = jsBuilder.toString()
        CoroutineScope(Dispatchers.Main).launch{
            log.info("Calling javascript with $call")
            web_view.loadUrl("javascript:$call")
        }
    }

    private fun onGestureToTazapi(gesture: GESTURES, e1: MotionEvent) {
        callTazApi("onGesture", gesture.name, e1.x, e1.y)
    }

    override fun onSwipeLeft(e1: MotionEvent, e2: MotionEvent) {
        log.debug("swiping left")
        onGestureToTazapi(GESTURES.swipeLeft, e1)
    }

    override fun onSwipeRight(e1: MotionEvent, e2: MotionEvent) {
        onGestureToTazapi(GESTURES.swipeRight, e1)
    }


    override fun onSwipeTop(e1: MotionEvent, e2: MotionEvent) {
        onGestureToTazapi(GESTURES.swipeUp, e1)
    }

    override fun onSwipeBottom(e1: MotionEvent, e2: MotionEvent) {
        onGestureToTazapi(GESTURES.swipeDown, e1)
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
//        if (getReaderActivity() != null) {
//            getReaderActivity().speak(articleViewModel.getKey(), getTextToSpeech());
//        }
        return true
    }

    private enum class GESTURES {
        swipeUp, swipeDown, swipeRight, swipeLeft
    }

    override fun onScrollStarted() {
        log.debug("${web_view?.scrollX}, ${web_view?.scrollY}")
    }

    override fun onScrollFinished() {
        log.debug("${web_view?.scrollX}, ${web_view?.scrollY}")
    }


}

class TazWebViewClient : WebViewClient() {

    private val log by Log

    private val fileHelper = FileHelper.getInstance()

    // internal links should be handles by the app, external ones - by a web browser
    private fun handleInternalLinks(view: WebView?, url: String?) : Boolean {
        url?.let {urlString ->
            view?.let {
                if (urlString.startsWith(fileHelper.getFileDirectoryUrl(view.context))) {
                    return true
                }
            }
        }
        return false
    }

    @SuppressLint("Deprecated")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (handleInternalLinks(view, url)) return false
        return super.shouldOverrideUrlLoading(view, url)
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        if (handleInternalLinks(view, request?.url.toString())) return false
        return super.shouldOverrideUrlLoading(view, request)
    }

    // intercept links to "resources/" and "global/" and point them to the correct directories
    private fun overrideInternalLinks(view: WebView?, url: String?) : String? {
        view?.let {
            url?.let {
                val fileDir = fileHelper.getFileDirectoryUrl(view.context)

                var newUrl = url.replace("$fileDir/\\w+/\\d{4}-\\d{2}-\\d{2}/resources/".toRegex(), "$fileDir/resources/")
                newUrl = newUrl.replace("$fileDir/\\w+/\\d{4}-\\d{2}-\\d{2}/global/".toRegex(), "$fileDir/global/")

                return newUrl
            }

        }
        return url
    }

    override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
        val newUrl = overrideInternalLinks(view, url)

        val data = File(newUrl.toString().removePrefix("file:///"))

        log.debug("Intercepted Url is ${url.toString()}")

        // handle correctly different resource types
        // we have to return our own WebResourceResponse object here
        // TODO not sure whether these are all possible resource types and whether all mimeTypes are correct
        return when {
            url.toString().contains(".css") -> WebResourceResponse("text/css", "UTF-8", data.inputStream())
            url.toString().contains(".html") -> WebResourceResponse("text/html", "UTF-8", data.inputStream())
            url.toString().contains(".js") -> WebResourceResponse("application/javascript", "UTF-8", data.inputStream())
            url.toString().contains(".png") -> WebResourceResponse("image/png", "binary", data.inputStream())
            url.toString().contains(".svg") -> WebResourceResponse("image/svg+xml", "UTF-8", data.inputStream())
            url.toString().contains(".woff") -> WebResourceResponse("font/woff", "binary", data.inputStream())
            else -> WebResourceResponse("text/plain", "UTF-8", data.inputStream())
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        request?.let {
            val newUrl = overrideInternalLinks(view, request.url.toString())
            val data = File(newUrl.toString().removePrefix("file:///"))
            log.debug("Intercepted Url is ${request.url}")

            // handle correctly different resource types
            // we have to return our own WebResourceResponse object here
            // TODO not sure whether these are all possible resource types and whether all mimeTypes are correct
            return when {
                newUrl.toString().contains(".css") -> WebResourceResponse("text/css", "UTF-8", data.inputStream())
                newUrl.toString().contains(".html") -> WebResourceResponse("text/html", "UTF-8", data.inputStream())
                newUrl.toString().contains(".js") -> WebResourceResponse("application/javascript", "UTF-8", data.inputStream())
                newUrl.toString().contains(".png") -> WebResourceResponse("image/png", "binary", data.inputStream())
                newUrl.toString().contains(".svg") -> WebResourceResponse("image/svg+xml", "UTF-8", data.inputStream())
                newUrl.toString().contains(".woff") -> WebResourceResponse("font/woff", "binary", data.inputStream())
                else -> WebResourceResponse("text/plain", "UTF-8", data.inputStream())
            }
        }
        return null
     }
}
