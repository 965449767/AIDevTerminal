package com.aidev.terminal

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * AIDev Application 类。
 * 通过 ActivityLifecycleCallbacks 跟踪当前前台 Activity，替代反射方案。
 */
class AIDevApp : Application() {

    companion object {
        @Volatile
        private var currentActivityRef: WeakReference<Activity>? = null

        /** 获取当前前台 Activity（线程安全） */
        fun getCurrentActivity(): Activity? = currentActivityRef?.get()
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                currentActivityRef = WeakReference(activity)
            }
            override fun onActivityPaused(activity: Activity) {
                if (currentActivityRef?.get() == activity) {
                    currentActivityRef = null
                }
            }
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivityRef?.get() == activity) {
                    currentActivityRef = null
                }
            }
        })
    }
}
