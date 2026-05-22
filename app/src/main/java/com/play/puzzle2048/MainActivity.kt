package com.play.puzzle2048

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private var interstitialAd: InterstitialAd? = null
    private lateinit var appOpenAdManager: AppOpenAdManager
    private lateinit var appUpdateManager: AppUpdateManager
    private val updateLauncher: ActivityResultLauncher<IntentSenderRequest> =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode != RESULT_OK) {
                // If the update is cancelled or fails, you can decide whether to exit or allow the user to continue.
                // For IMMEDIATE updates, usually we check again onResume.
            }
        }

    private var webView: WebView? = null
    private var isWebViewReady by mutableStateOf(false)

    private lateinit var soundPool: SoundPool
    private var soundMergeId: Int = 0
    private var soundWinnerId: Int = 0
    private var soundMilestoneId: Int = 0
    private var soundGameOverId: Int = 0
    private var soundsEnabled: Boolean = true
    private var isPageFinishedLoading by mutableStateOf(false)
    private var webViewProgress by mutableStateOf(0.1f)
    private var backPressedTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemUI()

        // Set content as early as possible to show the loading bar immediately
        setContent {
            MainScreen()
        }

        // Initialize services in background
        MobileAds.initialize(this) {}
        appOpenAdManager = AppOpenAdManager(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(appOpenAdManager)
        appUpdateManager = AppUpdateManagerFactory.create(this)
        checkForUpdates()
        loadInterstitialAd()
        initSoundPool()

        // Back button handling
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView?.evaluateJavascript("typeof handleBack === 'function' && handleBack()") { result ->
                    if (result != "true") {
                        if (backPressedTime + 2000 > System.currentTimeMillis()) {
                            finish()
                        } else {
                            Toast.makeText(this@MainActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
                            backPressedTime = System.currentTimeMillis()
                        }
                    }
                }
            }
        })
    }

    private fun hideSystemUI() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun checkForUpdates() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    updateLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()

        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    updateLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                )
            }
        }
    }

    @Composable
    fun MainScreen() {
        // Start showing some progress immediately and keep it moving slowly
        LaunchedEffect(isWebViewReady) {
            while (!isWebViewReady && webViewProgress < 0.9f) {
                delay(100)
                if (webViewProgress < 0.9f) {
                    webViewProgress += 0.005f // Steady crawl while waiting
                }
            }
        }

        // Wait for both progress to be 100% and JS to signal readiness
        LaunchedEffect(isPageFinishedLoading, webViewProgress) {
            if (isPageFinishedLoading && webViewProgress >= 1f && !isWebViewReady) {
                delay(1000) // Ensure the 800ms tween to 100% has finished visually
                window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.parseColor("#000000")))
                isWebViewReady = true
            }
        }


        // Use a transparent Surface so the windowBackground (with the black track) shows through
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val screenHeight = maxHeight
                // Calculate thickness based on the 360x640 viewport used in splash_background.xml
                // Matching the 20dp strokeWidth from the XML for a perfect fit
                val barThickness = screenHeight * (20f / 640f)

                if (!isWebViewReady) {
                    val animatedProgress by animateFloatAsState(
                        targetValue = webViewProgress,
                        animationSpec = tween(durationMillis = 800), // Predictable duration
                        label = "loadingProgress"
                    )

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = BiasAlignment(0f, 0.7742f) // Aligns center of 20dp bar at y=560 in 640dp viewport
                    ) {
                        // The track (background) of the loading bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(292f / 360f)
                                .height(barThickness)
                                .background(Color.Black, shape = CircleShape)
                        )

                        // The actual progress filling the track
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(292f / 360f)
                                .height(barThickness)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedProgress)
                                    .fillMaxHeight()
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                Color(0xFFFF5252), // Coral Red
                                                Color(0xFFFFD740), // Amber
                                                Color(0xFF69F0AE), // Spring Green
                                                Color(0xFF40C4FF), // Light Blue
                                                Color(0xFFE040FB)  // Orchid
                                            ),
                                            start = Offset(0f, 0f),
                                            end = Offset(Float.POSITIVE_INFINITY, 0f)
                                        ),
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }

                // Main content is hidden via alpha until ready.
                // Using alpha(0) keeps the WebView alive and loading while the splash stays visible.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (isWebViewReady) 1f else 0f)
                ) {
                    // WebView with fixed layout parameters
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                                setBackgroundColor(android.graphics.Color.parseColor("#000000"))

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true

                                    // Proper scaling for the responsive game
                                    useWideViewPort = true
                                    loadWithOverviewMode = true

                                    setSupportZoom(false)
                                    builtInZoomControls = false
                                    displayZoomControls = false

                                    cacheMode = WebSettings.LOAD_DEFAULT
                                }

                                isHapticFeedbackEnabled = true
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                    }
                                }
                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        val progress = newProgress / 100f
                                        // Cap web progress at 0.9 to let JS signal the final completion
                                        val cappedProgress = if (progress >= 1f) 0.9f else progress
                                        if (cappedProgress > webViewProgress) {
                                            webViewProgress = cappedProgress
                                        }
                                    }
                                }
                                addJavascriptInterface(WebAppInterface(this@MainActivity), "AndroidInterface")
                                loadUrl("file:///android_asset/index.html")
                                this@MainActivity.webView = this
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )

                    // Dedicated Banner Ad area with solid black background
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { context ->
                                AdView(context).apply {
                                    setAdSize(AdSize.BANNER)
                                    adUnitId = "ca-app-pub-3940256099942544/6300978111"
                                    loadAd(AdRequest.Builder().build())
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    private fun initSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()

        soundMergeId = soundPool.load(this, R.raw.merge, 1)
        soundWinnerId = soundPool.load(this, R.raw.winner, 1)
        soundMilestoneId = soundPool.load(this, R.raw.milestone, 1)
        soundGameOverId = soundPool.load(this, R.raw.out_of_moves, 1)

        val sharedPref = getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
        soundsEnabled = sharedPref.getBoolean("sounds_enabled", true)
    }

    fun playNativeSound(soundId: Int, volume: Float = 1.0f) {
        if (soundsEnabled && soundId != 0) {
            // Priority 1 for game sounds, and remove runOnUiThread for lower latency
            soundPool.play(soundId, volume, volume, 1, 0, 1f)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPool.release()
    }

    fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, "ca-app-pub-3940256099942544/1033173712", adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    interstitialAd = null
                }
            })
    }

    fun showInterstitialAd() {
        if (interstitialAd != null) {
            interstitialAd?.show(this)
            loadInterstitialAd() // Load next
        } else {
            loadInterstitialAd()
        }
    }

    inner class WebAppInterface(private val mContext: Context) {
        @JavascriptInterface
        fun onPageReady() {
            (mContext as? Activity)?.runOnUiThread {
                webViewProgress = 1f
                isPageFinishedLoading = true
            }
        }

        @JavascriptInterface
        fun vibrate(duration: Long) {
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = mContext.getSystemService(VibratorManager::class.java)
                    vibratorManager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    mContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }

                if (vibrator == null || !vibrator.hasVibrator()) return

                // Standardize short vibrations to be more noticeable
                val effectiveDuration = if (duration > 0 && duration < 20) 20L else duration

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val effect = when {
                        effectiveDuration <= 60 -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                        effectiveDuration <= 120 -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                        else -> VibrationEffect.createOneShot(effectiveDuration, 255) // Max amplitude for "strong" feedback
                    }
                    vibrator.vibrate(effect)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val amplitude = if (effectiveDuration > 120) 255 else VibrationEffect.DEFAULT_AMPLITUDE
                    vibrator.vibrate(VibrationEffect.createOneShot(effectiveDuration, amplitude))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(effectiveDuration)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        @JavascriptInterface
        fun showInterstitialAd() {
            (mContext as? Activity)?.runOnUiThread {
                (mContext as MainActivity).showInterstitialAd()
            }
        }

        @JavascriptInterface
        fun shareApp() {
            val shareMessage = """
                🎨 Get ready to beat my score in 2048 Brain Puzzle! 🧩✨
                
                Download 2048 Brain Puzzle from official Google Play Store
                👇
                https://play.google.com/store/apps/details?id=${mContext.packageName}
            """.trimIndent()

            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareMessage)
                type = "text/plain"
            }
            mContext.startActivity(Intent.createChooser(sendIntent, "Share 2048 Brain Puzzle"))
        }

        @JavascriptInterface
        fun rateApp() {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=${mContext.packageName}")
                setPackage("com.android.vending")
            }
            try {
                mContext.startActivity(intent)
            } catch (e: Exception) {
                mContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${mContext.packageName}")))
            }
        }

        @JavascriptInterface
        fun saveData(key: String, value: String) {
            val sharedPref = mContext.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putString(key, value)
                if (key == "2048-sounds") {
                    soundsEnabled = value == "true"
                    putBoolean("sounds_enabled", soundsEnabled)
                }
                apply()
            }
        }

        @JavascriptInterface
        fun playSound(type: String) {
            var volume = 1.0f
            val soundId = when (type) {
                "merge" -> {
                    volume = 1.0f
                    soundMergeId
                }
                "winner" -> {
                    volume = 0.6f
                    soundWinnerId
                }
                "milestone" -> {
                    volume = 0.35f
                    soundMilestoneId
                }
                "gameover" -> {
                    volume = 0.6f
                    soundGameOverId
                }
                else -> 0
            }
            if (soundId != 0) {
                (mContext as? MainActivity)?.playNativeSound(soundId, volume)
            }
        }

        @JavascriptInterface
        fun loadData(key: String, defaultValue: String): String {
            val sharedPref = mContext.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
            return sharedPref.getString(key, defaultValue) ?: defaultValue
        }
    }
}

class AppOpenAdManager(private val activity: Activity) : DefaultLifecycleObserver {

    private var appOpenAd: AppOpenAd? = null
    private var isShowingAd = false
    private var isForeground = false
    private val launchTime = System.currentTimeMillis()

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        isForeground = true
        showAdIfAvailable()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        isForeground = false
    }

    private fun showAdIfAvailable() {
        if (isShowingAd) return
        if (activity.isFinishing || activity.isDestroyed) return

        // Prevent showing ads for the first 15 seconds after app launch
        val millisSinceLaunch = System.currentTimeMillis() - launchTime
        if (millisSinceLaunch < 15000) {
            if (appOpenAd == null) {
                fetchAd(showImmediately = false)
            }
            return
        }

        if (appOpenAd == null) {
            fetchAd(showImmediately = true)
            return
        }

        appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowingAd = false
                fetchAd(showImmediately = false)
            }

            override fun onAdShowedFullScreenContent() {
                isShowingAd = true
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                appOpenAd = null
                isShowingAd = false
                fetchAd(showImmediately = false)
            }
        }
        try {
            appOpenAd?.show(activity)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun fetchAd(showImmediately: Boolean) {
        val request = AdRequest.Builder().build()
        AppOpenAd.load(
            activity,
            "ca-app-pub-3940256099942544/9257395921",
            request,
            AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    if (showImmediately && isForeground) {
                        showAdIfAvailable()
                    }
                }
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {}
            }
        )
    }
}