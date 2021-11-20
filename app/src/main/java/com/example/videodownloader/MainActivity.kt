package com.example.videodownloader

import android.app.Application
import android.content.res.Configuration
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.net.toUri
import androidx.core.view.*
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
    private lateinit var viewModelFactory: MainViewModelFactory
    //private var videoUri: Uri = "".toUri()

    private lateinit var mainViewModel: MainViewModel

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

        //Timber.d("OnCreate: Video uri is $videoUri")

        viewModelFactory = MainViewModelFactory(Application(), this)

        mainViewModel = ViewModelProvider(this, viewModelFactory).get(MainViewModel::class.java)


        //mainViewModel = ViewModelProvider(this).get(MainViewModel::class.java)

        mainViewModel.preparePlayer()

        lifecycleScope.launchWhenStarted {
            mainViewModel.isFullscreenOn.collectLatest { }
        }

        lifecycleScope.launchWhenStarted {
            mainViewModel.downloadState.collectLatest {
                showSuitableUiState()
            }
        }

        lifecycleScope.launchWhenStarted {
            mainViewModel.currentUri.collectLatest {
                Timber.d("mainViewModel.currentUri.value is ${mainViewModel.currentUri.value}")
               // binding.inputUri.text = SpannableStringBuilder(viewModel.currentUri.value.toString())
            }
        }

        // TODO: Probably you should keep them in onSaveStateHandle or don't keep at all
        if (savedInstanceState != null) {
            playbackPosition = savedInstanceState.getLong(STATE_RESUME_POSITION)
            isPlayerPlaying = savedInstanceState.getBoolean(STATE_PLAYER_PLAYING)
        }

        constraints = ConstraintSet()


        Timber.d("Download state is ${mainViewModel.downloadState.value}")

        // Get player instance
        binding.playerView.player = mainViewModel.exoPlayer

        binding.setUpAutoCompleteView()

        binding.downloadButton.setOnClickListener {
            showStartedDownloadWidgets()
            downloadVideo()
        }

        binding.clearButton.setOnClickListener {
            mainViewModel.clearAllDownloadedVideos(this)
            mainViewModel.pausePlayer()
        }

        binding.pauseButton.setOnClickListener {
            mainViewModel.pauseDownloading(this, mainViewModel.currentUri.value)
        }

        binding.resumeButton.setOnClickListener {
            if (checkIsConnectedToWifi()) {
                mainViewModel.resumeDownloading(this, mainViewModel.currentUri.value)
            } else {
                showErrorWifiResumeWidgets()
            }

        }

        binding.retryButton.setOnClickListener {
           // downloadVideo()
            retryDownload()
        }

        binding.retryButtonNoWifi.setOnClickListener {
            //downloadVideo()
            retryDownload()
        }

        binding.tryAnotherLinkButton.setOnClickListener {
            showNotStartedDownloadWidgets()
        }

        binding.setUpVideoFullscreenViewButtons()





        setContentView(binding.root)
    }

    override fun onStart() {
        super.onStart()
       // Timber.d("OnStart: Video uri is $videoUri")
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
        tryAnotherLinkButton,
        retryButtonNoWifi,
        errorWifiResume
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
        retryButtonNoWifi,
        emptyPlayerPlaceholder,
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
        errorWifiResume,
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
            if (!mainViewModel.isFullscreenOn.value) {
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
        // TODO: Fix bug^ sometimes X is not displayed when there is text in the field
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

        // Initially show clear button only if there are any text in input view
        clearInputUri.isVisible = inputUri.editableText.isNotEmpty()

        inputUri.addTextChangedListener(
            textWatcherForClearingTextButton(clearInputUri)
        )

    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        Timber.d("onConfigurationChanged: Video uri is ${binding.inputUri.editableText}")

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (mainViewModel.isFullscreenOn.value) {
                setFullScreen()
            }

        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (mainViewModel.isFullscreenOn.value) {
                removeStatusBar()
                ViewCompat.setOnApplyWindowInsetsListener(controller) { view, insets ->
                    view.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
                    insets
                }
            }
        }
    }

    private fun openPlayerInFullscreen() {
        mainViewModel.setIsFullscreenOn(true)
        constraints.apply {
            clone(binding.mainLayout)
            connect(R.id.player_frame, ConstraintSet.TOP, R.id.main_layout, ConstraintSet.TOP, 0)
            setDimensionRatio(R.id.player_frame, null)
            setMargin(R.id.player_frame, ConstraintSet.BOTTOM, 0)
            setMargin(R.id.player_frame, ConstraintSet.START, 0)
            setMargin(R.id.player_frame, ConstraintSet.END, 0)
            applyTo(binding.mainLayout)
        }

        binding.root.setBackgroundColor(Color.BLACK)

        setUpWidgetsForDifferentStates(fullscreenVideoWidgets())

        fullscreenButton.setImageResource(R.drawable.ic_baseline_fullscreen_exit_36)

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
    }

    private fun returnFromPlayerInFullscreen() {
        mainViewModel.setIsFullscreenOn(false)
        val margin = convertDpToPixels(this, 16)
        constraints.apply {
            clone(binding.mainLayout)
            connect(R.id.player_frame, ConstraintSet.TOP, R.id.guideline, ConstraintSet.BOTTOM, 0)
            connect(R.id.player_frame, ConstraintSet.START, R.id.main_layout, ConstraintSet.START, margin)
            connect(R.id.player_frame, ConstraintSet.END, R.id.main_layout, ConstraintSet.END, margin)
            connect(R.id.player_frame, ConstraintSet.BOTTOM, R.id.main_layout, ConstraintSet.BOTTOM, margin)
            setDimensionRatio(R.id.player_frame, "16:9")
            constrainWidth(R.id.player_frame, 0)
            constrainHeight(R.id.player_frame, 0)
            applyTo(binding.mainLayout)
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

    // TODO CheckForEmptiness
    private fun showDownloadProgress() {

        //videoUri = binding.inputUri.editableText.toString().toUri()
       // val gotUri = videoUri ?: binding.inputUri.editableText.toString().toUri()
        Timber.d("videoUri in showDownloadProgress() is ${mainViewModel.currentUri.value}")
            lifecycleScope.launch {
                mainViewModel.downloadState.collectLatest {
                    mainViewModel.getDownloadedBytes(mainViewModel.currentUri.value).collectLatest {
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

    // TODO: When proxy is turned on sometimes app stacks at QUEUED state. Make a timeout and return failed at thate case!
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
        if (checkIsConnectedToWifi()) {
                /*
            Show an error under view for video link if it remained empty after clicking Download.
            Don't start downloading at that case.
             */
                if (!checkIsEmptyAutoCompleteView()) {

                    Timber.d("Don't have failed downloads. Last uri is ${mainViewModel.currentUri.value}")
                    //TODO: Check if something in the cache before clearing!!! And actually you don't use downloading failed videos!
                    // TODO: It doesn't work correctly if I changed link. It still tries to download failed video!!!
                    mainViewModel.clearAllDownloadedVideos(this)

                    mainViewModel.updateCurrentUri(addUriSchemaIfNecessary(mainViewModel.currentUri.value))
                    Timber.d("videoUri after calling addUriSchemaIfNecessary() is ${mainViewModel.currentUri.value}")
                    //isConnected?

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
   // }

//    private fun downloadWithUpdatingCurrentUri() {
//        //mainViewModel.updateCurrentUri(binding.inputUri.editableText.toString().toUri())
//        Timber.d("Video uri is ${mainViewModel.currentUri.value} after updating it in downloadWithUpdatingCurrentUri()")
//        downloadWithoutUpdatingCurrentUri()
//    }




//    private fun downloadVideo() {
////        if (binding.inputUri.editableText.isNotEmpty()) {
////            viewModel.updateCurrentUri(binding.inputUri.editableText.toString().toUri())
////        }
//
//    }

    private fun showCompletedDownloadWidgets() {
        setUpWidgetsForDifferentStates(binding.completedDownloadWidgets())
        if (mainViewModel.checkHaveCompletelyDownloadedVideo()) {
            mainViewModel.playDownloadedVideo(isPlayerPlaying, playbackPosition)
        } else {
            showGeneralErrorDownloadWidgets()
        }

    }

    // TODO: Check also Internet connection because there is no error when
    // TODO: it became offline before or during downloading
    private fun showGeneralErrorDownloadWidgets() {
        setUpWidgetsForDifferentStates(binding.generalErrorDownloadWidgets())
    }

    private fun showWifiErrorDownloadingWidgets() {
        Timber.d("showWifiErrorDownloadingWidgets() is called")
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
        when (mainViewModel.downloadState.value) {
            MainViewModel.DownloadState.NotStarted -> {
                showNotStartedDownloadWidgets()
            }
            MainViewModel.DownloadState.Removing -> {
                showNotStartedDownloadWidgets()
            }
//            ViewModel.DownloadState.SomethingIsDownloading -> {
//                showStartedDownloadWidgets()
//            }
//            ViewModel.DownloadState.SomethingIsDownloaded -> {
//                showCompletedDownloadWidgets()
//            }
            MainViewModel.DownloadState.Queued -> {
                // Downloading is already started (cannot be paused because I use sendStopReason() instead of pause())
                showStartedDownloadWidgets()
                showDownloadProgress()
            }
            MainViewModel.DownloadState.Downloading -> {
                Timber.d("ViewModel.DownloadState.Downloading")
                //viewModel.setStopReasonDuringDownloading(this, videoUri)
                showStartedDownloadWidgets()
                showDownloadProgress()
            }
            MainViewModel.DownloadState.Restarting -> {
                Timber.d("ViewModel.DownloadState.Restarting")
                showStartedDownloadWidgets()
            }
            MainViewModel.DownloadState.Completed -> {
                Timber.d("Completed download")
                showCompletedDownloadWidgets()
            }
            MainViewModel.DownloadState.Failed -> {
                // TODO: Probably should not call this
               // viewModel.setStopReasonDuringDownloading(this, viewModel.currentUri.value)
                showGeneralErrorDownloadWidgets()

            }
            MainViewModel.DownloadState.Stopped -> {
                showPausedDownloadWidgets()
            }
        }
    }

    //TODO: In Dark mode download progress is not visible!

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