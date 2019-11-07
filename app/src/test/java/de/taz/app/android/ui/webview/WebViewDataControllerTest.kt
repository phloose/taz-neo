package de.taz.app.android.ui.webview

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import de.taz.app.android.TestLifecycleOwner
import de.taz.app.android.api.interfaces.WebViewDisplayable
import org.junit.After
import org.junit.Before
import org.junit.Test

import org.junit.Assert.*
import org.junit.Rule
import org.mockito.MockitoAnnotations
import java.io.File

class WebViewDataControllerTest {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    private lateinit var webViewDataController: WebViewDataController

    private val webViewDisplayable: WebViewDisplayable = object : WebViewDisplayable {
        override fun getFile(): File? { return File("/path/to/exile") }
        override fun next(): WebViewDisplayable? { return null }
        override fun previous(): WebViewDisplayable? { return null }
    }

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        webViewDataController = WebViewDataController()

    }

    @After
    fun tearDown() {
    }

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @Test
    fun getFileLiveData() {
        // this is needed so fileLiveData updates
        webViewDataController.fileLiveData.observe(TestLifecycleOwner(), Observer {})

        assertNull(webViewDataController.fileLiveData.value)

        webViewDataController.setWebViewDisplayable(webViewDisplayable)
        assertEquals(webViewDataController.fileLiveData.value, webViewDisplayable.getFile())

    }

    @Test
    fun webViewDisplayable() {
        webViewDataController.setWebViewDisplayable(webViewDisplayable)
        assertEquals(webViewDataController.getWebViewDisplayable(), webViewDisplayable)
    }

}