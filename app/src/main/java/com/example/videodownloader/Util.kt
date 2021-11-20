package com.example.videodownloader

import android.app.Activity
import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.InputMethodManager
import androidx.core.net.toUri
import androidx.core.view.*
import com.example.videodownloader.Util.bytesToKilobytes
import com.example.videodownloader.Util.bytesToMegabytes
import timber.log.Timber
import kotlin.math.nextUp
import kotlin.math.pow
import kotlin.math.roundToInt

object Util {
    /**
     * Make layout going from edge to edge if Android version is at least 11
     */
    fun Activity.makeEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        } else {
            Timber.i("Cannot use makeFullscreen() as API Level < 30")
            return
        }
    }

    /**
     * Apply window insets to the view to avoid overlapping with system bars which occurs
     * if the app is displayed edge to edge
     */
    fun setUiWindowInsets(view: View, topPadding: Int = 0, bottomPadding: Int = 0) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom + bottomPadding)
            v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top + topPadding)
            insets
        }
    }


    //WindowInsets.Type.statusBars()
    @Suppress("DEPRECATION")
    fun Activity.setFullScreen() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
                val controller = window.insetsController
                if (controller != null) {
                    controller.hide(WindowInsets.Type.navigationBars())
                    controller.hide(WindowInsets.Type.statusBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                // This work only for android 4.4+
                val flags = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
                window.decorView.systemUiVisibility = flags
            }
    }
    @Suppress("DEPRECATION")
    fun Activity.removeStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            val controller = window.insetsController
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // This work only for android 4.4+
            val flags = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            window.decorView.systemUiVisibility = flags
        }
    }


    @Suppress("DEPRECATION")
    fun Activity.resetFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController
            if (controller != null) {
                controller.show(WindowInsets.Type.navigationBars())
                controller.show(WindowInsets.Type.statusBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_DEFAULT
            }
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    fun convertDpToPixels(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    @Suppress("DEPRECATION")
    fun Activity.checkIsConnectedToWiFi(): Boolean {
        var isConnectedToWiFi = false
        val connMgr = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connMgr.activeNetwork
            if (network == null) {
                isConnectedToWiFi = false
            } else {
                val activeNetwork = connMgr.getNetworkCapabilities(network)
                if (activeNetwork == null) {
                    isConnectedToWiFi = false
                }
                if (activeNetwork != null) {
                    isConnectedToWiFi = when {
                        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                        else -> false
                    }
                }
            }

        } else {
            connMgr.allNetworks.forEach { network ->
                val type = connMgr.getNetworkInfo(network)?.type
                if (type == null) {
                    isConnectedToWiFi = false
                } else {
                    isConnectedToWiFi = type == ConnectivityManager.TYPE_WIFI
                }
            }

        }
        Timber.d("checkIsConnectedToWiFi() returned $isConnectedToWiFi")
        return isConnectedToWiFi
    }

    fun hideSoftKeyboard(context: Context, view: View) {
        val inputMgr = context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMgr.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun textWatcherForClearingTextButton(view: View) = object : TextWatcher {
        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            Timber.d("beforeTextChanged() is called. p0 is $p0")
            view.isVisible = p0 != null && p0 != ""

        }

        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            Timber.d("onTextChanged() is called. p0 is $p0")
            view.isVisible = p0 != null && p0 != ""
        }

        override fun afterTextChanged(p0: Editable?) {
            Timber.d("afterTextChanged() is called. p0 is $p0")
            view.isVisible = p0 != null && p0.toString() != ""
        }
    }

//    fun addUriSchemaIfNecessary(editableText: Editable): Editable {
//        if (!(editableText.startsWith("https://", true) ||
//                    editableText.startsWith("http://", true))
//        ) {
//            editableText.insert(0, "https://")
//        }
//        return editableText
//    }

    fun addUriSchemaIfNecessary(currentUri: Uri): Uri {
        var modifiedCurrentUri = currentUri
        if (!(currentUri.toString().startsWith("https://", true) ||
                    currentUri.toString().startsWith("http://", true))
        ) {
            modifiedCurrentUri = "https://$currentUri".toUri()
        }
        return modifiedCurrentUri
    }

    val Long.bytesToKilobytes
        get() = this.toFloat() / 1024


    val Long.bytesToMegabytes
        get() = this.toFloat() / 1024f.pow(2)

    fun prettyBytes(bytes: Long) = when (bytes.toFloat()) {
        in 0f .. 1023f -> String.format("%.1f", bytes.bytesToKilobytes) + " kB"
        in 1024f .. Float.MAX_VALUE -> String.format("%.1f", bytes.bytesToMegabytes) + " MB"
        else -> "$bytes B"
    }
}