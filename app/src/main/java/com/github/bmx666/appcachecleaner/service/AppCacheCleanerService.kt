package com.github.bmx666.appcachecleaner.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.github.bmx666.appcachecleaner.BuildConfig
import com.github.bmx666.appcachecleaner.R
import com.github.bmx666.appcachecleaner.clearcache.AccessibilityClearCacheManager
import com.github.bmx666.appcachecleaner.log.Logger
import com.github.bmx666.appcachecleaner.ui.view.AccessibilityOverlay
import com.github.bmx666.appcachecleaner.util.IIntentServiceCallback
import com.github.bmx666.appcachecleaner.util.IntentSettings
import com.github.bmx666.appcachecleaner.util.LocalBroadcastManagerServiceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppCacheCleanerService : AccessibilityService(), IIntentServiceCallback {

    companion object {
        private val accessibilityClearCacheManager = AccessibilityClearCacheManager()
    }

    private val logger = Logger()

    private lateinit var accessibilityOverlay: AccessibilityOverlay
    private lateinit var localBroadcastManager: LocalBroadcastManagerServiceHelper

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG)
            logger.onCreate(cacheDir)

        localBroadcastManager = LocalBroadcastManagerServiceHelper(this, this)

        accessibilityOverlay = AccessibilityOverlay {
            accessibilityClearCacheManager.interrupt()
        }
    }

    override fun onDestroy() {
        if (BuildConfig.DEBUG)
            logger.onDestroy()

        localBroadcastManager.onDestroy()

        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            accessibilityClearCacheManager.checkEvent(event)
    }

    override fun onInterrupt() {
    }

    override fun onStopAccessibilityService() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        disableSelf()
    }

    override fun onSetSettings(intentSettings: IntentSettings?) {
        intentSettings ?: return

        accessibilityClearCacheManager.setSettings(
            intentSettings.scenario,
            AccessibilityClearCacheManager.Settings(
                clearCacheTextList =
                    ArrayList<CharSequence>().apply {
                        intentSettings.clearCacheTextList?.forEach { add(it) }
                        add(getText(R.string.clear_cache_btn_text))
                    },
                clearDataTextList =
                    ArrayList<CharSequence>().apply {
                        intentSettings.clearDataTextList?.forEach { add(it) }
                        add(getText(R.string.clear_user_data_text))
                    },
                storageTextList =
                    ArrayList<CharSequence>().apply {
                        intentSettings.storageTextList?.forEach { add(it) }
                        add(getText(R.string.storage_settings_for_app))
                        add(getText(R.string.storage_label))
                    },
                okTextList =
                    ArrayList<CharSequence>().apply {
                        intentSettings.okTextList?.forEach { add(it) }
                        add(getText(android.R.string.ok))
                    },
                maxWaitAppTimeout = intentSettings.maxWaitAppTimeout,
                maxWaitClearCacheButtonTimeout = intentSettings.maxWaitClearCacheButtonTimeout
            )
        )
    }

    override fun onClearCache(pkgList: ArrayList<String>?) {
        if (BuildConfig.DEBUG)
            logger.onClearCache()

        pkgList?.let{
            accessibilityOverlay.show(this)
            val pkgListSize = pkgList.size
            CoroutineScope(Dispatchers.IO).launch {
                accessibilityClearCacheManager.clearCacheApp(
                    pkgList,
                    { index: Int ->
                        accessibilityOverlay.updateCounter(index, pkgListSize)
                    },
                    localBroadcastManager::sendAppInfo,
                    localBroadcastManager::sendFinish)
            }
        } ?: localBroadcastManager.sendFinish(true)
    }

    override fun onCleanCacheFinish() {
        accessibilityOverlay.hide(this)
    }
}
