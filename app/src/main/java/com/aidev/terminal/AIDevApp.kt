package com.aidev.terminal

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * AIDev Application 类。
 * 通过 ActivityLifecycleCallbacks 跟踪当前前台 Activity，替代反射方案。
 */
class AIDevApp : Application() {

    companion object {
        @Volatile
        private var currentActivity: Activity? = null

        /** 获取当前前台 Activity（线程安全） */
        fun getCurrentActivity(): Activity? = currentActivity
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                currentActivity = activity
            }
            override fun onActivityPaused(activity: Activity) {
                if (currentActivity == activity) {
                    currentActivity = null
                }
            }
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivity == activity) {
                    currentActivity = null
                }
            }
        })
    }
}
