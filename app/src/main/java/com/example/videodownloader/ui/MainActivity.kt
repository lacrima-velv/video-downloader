package com.example.videodownloader.ui

import android.content.res.Configuration
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.net.toUri
import androidx.core.view.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.videodownloader.BuildConfig
import com.example.videodownloader.R
import com.example.videodownloader.Util.addUriSchemaIfNecessary
import com.example.videodownloader.Util.checkIsConnectedToWiFi
import com.example.videodownloader.Util.convertDpToPixels
import com.example.videodownloader.Util.hideSoftKeyboard
import com.example.videodownloader.Util.removeStatusBar
import com.example.videodownloader.Util.removeUiWindowInsets
import com.example.videodownloader.Util.resetFullscreen
import com.example.videodownloader.Util.setFullScreen
import com.example.videodownloader.Util.setUiWindowInsets
import com.example.videodownloader.Util.setUiWindowInsetsBottom
import com.example.videodownloader.Util.setUiWindowInsetsLeft
import com.example.videodownloader.Util.setUiWindowInsetsRight
import com.example.videodownloader.databinding.ActivityMainBinding
import com.google.android.exoplayer2.ui.PlayerControlView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

const val STATE_RESUME_POSITION = "resumePosition"
const val STATE_PLAYER_PLAYING = "isPlayerPlaying"
/*
Current max video size is 200 MB. If it exceeds the limit,
an error is displayed during downloading and downloading is stopped.
*/
const val MAX_VIDEO_SIZE = 209715200

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fullscreenButton: ImageButton
    private lateinit var fullscreenToolbar: Toolbar
    private lateinit var controller: PlayerControlView
    private lateinit var constraints: ConstraintSet
    private lateinit var mainViewModel: MainViewModel

    // Used for restoring player state during configuration changes
    private var playbackPosition: Long = 0
    private var isPlayerPlaying = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)

        // Debug logs are used only in debug build type
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        mainViewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Set up buttons and toolbar for video's fullscreen mode
        binding.setUpVideoFullscreenViewButtons()

        lifecycleScope.launchWhenStarted {
            /*
            Watch for changing screen to fullscreen / non-fullscreen mode to correctly
            change layout and override onBackPressed in fullscreen mode
             */
            launch {
                mainViewModel.isFullscreenOn.collectLatest { }
            }
            launch {
                mainViewModel.downloadState.collectLatest {
                    showSuitableUiState()
                }
            }
            // currentUri is used for downloading and playing videos
            launch {
                mainViewModel.currentUri.collectLatest {
                    Timber.d("currentUri.value is ${mainViewModel.currentUri.value}")
                }
            }
            // videoTitle is used in fullscreen mode
            launch {
                mainViewModel.videoTitle.collectLatest {
                    Timber.d("Video title is $it")
                    fullscreenToolbar.title = it
                }
            }
            // Show suitable placeholder in video frame if player couldn't play the video
            launch {
                mainViewModel.hasPlayerGotError.collectLatest {
                    Timber.d("Player's got an error")
                    if (mainViewModel.hasPlayerGotError.value) {
                        showCompletedDownloadWithErrorPlaybackWidgets()
                    }
                }
            }
            /*
            Watch for file size when started downloading to display an error and remove downloading
            in case of exceeding the limit
             */
            launch {
                mainViewModel.sizeOfDownloadingFile.collectLatest {
                    if (mainViewModel.sizeOfDownloadingFile.value > MAX_VIDEO_SIZE &&
                        mainViewModel.downloadState.value ==
                        MainViewModel.DownloadState.Downloading) {
                        showFileTooBigErrorWidgets()
                        if (mainViewModel.checkIsAnythingDownloaded()) {
                            mainViewModel.clearAllDownloads(this@MainActivity)
                        }
                    }
                }
            }
            launch {
                mainViewModel.retriedAnotherLinkAfterFailed.collectLatest { }
            }
        }
        // Used to retain player's state
        if (savedInstanceState != null) {
            playbackPosition = savedInstanceState.getLong(STATE_RESUME_POSITION)
            isPlayerPlaying = savedInstanceState.getBoolean(STATE_PLAYER_PLAYING)
        }

        constraints = ConstraintSet()


        // Get player instance, created in view model
        binding.playerView.player = mainViewModel.exoPlayer

        mainViewModel.preparePlayer()

        // Prepare autocomplete view with its clear button to use with our embedded test links
        binding.setUpAutoCompleteView()

        // Add click listeners to buttons for all the states
        binding.downloadButton.setOnClickListener {
            showStartedDownloadWidgets()
            downloadVideo()
        }

        binding.clearButton.setOnClickListener {
            mainViewModel.clearAllDownloads(this)
            mainViewModel.pausePlayer()
        }

        binding.pauseButton.setOnClickListener {
            mainViewModel.pauseDownloading(this)
        }

        binding.resumeButton.setOnClickListener {
            if (checkIsConnectedToWifi()) {
                mainViewModel.resumeDownloading(this)
            } else {
                showErrorWifiResumeWidgets()
            }

        }

        binding.retryButton.setOnClickListener {
            retryDownload()
        }

        binding.retryButtonNoWifi.setOnClickListener {
            retryDownload()
        }

        binding.tryAnotherLinkButton.setOnClickListener {
            showNotStartedDownloadWidgets()
            mainViewModel.setRetryAnotherLinkAfterFailed(true)
        }

        setContentView(binding.root)
    }

    override fun onStart() {
        super.onStart()

        if (mainViewModel.isFullscreenOn.value) {
            openPlayerInFullscreen()
        }
        val screenOrientation = this.resources.configuration.orientation

        if (screenOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            setInsetsForFullscreenLandscape()
            setInsetsForNonFullscreenLandscape()
        } else {
            removeUiWindowInsets(binding.playerFrame)
        }
    }

    override fun onResume() {
        super.onResume()
        // Used for pausing/un-pausing video playback
        if (!isPlayerPlaying) {
            mainViewModel.pausePlayer()
        }
    }

    // Functions to get all suitable views for different states
    private fun ActivityMainBinding.allChangeableWidgets() = listOf(
        inputUri,
        downloadButton,
        playerPlaceholder,
        emptyPlayerImage,
        emptyPlayerText,
        errorPlayerImage,
        errorPlayerText,
        downloadingProgress,
        pauseButton,
        resumeButton,
        clearButton,
        playerView,
        errorGeneral,
        retryButton,
        toolbar,
        fullscreenToolbar,
        errorEmptyInput,
        errorWifi,
        clearInputUri,
        tryAnotherLinkButton,
        retryButtonNoWifi,
        errorFileTooBig
    )
    private fun ActivityMainBinding.notStartedDownloadWidgets() = listOf(
        inputUri,
        downloadButton,
        playerPlaceholder,
        emptyPlayerImage,
        emptyPlayerText,
        toolbar
    )
    private fun ActivityMainBinding.startedDownloadWidgets() = listOf(
        downloadingProgress,
        pauseButton,
        playerPlaceholder,
        emptyPlayerImage,
        emptyPlayerText,
        toolbar
    )
    private fun ActivityMainBinding.pausedDownloadWidgets() = listOf(
        downloadingProgress,
        resumeButton,
        playerPlaceholder,
        emptyPlayerImage,
        emptyPlayerText,
        toolbar
    )
    private fun ActivityMainBinding.completedDownloadWidgets() = listOf(
        clearButton,
        playerView,
        toolbar
    )
    private fun ActivityMainBinding.generalErrorDownloadWidgets() = listOf(
        errorGeneral,
        retryButton,
        playerPlaceholder,
        emptyPlayerImage,
        emptyPlayerText,
        tryAnotherLinkButton,
        toolbar
    )
    private fun ActivityMainBinding.wifiErrorDownloadWidgets() = listOf(
        errorWifi,
        retryButtonNoWifi,
        playerPlaceholder,
        emptyPlayerImage,
        emptyPlayerText,
        toolbar
    )
    private fun ActivityMainBinding.errorEmptyInputWidgets() = listOf(
        errorEmptyInput,
        inputUri,
        downloadButton,
        playerPlaceholder,
        emptyPlayerImage,
        emptyPlayerText,
        toolbar
    )
    private fun ActivityMainBinding.wifiErrorResumeWidgets() = listOf(
        errorWifi,
        resumeButton,
        playerPlaceholder,
        emptyPlayerImage,
        emptyPlayerText,
        toolbar
    )
    private fun ActivityMainBinding.fileTooBigErrorWidgets() = listOf(
        errorFileTooBig,
        tryAnotherLinkButton,
        playerPlaceholder,
        emptyPlayerImage,
        emptyPlayerText,
        toolbar
    )
    private fun ActivityMainBinding.completedDownloadWithErrorPlaybackWidgets() = listOf(
        clearButton,
        toolbar,
        playerPlaceholder,
        errorPlayerImage,
        errorPlayerText
    )
    private fun fullscreenVideoWidgets() = listOf(fullscreenToolbar, binding.playerView)

    // Change views' visibility by using lists of suitable views
    private fun setUpWidgetsForDifferentStates(listOfVisibleWidgets: List<View>) {
        for (widget in listOfVisibleWidgets) {
            widget.isVisible = true
        }
        val listOfNonVisibleWidgets = binding.allChangeableWidgets()
            .minus(listOfVisibleWidgets)

        for (widget in listOfNonVisibleWidgets) {
            widget.isVisible = false
        }
    }

    private fun ActivityMainBinding.setUpVideoFullscreenViewButtons() {
        controller = playerView.findViewById(com.google.android.exoplayer2.ui.R.id.exo_controller)

        fullscreenButton = controller.findViewById(R.id.exo_fullscreen_button)
        fullscreenToolbar = controller.findViewById(R.id.toolbar_fullscreen)

        setSupportActionBar(fullscreenToolbar)

        fullscreenToolbar.setNavigationOnClickListener {
            returnFromPlayerInFullscreen()
        }

        fullscreenButton.setOnClickListener {
            if (!mainViewModel.isFullscreenOn.value) {
                openPlayerInFullscreen()
            } else {
                returnFromPlayerInFullscreen()
            }
        }
    }

    // Display an error if autocomplete view is empty when the user tap Download button
    private fun checkIsEmptyAutoCompleteView(): Boolean {
        val isEmpty = binding.inputUri.editableText.isEmpty()
        if (isEmpty) {
            showErrorEmptyInputWidgets()
        }
        return isEmpty
    }

    // Display an error if there's no wifi connection when the user tap Download button
    private fun checkIsConnectedToWifi(): Boolean {
        val isConnected = checkIsConnectedToWiFi()
        if (!isConnected) {
            showWifiErrorDownloadingWidgets()
        }
        return isConnected
    }

    private fun ActivityMainBinding.setUpAutoCompleteView() {
        val videoLinks = resources.getStringArray(R.array.video_links).toList()
        inputUri.setAdapter(ArrayAdapter(
            this@MainActivity, android.R.layout.simple_dropdown_item_1line, videoLinks)
        )
        // The user must type at least two symbols to input view for suggestions to be displayed
        inputUri.threshold = 2

        inputUri.onItemClickListener = AdapterView.OnItemClickListener { _, _, _, _ ->
            hideSoftKeyboard(this@MainActivity, inputUri)
        }

        inputUri.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                inputUri.showDropDown()
            } else {
                inputUri.dismissDropDown()
                hideSoftKeyboard(this@MainActivity, inputUri)
            }
        }

        fun textWatcherForClearingTextButton(view: View) = object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence?, p1: Int, p2: Int, p3: Int) {
                Timber.d("beforeTextChanged() is called. charSequence is $charSequence")
                view.isVisible = charSequence != null && charSequence.isNotEmpty()
                        && inputUri.isVisible
            }

            override fun onTextChanged(charSequence: CharSequence?, p1: Int, p2: Int, p3: Int) {
                Timber.d("onTextChanged() is called. charSequence is $charSequence")
                view.isVisible = charSequence != null && charSequence.isNotEmpty()
                        && inputUri.isVisible
            }

            override fun afterTextChanged(p0: Editable?) { }
        }

        inputUri.addTextChangedListener(
            textWatcherForClearingTextButton(clearInputUri)
        )
        // Clear text by clicking "X" button
        clearInputUri.setOnClickListener {
            inputUri.editableText.clear()
        }
    }

    override fun onPause() {
        /*
        Clear focus of inputUri view to dismiss dropdown,
        because the app crashes if dropdown is shown during configuration change
        */
        super.onPause()
        if (binding.inputUri.isFocused) {
            binding.inputUri.clearFocus()
        }
    }

    private fun openPlayerInFullscreen() {
        mainViewModel.setIsFullscreenOn(true)
        binding.clearButton.isVisible = false
        constraints.apply {
            clone(binding.mainLayout)
            connect(R.id.player_frame, ConstraintSet.TOP, R.id.main_layout,
                ConstraintSet.TOP, 0)
            connect(R.id.player_frame, ConstraintSet.BOTTOM, R.id.main_layout,
                ConstraintSet.BOTTOM, 0)
            connect(R.id.player_frame, ConstraintSet.START, R.id.main_layout,
                ConstraintSet.START, 0)
            connect(R.id.player_frame, ConstraintSet.END, R.id.main_layout,
                ConstraintSet.END, 0)
            setDimensionRatio(R.id.player_frame, null)
            applyTo(binding.mainLayout)
        }

        binding.root.setBackgroundColor(Color.BLACK)

        setUpWidgetsForDifferentStates(fullscreenVideoWidgets())

        fullscreenButton.setImageResource(R.drawable.ic_baseline_fullscreen_exit_36)

        val screenOrientation = this.resources.configuration.orientation

        if (screenOrientation == Configuration.ORIENTATION_PORTRAIT) {
            removeStatusBar()
            setUiWindowInsetsBottom(controller)
        } else {
            setFullScreen()
        }
    }

    private fun returnFromPlayerInFullscreen() {
        mainViewModel.setIsFullscreenOn(false)
        val margin = convertDpToPixels(this, 16)
        val screenOrientation = this.resources.configuration.orientation

        setUpWidgetsForDifferentStates(binding.completedDownloadWidgets())

        // Reset Background color to default (as it's in app theme)
        val typedValue = TypedValue()
        if (theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)) {
            binding.root.setBackgroundColor(typedValue.data)
        }

        fullscreenButton.setImageResource(R.drawable.ic_baseline_fullscreen_36)
        resetFullscreen()

        if (screenOrientation == Configuration.ORIENTATION_PORTRAIT) {
            constraints.apply {
                clone(binding.mainLayout)
                connect(R.id.player_frame, ConstraintSet.TOP, R.id.guideline,
                    ConstraintSet.BOTTOM, 0)
                connect(R.id.player_frame, ConstraintSet.START, R.id.main_layout,
                    ConstraintSet.START, margin)
                connect(R.id.player_frame, ConstraintSet.END, R.id.main_layout,
                    ConstraintSet.END, margin)
                connect(R.id.player_frame, ConstraintSet.BOTTOM, R.id.main_layout,
                    ConstraintSet.BOTTOM, margin)
                setDimensionRatio(R.id.player_frame, "16:9")
                constrainWidth(R.id.player_frame, 0)
                constrainHeight(R.id.player_frame, 0)
                applyTo(binding.mainLayout)
            }
            removeUiWindowInsets(controller)
        } else {
            constraints.apply {
                clone(binding.mainLayout)
                connect(R.id.player_frame, ConstraintSet.TOP, R.id.appbar,
                    ConstraintSet.BOTTOM, margin)
                connect(R.id.player_frame, ConstraintSet.START, R.id.guideline,
                    ConstraintSet.END, margin)
                connect(R.id.player_frame, ConstraintSet.END, R.id.main_layout,
                    ConstraintSet.END, margin)
                connect(R.id.player_frame, ConstraintSet.BOTTOM, R.id.main_layout,
                    ConstraintSet.BOTTOM, margin)
                setDimensionRatio(R.id.player_frame, "16:9")
                constrainWidth(R.id.player_frame, 0)
                constrainHeight(R.id.player_frame, 0)
                applyTo(binding.mainLayout)
            }
            setInsetsForNonFullscreenLandscape()
        }
    }

    private fun setInsetsForFullscreenLandscape() {
        setUiWindowInsets(binding.appbar)
        setUiWindowInsetsRight(binding.playerFrame,
            rightPadding = convertDpToPixels(this, 16))
    }

    private fun setInsetsForNonFullscreenLandscape() {
        setUiWindowInsetsRight(binding.playerFrame)
        setUiWindowInsetsLeft(binding.inputUri,
            leftPadding = convertDpToPixels(this, 8))
    }

    // Use Hardware Back to return from Fullscreen mode (as well as Up button and ][)
    override fun onBackPressed() {
        if (mainViewModel.isFullscreenOn.value) {
            returnFromPlayerInFullscreen()
        } else {
            super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_RESUME_POSITION, mainViewModel.getPlaybackPosition())
        outState.putBoolean(STATE_PLAYER_PLAYING, mainViewModel.isPlayerPlaying())
    }

    private fun showDownloadProgress() {
        lifecycleScope.launch {
            mainViewModel.downloadState.collectLatest {
                mainViewModel.getDownloadedBytes(mainViewModel.currentUri.value).collectLatest {
                    binding.downloadingProgress.text = it
                }
            }
        }
    }

    private fun showStartedDownloadWidgets() {
        setUpWidgetsForDifferentStates(binding.startedDownloadWidgets())

        constraints.apply {
            clone(binding.mainLayout)
            connect(R.id.pause_button, ConstraintSet.TOP, R.id.downloading_progress,
                ConstraintSet.BOTTOM, convertDpToPixels(this@MainActivity, 16))
            connect(R.id.downloading_progress, ConstraintSet.BOTTOM, R.id.pause_button,
                ConstraintSet.TOP, 0)
            applyTo(binding.mainLayout)
        }
    }

    private fun showErrorEmptyInputWidgets() {
        setUpWidgetsForDifferentStates(binding.errorEmptyInputWidgets())
    }

    private fun showErrorWifiResumeWidgets() {
        setUpWidgetsForDifferentStates(binding.wifiErrorResumeWidgets())

        constraints.apply {
            clone(binding.mainLayout)
            connect(R.id.resume_button, ConstraintSet.TOP, R.id.error_wifi,
                ConstraintSet.BOTTOM, convertDpToPixels(this@MainActivity, 16))
            applyTo(binding.mainLayout)
        }

    }

    private fun showFileTooBigErrorWidgets() {
        setUpWidgetsForDifferentStates(binding.fileTooBigErrorWidgets())
        constraints.apply {
            clone(binding.mainLayout)
            connect(R.id.try_another_link_button, ConstraintSet.TOP, R.id.error_file_too_big,
                ConstraintSet.BOTTOM, convertDpToPixels(this@MainActivity, 16))
            applyTo(binding.mainLayout)
        }
    }

    private fun showPausedDownloadWidgets() {
        setUpWidgetsForDifferentStates(binding.pausedDownloadWidgets())

        constraints.apply {
            clone(binding.mainLayout)
            connect(R.id.downloading_progress, ConstraintSet.TOP, R.id.appbar, ConstraintSet.BOTTOM,
                convertDpToPixels(this@MainActivity, 16))
            connect(R.id.downloading_progress, ConstraintSet.BOTTOM, R.id.resume_button,
                ConstraintSet.TOP, 0)
            applyTo(binding.mainLayout)
        }
    }

    private fun showCompletedDownloadWithErrorPlaybackWidgets() {
        setUpWidgetsForDifferentStates(binding.completedDownloadWithErrorPlaybackWidgets())
    }

    private fun retryDownload() {
        if (checkIsConnectedToWifi()) {
            if (mainViewModel.checkHaveFailedDownloadedVideo()) {
                mainViewModel.downloadFailedVideo(this)
            } else {
                mainViewModel.downloadVideo(this, mainViewModel.currentUri.value)
                if (mainViewModel.downloadState.value != MainViewModel.DownloadState.Downloading
                ) {
                    mainViewModel.downloadVideo(this, mainViewModel.currentUri.value)
                    Timber.d("Started download")
                }
            }
        }
    }


    private fun downloadVideo() {
        mainViewModel.updateCurrentUri(binding.inputUri.editableText.toString().toUri())
        /*
       Show an error under view for video link if it remained empty after clicking Download.
       Don't start downloading at that case.
       */
        if (!checkIsEmptyAutoCompleteView()) {
            binding.inputUri.editableText.clear()
            // Reset retriedAnotherLinkAfterFailed after the user clicked Download
            mainViewModel.setRetryAnotherLinkAfterFailed(false)
            if (checkIsConnectedToWifi()) {
                Timber.d("Don't have failed downloads. " +
                        "Last uri is ${mainViewModel.currentUri.value}")
                if (mainViewModel.checkIsAnythingDownloaded()) {
                    mainViewModel.clearAllDownloads(this)
                }
                mainViewModel.updateCurrentUri(
                    addUriSchemaIfNecessary(mainViewModel.currentUri.value)
                )
                Timber.d("videoUri after adding https//:${mainViewModel.currentUri.value}")
                // Start downloading only when nothing is downloading
                mainViewModel.downloadVideo(this, mainViewModel.currentUri.value)
                if (mainViewModel.downloadState.value != MainViewModel.DownloadState.Downloading
                ) {
                    mainViewModel.downloadVideo(this, mainViewModel.currentUri.value)
                    Timber.d("Started download")
                }
            }
        }
    }

    private fun showCompletedDownloadWidgets() {
        setUpWidgetsForDifferentStates(binding.completedDownloadWidgets())
        if (mainViewModel.checkHaveCompletelyDownloadedVideo()) {
            mainViewModel.playDownloadedVideo(isPlayerPlaying, playbackPosition)
        } else {
            showGeneralErrorDownloadWidgets()
        }

    }

    private fun showGeneralErrorDownloadWidgets() {
        setUpWidgetsForDifferentStates(binding.generalErrorDownloadWidgets())
        constraints.apply {
            clone(binding.mainLayout)
            connect(R.id.try_another_link_button, ConstraintSet.TOP, R.id.error_general,
                ConstraintSet.BOTTOM, convertDpToPixels(this@MainActivity, 16))
            connect(R.id.retry_button, ConstraintSet.TOP, R.id.error_general, ConstraintSet.BOTTOM,
                convertDpToPixels(this@MainActivity, 16))
            applyTo(binding.mainLayout)
        }
    }

    private fun showWifiErrorDownloadingWidgets() {
        setUpWidgetsForDifferentStates(binding.wifiErrorDownloadWidgets())
    }

    private fun showNotStartedDownloadWidgets() {
        setUpWidgetsForDifferentStates(binding.notStartedDownloadWidgets())
    }

    private fun showFullscreenWidgets() {
        setUpWidgetsForDifferentStates(fullscreenVideoWidgets())
    }

    private fun showSuitableUiState() {
        when (mainViewModel.downloadState.value) {
            MainViewModel.DownloadState.NotStarted -> {
                Timber.d("DownloadState.NotStarted")
                showNotStartedDownloadWidgets()
            }
            MainViewModel.DownloadState.Removing -> {
                Timber.d("DownloadState.Removing")
                if (mainViewModel.sizeOfDownloadingFile.value <= MAX_VIDEO_SIZE) {
                    showNotStartedDownloadWidgets()
                } else {
                    showFileTooBigErrorWidgets()
                }
            }
            MainViewModel.DownloadState.Queued -> {
                Timber.d("DownloadState.Queued")
                /*
                Downloading is already started
                (it cannot be paused because I use sendStopReason() instead of pause())
                 */
                showStartedDownloadWidgets()
                showDownloadProgress()
            }
            MainViewModel.DownloadState.Downloading -> {
                Timber.d("DownloadState.Downloading")
                showStartedDownloadWidgets()
                showDownloadProgress()
            }
            MainViewModel.DownloadState.Restarting -> {
                Timber.d("DownloadState.Restarting")
                showStartedDownloadWidgets()
            }
            MainViewModel.DownloadState.Completed -> {
                Timber.d("DownloadState.Completed")
                showCompletedDownloadWidgets()
                if (mainViewModel.isFullscreenOn.value) {
                    showFullscreenWidgets()
                }
            }
            MainViewModel.DownloadState.Failed -> {
                Timber.d("DownloadState.Failed")
                // Used to persist opened screen with input view after configuration changing
                if (!mainViewModel.retriedAnotherLinkAfterFailed.value) {
                    showGeneralErrorDownloadWidgets()
                } else {
                    showNotStartedDownloadWidgets()
                }
            }
            MainViewModel.DownloadState.Stopped -> {
                Timber.d("DownloadState.Stopped")
                showPausedDownloadWidgets()
                showDownloadProgress()
            }
        }
    }

}
