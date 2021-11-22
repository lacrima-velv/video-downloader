package com.example.videodownloader

import android.app.Activity
import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.InputMethodManager
import androidx.core.net.toUri
import androidx.core.view.*
import timber.log.Timber
import kotlin.math.pow

object Util {
    /**
     * Apply window insets to all sides of the view
     */
    fun setUiWindowInsets(view: View, topPadding: Int = 0, bottomPadding: Int = 0, leftPadding: Int = 0, rightPadding: Int = 0) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom + bottomPadding)
            v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top + topPadding)
            v.updatePadding(left = insets.getInsets(WindowInsetsCompat.Type.systemBars()).left + leftPadding)
            v.updatePadding(right = insets.getInsets(WindowInsetsCompat.Type.systemBars()).right + rightPadding)
            insets
        }
    }

    /**
     * Apply window insets to the bottom of the view
     */
    fun setUiWindowInsetsBottom(view: View, bottomPadding: Int = 0) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom + bottomPadding)
            insets
        }
    }

    /**
     * Apply window insets to the right side of the view
     */
    fun setUiWindowInsetsRight(view: View, rightPadding: Int = 0) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            v.updatePadding(right = insets.getInsets(WindowInsetsCompat.Type.systemBars()).right + rightPadding)
            insets
        }
    }

    /**
     * Apply window insets to the left side of the view
     */
    fun setUiWindowInsetsLeft(view: View, leftPadding: Int = 0) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            v.updatePadding(left = insets.getInsets(WindowInsetsCompat.Type.systemBars()).left + leftPadding)
            insets
        }
    }

    fun removeUiWindowInsets(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            v.updatePadding(bottom = 0)
            v.updatePadding(top = 0)
            v.updatePadding(left = 0)
            v.updatePadding(right = 0)
            insets
        }
    }
    /**
     * Hides status bar and nav bar
     */
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

    /**
     * Hides only status bar
     */
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

    /**
     * Returns status bar and nav bar if they were hidden
     */
    @Suppress("DEPRECATION")
    fun Activity.resetFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController
            if (controller != null) {
                controller.show(WindowInsets.Type.navigationBars())
                controller.show(WindowInsets.Type.statusBars())
            }
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    fun convertDpToPixels(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    /**
     * Checks connection to wi-fi only.
     * If the device is connected to mobile network, it returns false.
     * This is useful for cases when the network traffic might be metered.
     */
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
                isConnectedToWiFi = if (type == null) {
                    false
                } else {
                    type == ConnectivityManager.TYPE_WIFI
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

    /**
     * Checks if a string has a scheme (http(s)://). If it hasn't, this function adds a scheme.
     */
    fun addUriSchemaIfNecessary(currentUri: Uri): Uri {
        var modifiedCurrentUri = currentUri
        if (!(currentUri.toString().startsWith("https://", true) ||
                    currentUri.toString().startsWith("http://", true))
        ) {
            modifiedCurrentUri = "https://$currentUri".toUri()
        }
        return modifiedCurrentUri
    }

    private val Long.bytesToKilobytes
        get() = this.toFloat() / 1024


    private val Long.bytesToMegabytes
        get() = this.toFloat() / 1024f.pow(2)

    /**
     * Converts bytes to kilobytes, if the value is less then 1 MB.
     * If the value is bigger, it converts it to megabytes.
     * Else it displays row bytes.
     */
    fun prettyBytes(bytes: Long) = when (bytes.toFloat()) {
        in 0f .. 1024f.pow(2) -> String.format("%.1f", bytes.bytesToKilobytes) + " kB"
        in 1024f.pow(2) .. Float.MAX_VALUE -> String.format("%.1f", bytes.bytesToMegabytes) + " MB"
        else -> "$bytes B"
    }

    /**
     * Returns last part of the uri after "/" without file extension.
     * If there's no last part or no file extension it returns "No name"
     */
    fun getFileNameFromUri(uri: Uri): String {
        val path = uri.path
        val lastPartWithExtension = path?.substring(path.lastIndexOf('/') + 1)
        return lastPartWithExtension?.
        substringBeforeLast('.', lastPartWithExtension) ?: "No name"
    }
}