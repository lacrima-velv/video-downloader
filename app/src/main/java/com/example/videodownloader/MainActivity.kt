package com.example.videodownloader

import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.net.toUri
import androidx.core.view.*
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.videodownloader.Util.addUriSchemaIfNecessary
import com.example.videodownloader.Util.checkIsConnectedToWiFi
import com.example.videodownloader.Util.convertDpToPixels
import com.example.videodownloader.Util.hideSoftKeyboard
import com.example.videodownloader.Util.removeStatusBar
import com.example.videodownloader.Util.resetFullscreen
import com.example.videodownloader.Util.setFullScreen
import com.example.videodownloader.Util.textWatcherForClearingTextButton
import com.example.videodownloader.databinding.ActivityMainBinding
import com.google.android.exoplayer2.ui.PlayerControlView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

const val STATE_RESUME_WINDOW = "resumeWindow"
const val STATE_RESUME_POSITION = "resumePosition"
const val STATE_PLAYER_FULLSCREEN = "playerFullscreen"
const val STATE_PLAYER_PLAYING = "playerOnPlay"
const val BOTTOM_BAR_INSET = "bottomBarInset"
const val TOP_BAR_INSET = "topBarInset"
const val LEFT_BAR_INSET = "leftBarInset"
/*
This activity must implement an interface to set onClickListener to a button in
Permission Rationale Dialog
 */
class MainActivity : AppCompatActivity() {

    private lateinit var activityResultLauncherRequestPermission: ActivityResultLauncher<String>
    private lateinit var binding: ActivityMainBinding
    private lateinit var fullscreenButton: ImageButton
    private lateinit var fullscreenToolbar: Toolbar
    private lateinit var controller: PlayerControlView
    private lateinit var constraints: ConstraintSet
    private var videoUri: Uri = "".toUri()

    private lateinit var viewModel: ViewModel

    //private var currentWindow = 0
    private var playbackPosition: Long = 0
    //private var isFullscreen = false
    private var isPlayerPlaying = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }



        viewModel = ViewModelProvider(this).get(ViewModel::class.java)

        viewModel.preparePlayer()

        lifecycleScope.launchWhenStarted {
            viewModel.isFullscreenOn.collectLatest { }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.downloadState.collectLatest {
                showSuitableUiState()
            }
        }

        // TODO: Probably you should keep them in onSaveStateHandle or don't keep at all
        if (savedInstanceState != null) {
            playbackPosition = savedInstanceState.getLong(STATE_RESUME_POSITION)
            isPlayerPlaying = savedInstanceState.getBoolean(STATE_PLAYER_PLAYING)
        }

        constraints = ConstraintSet()


        Timber.d("Download state is ${viewModel.downloadState.value}")

        // Get player instance
        binding.playerView.player = viewModel.exoPlayer

        binding.setUpAutoCompleteView()

        binding.downloadButton.setOnClickListener {

            showStartedDownloadWidgets()

//            if (!viewModel.isPlayerPrepared()) {
//                viewModel.preparePlayer()
//            }
//            val videoUriFromInput = videoUri ?: binding.inputUri.editableText

            Timber.d("Video uri is $videoUri")
            downloadVideo()

        }

        binding.clearButton.setOnClickListener {
            viewModel.clearAllDownloadedVideos(this)
            viewModel.pausePlayer()
        }

        binding.pauseButton.setOnClickListener {
            viewModel.pauseDownloading(this)
        }

//        binding.resumeButton.setOnClickListener {
//            if (checkIsConnectedToWifi()) {
//                viewModel.resumeDownloading(this)
//            } else {
//                showErrorWifiResumeWidgets()
//            }
//
//        }

//        binding.retryButton.setOnClickListener {
//            viewModel.continueDownloadFailedVideo(this, videoUri)
//        }

        binding.tryAnotherLinkButton.setOnClickListener {
            showNotStartedDownloadWidgets()
        }

        binding.setUpVideoFullscreenViewButtons()





        setContentView(binding.root)
    }

    private fun ActivityMainBinding.allChangeableWidgets() = listOf(
        inputUri,
        downloadButton,
        emptyPlayerPlaceholder,
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
        tryAnotherLinkButton
    )

    private fun ActivityMainBinding.notStartedDownloadWidgets() = listOf(
        inputUri,
        downloadButton,
        emptyPlayerPlaceholder,
        toolbar
    )
    private fun ActivityMainBinding.startedDownloadWidgets() = listOf(
        downloadingProgress,
        pauseButton,
        emptyPlayerPlaceholder,
        toolbar
    )
    private fun ActivityMainBinding.pausedDownloadWidgets() = listOf(
        downloadingProgress,
        resumeButton,
        emptyPlayerPlaceholder,
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
        emptyPlayerPlaceholder,
        tryAnotherLinkButton,
        toolbar
    )
    private fun ActivityMainBinding.wifiErrorDownloadWidgets() = listOf(
        errorWifi,
        retryButton,
        emptyPlayerPlaceholder,
        tryAnotherLinkButton,
        toolbar
    )
    private fun ActivityMainBinding.errorEmptyInputWidgets() = listOf(
        errorEmptyInput,
        inputUri,
        downloadButton,
        emptyPlayerPlaceholder,
        toolbar
    )
    private fun ActivityMainBinding.wifiErrorResumeWidgets() = listOf(
        errorWifi,
        resumeButton,
        emptyPlayerPlaceholder,
        toolbar
    )
    private fun fullscreenVideoWidgets() = listOf(fullscreenToolbar, binding.playerView)

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

        // TODO: Set toolbar name to Video's name

        setSupportActionBar(fullscreenToolbar)

        fullscreenToolbar.setNavigationOnClickListener {
            returnFromPlayerInFullscreen()
        }

        fullscreenButton.setOnClickListener {
            Timber.d("Fullscreen button clicked")
            //if (fullscreenButton.tag != R.drawable.ic_baseline_fullscreen_exit_36) {
            if (!viewModel.isFullscreenOn.value) {
                openPlayerInFullscreen()
            } else {
                returnFromPlayerInFullscreen()
            }
        }
    }

    private fun checkIsEmptyAutoCompleteView(): Boolean {
        val isEmpty = binding.inputUri.editableText.isEmpty()
        if (isEmpty) {
            showErrorEmptyInputWidgets()
        }
        return isEmpty
    }

    private fun checkIsConnectedToWifi(): Boolean {
        val isConnected = checkIsConnectedToWiFi()
        if (!isConnected) {
            showWifiErrorDownloadingWidgets()
        }
        return isConnected
    }

    private fun ActivityMainBinding.setUpAutoCompleteView() {
        val videoLinks = resources.getStringArray(R.array.video_links).toList()
        inputUri.setAdapter(ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, videoLinks))
        inputUri.threshold = 2
        inputUri.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // Show dropdown list
                inputUri.showDropDown()
            } else {
                hideSoftKeyboard(this@MainActivity, inputUri)
            }
        }
        inputUri.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, id ->
           // videoUri = parent.getItemAtPosition(position).toString().toUri()
            hideSoftKeyboard(this@MainActivity, inputUri)
        }

        clearInputUri.setOnClickListener {
            inputUri.editableText.clear()
        }

        inputUri.addTextChangedListener(
            textWatcherForClearingTextButton(clearInputUri)
        )

    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (viewModel.isFullscreenOn.value) {
                setFullScreen()
            }

        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (viewModel.isFullscreenOn.value) {
                removeStatusBar()
                ViewCompat.setOnApplyWindowInsetsListener(controller) { view, insets ->
                    view.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
                    insets
                }
            }
        }
    }

    private fun openPlayerInFullscreen() {
        viewModel.setIsFullscreenOn(true)
        val constraints = ConstraintSet()
        val constraintLayout = findViewById<ConstraintLayout>(R.id.main_layout)
        constraints.apply {
            clone(constraintLayout)
            connect(R.id.player_frame, ConstraintSet.TOP, R.id.main_layout, ConstraintSet.TOP, 0)
            setDimensionRatio(R.id.player_frame, null)
            setMargin(R.id.player_frame, ConstraintSet.BOTTOM, 0)
            setMargin(R.id.player_frame, ConstraintSet.START, 0)
            setMargin(R.id.player_frame, ConstraintSet.END, 0)
            applyTo(constraintLayout)
        }

        binding.root.setBackgroundColor(Color.BLACK)

        setUpWidgetsForDifferentStates(fullscreenVideoWidgets())


//        binding.toolbar.isVisible = false
//        binding.inputUri.isVisible = false
//        binding.downloadButton.isVisible = false
        //binding.toolbar.isVisible = true
        fullscreenButton.setImageResource(R.drawable.ic_baseline_fullscreen_exit_36)
       // fullscreenButton.tag = R.drawable.ic_baseline_fullscreen_exit_36

        val screenOrientation = this.resources.configuration.orientation

        if (screenOrientation == Configuration.ORIENTATION_PORTRAIT) {
            removeStatusBar()
            ViewCompat.setOnApplyWindowInsetsListener(controller) { view, insets ->
                view.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
                insets
            }
        } else {
            setFullScreen()
        }

       // fullscreenToolbar.isVisible = true

    }

    private fun returnFromPlayerInFullscreen() {
        viewModel.setIsFullscreenOn(false)
        val margin = convertDpToPixels(this, 16)
        val constraintLayout = findViewById<ConstraintLayout>(R.id.main_layout)
        val constraints = ConstraintSet()
        constraints.apply {
            clone(constraintLayout)
            connect(R.id.player_frame, ConstraintSet.TOP, R.id.guideline, ConstraintSet.BOTTOM, 0)
            connect(R.id.player_frame, ConstraintSet.START, R.id.main_layout, ConstraintSet.START, margin)
            connect(R.id.player_frame, ConstraintSet.END, R.id.main_layout, ConstraintSet.END, margin)
            connect(R.id.player_frame, ConstraintSet.BOTTOM, R.id.main_layout, ConstraintSet.BOTTOM, margin)
            setDimensionRatio(R.id.player_frame, "16:9")
            constrainWidth(R.id.player_frame, 0)
            constrainHeight(R.id.player_frame, 0)
            applyTo(constraintLayout)
        }

        ViewCompat.setOnApplyWindowInsetsListener(controller) { view, insets ->
            view.updatePadding(bottom = 0)
            insets
        }

        setUpWidgetsForDifferentStates(binding.completedDownloadWidgets())

        binding.root.setBackgroundColor(Color.WHITE)
//        binding.toolbar.isVisible = true
//        binding.inputUri.isVisible = false
//        binding.downloadButton.isVisible = false
        fullscreenButton.setImageResource(R.drawable.ic_baseline_fullscreen_36)
        //fullscreenButton.tag = R.drawable.ic_baseline_fullscreen_36
        resetFullscreen()
//        fullscreenToolbar.isVisible = false
    }

    override fun onBackPressed() {
        //if (fullscreenButton.tag == R.drawable.ic_baseline_fullscreen_exit_36) {
        if (viewModel.isFullscreenOn.value) {
            returnFromPlayerInFullscreen()
        } else {
            super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_RESUME_POSITION, viewModel.getPlaybackPosition())
        outState.putBoolean(STATE_PLAYER_PLAYING, viewModel.isPlayerPlaying())
    }

    // TODO CheckForEmptiness
    private fun showDownloadProgress() {
        videoUri = binding.inputUri.editableText.toString().toUri()
       // val gotUri = videoUri ?: binding.inputUri.editableText.toString().toUri()
        Timber.d("videoUri in showDownloadProgress() is $videoUri")
            lifecycleScope.launch {
                viewModel.downloadState.collectLatest {
                    viewModel.getDownloadedBytes(videoUri).collectLatest {
                        binding.downloadingProgress.text = it.toString()

                    }
                }
            }
    }

    // TODO: Show placeholder on Player screen when nothing is downloaded yet
    private fun showStartedDownloadWidgets() {
        setUpWidgetsForDifferentStates(binding.startedDownloadWidgets())
        //showDownloadProgress()
    }

    private fun showErrorEmptyInputWidgets() {
        setUpWidgetsForDifferentStates(binding.errorEmptyInputWidgets())
    }

    private fun showErrorWifiResumeWidgets() {
        setUpWidgetsForDifferentStates(binding.wifiErrorResumeWidgets())
    }


    // TODO: Show placeholder on Player screen when nothing is downloaded yet
    private fun showPausedDownloadWidgets() {
        setUpWidgetsForDifferentStates(binding.pausedDownloadWidgets())
        //showDownloadProgress()
    }


    private fun downloadVideo() {
        //TODO: Clear cache before downloading something new!
        viewModel.clearAllDownloadedVideos(this)
        /*
        Show an error under view for video link if it remained empty after clicking Download.
        Don't start downloading at that case.
         */
        if (!checkIsEmptyAutoCompleteView()) {
            videoUri = binding.inputUri.editableText.toString().toUri()
            Timber.d("videoUri is $videoUri")
            videoUri = addUriSchemaIfNecessary(binding.inputUri.editableText).toString().toUri()
            Timber.d("videoUri after calling addUriSchemaIfNecessary() is $videoUri")
            //isConnected?
            if (checkIsConnectedToWifi()) {
                Timber.d("Started download")
                Timber.d("viewModel.downloadState is ${viewModel.downloadState.value}")
                // Start downloading only at the initial state, when cache is empty and nothing is downloading
                if (viewModel.downloadState.value == ViewModel.DownloadState.NotStarted ||
                    viewModel.downloadState.value == ViewModel.DownloadState.Removing
                ) {
                    viewModel.downloadVideo(this, videoUri)
                }
                showDownloadProgress()
            }
        }
    }

    private fun showCompletedDownloadWidgets() {
        setUpWidgetsForDifferentStates(binding.completedDownloadWidgets())
        viewModel.playDownloadedVideo(isPlayerPlaying, playbackPosition)
    }

    // TODO: Check also Internet connection because there is no error when
    // TODO: it became offline before or during downloading
    private fun showGeneralErrorDownloadWidgets() {
        setUpWidgetsForDifferentStates(binding.generalErrorDownloadWidgets())
    }

    private fun showWifiErrorDownloadingWidgets() {
        setUpWidgetsForDifferentStates(binding.wifiErrorDownloadWidgets())
    }

    // TODO: Show placeholder on Player screen when nothing is downloaded yet
    private fun showNotStartedDownloadWidgets() {

        setUpWidgetsForDifferentStates(binding.notStartedDownloadWidgets())
//
//        for (widget in binding.notStartedDownloadWidgets()) {
//            widget.isVisible = true
//        }
//
//        val listOfNonVisibleWidgets = binding.allChangeableWidgets()
//            .minus(binding.notStartedDownloadWidgets())
//
//        for (widget in listOfNonVisibleWidgets) {
//            widget.isVisible = false
//        }
    }

    private fun showSuitableUiState() {
        when (viewModel.downloadState.value) {
            ViewModel.DownloadState.NotStarted -> {
                showNotStartedDownloadWidgets()
            }
            ViewModel.DownloadState.Removing -> {
                showNotStartedDownloadWidgets()
            }
            ViewModel.DownloadState.SomethingIsDownloading -> {
                showStartedDownloadWidgets()
            }
            ViewModel.DownloadState.SomethingIsDownloaded -> {
                showCompletedDownloadWidgets()
            }
            ViewModel.DownloadState.Queued -> {
                // Downloading is paused or is already started
                showPausedDownloadWidgets()
            }
            ViewModel.DownloadState.Downloading -> {
                //viewModel.setStopReasonDuringDownloading(this, videoUri)
                showStartedDownloadWidgets()
            }
            ViewModel.DownloadState.Restarting -> {
                showStartedDownloadWidgets()
            }
            ViewModel.DownloadState.Completed -> {
                Timber.d("Completed download")
                showCompletedDownloadWidgets()
            }
            ViewModel.DownloadState.Failed -> {
                //viewModel.setStopReasonDuringDownloading(this, videoUri)
                showGeneralErrorDownloadWidgets()

            }
            else -> {
                // TODO: The same as for NotStarted
            }
        }
    }

//    override fun onStart() {
//        super.onStart()
//        if (Build.VERSION.SDK_INT > 23) {
//            viewModel.preparePlayer()
//            binding.playerView.onResume()
//        }
//    }
//
//    override fun onResume() {
//        super.onResume()
//        if (Build.VERSION.SDK_INT <= 23) {
//            viewModel.preparePlayer()
//            binding.playerView.onResume()
//        }
//    }
//
//    override fun onPause() {
//        super.onPause()
//        if (Build.VERSION.SDK_INT <= 23) {
//            isPlayerPlaying = viewModel.isPlayerPlaying()
//            playbackPosition = viewModel.getPlaybackPosition()
//            viewModel.releasePlayer()
//            binding.playerView.onPause()
//        }
//    }
//
//    override fun onStop() {
//        super.onStop()
//        if (Build.VERSION.SDK_INT > 23) {
//            isPlayerPlaying = viewModel.isPlayerPlaying()
//            playbackPosition = viewModel.getPlaybackPosition()
//            viewModel.releasePlayer()
//            binding.playerView.onPause()
//        }
//    }
}