package net.meshnet.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Application entry point.
 *
 * @HiltAndroidApp triggers Hilt's code generation and installs the
 * application-level component as the root of the dependency graph.
 */
@HiltAndroidApp
class MeshNetApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initLogging()
    }

    private fun initLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Production: plant a non-crashlytics tree that strips PII from tags
        // (implemented in Phase 10 alongside release configuration)
    }
}
