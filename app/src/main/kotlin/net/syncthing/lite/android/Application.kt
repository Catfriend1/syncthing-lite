package net.syncthing.lite.android

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.security.Security
import net.syncthing.lite.error.ErrorStorage
import org.bouncycastle.jce.provider.BouncyCastleProvider

class Application: Application() {
    companion object {
        private const val LOG_TAG = "Application"
        private val handler = Handler(Looper.getMainLooper())
    }

    override fun onCreate() {
        super.onCreate()

        val provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
        if (provider == null || provider.javaClass != BouncyCastleProvider::class.java) {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        val mainThread = Thread.currentThread()

        if (defaultHandler == null) {
            Log.w(LOG_TAG, "could not get default crash handler")
        }

        fun handleCrash(ex: Throwable) {
            Log.w(LOG_TAG, "app crashed", ex)

            ErrorStorage.reportError(
                    this,
                    Log.getStackTraceString(ex)
            )

            if (defaultHandler != null) {
                defaultHandler.uncaughtException(mainThread, ex)
            } else {
                System.exit(1)
            }
        }

        Thread.setDefaultUncaughtExceptionHandler { _, ex ->
            if (Looper.getMainLooper() === Looper.myLooper()) {
                handleCrash(ex)
            } else {
                handler.post { handleCrash(ex) }
            }
        }
    }
}
