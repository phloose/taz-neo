package de.taz.app.android.ui.webview

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.base.BaseDataController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File

open class WebViewDataController : BaseDataController(), WebViewContract.DataController {

    private val webViewDisplayable = MutableLiveData<WebViewDisplayable?>().apply {
        postValue(null)
    }

    val fileLiveData: LiveData<File?> = Transformations.map(webViewDisplayable) {
        runBlocking(Dispatchers.IO) {
            it?.getFile()
        }
    }

    override fun getWebViewDisplayable(): WebViewDisplayable? {
        return webViewDisplayable.value
    }

    override fun setWebViewDisplayable(webViewDisplayable: WebViewDisplayable?) {
        this.webViewDisplayable.postValue(webViewDisplayable)
    }

}