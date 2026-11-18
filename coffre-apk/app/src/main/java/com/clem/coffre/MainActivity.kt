package com.clem.coffre

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import java.io.OutputStream

class MainActivity : Activity() {

    private lateinit var web: WebView
    private var chooser: ValueCallback<Array<Uri>>? = null

    /** Enregistre les fichiers sortis du coffre dans Téléchargements, morceau par morceau. */
    inner class Saver {
        private var out: OutputStream? = null
        private var uri: Uri? = null

        @JavascriptInterface
        fun begin(name: String, mime: String): Boolean {
            abort()
            return try {
                val safe = name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_").trim().ifEmpty { "fichier" }
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safe)
                    put(MediaStore.Downloads.MIME_TYPE, mime.ifEmpty { "application/octet-stream" })
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val u = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                if (u == null) {
                    false
                } else {
                    uri = u
                    out = contentResolver.openOutputStream(u)
                    out != null
                }
            } catch (e: Exception) {
                abort()
                false
            }
        }

        @JavascriptInterface
        fun chunk(b64: String) {
            out?.write(Base64.decode(b64, Base64.DEFAULT))
        }

        @JavascriptInterface
        fun end(): String {
            return try {
                out?.close()
                out = null
                val u = uri
                if (u != null) {
                    val cv = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                    contentResolver.update(u, cv, null, null)
                }
                uri = null
                "ok"
            } catch (e: Exception) {
                abort()
                "erreur"
            }
        }

        @JavascriptInterface
        fun abort() {
            try {
                out?.close()
            } catch (e: Exception) {
            }
            out = null
            val u = uri
            uri = null
            if (u != null) {
                try {
                    contentResolver.delete(u, null, null)
                } catch (e: Exception) {
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pas de capture d'écran ni d'aperçu dans les applis récentes
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#0C0E12"))
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.blockNetworkLoads = true
        s.cacheMode = WebSettings.LOAD_NO_CACHE
        s.setSupportZoom(false)
        web.addJavascriptInterface(Saver(), "AndroidSave")

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return loader.shouldInterceptRequest(request.url)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return true // aucune navigation ailleurs
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                chooser?.onReceiveValue(null)
                chooser = callback
                return try {
                    startActivityForResult(params.createIntent(), 1)
                    true
                } catch (e: Exception) {
                    chooser = null
                    callback.onReceiveValue(null)
                    false
                }
            }
        }
        setContentView(web)
        web.loadUrl("https://appassets.androidplatform.net/assets/coffre.html")
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            chooser?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            chooser = null
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        web.evaluateJavascript("(window.coffreBack&&window.coffreBack())===true") { r ->
            if (r != "true") finish()
        }
    }
}
