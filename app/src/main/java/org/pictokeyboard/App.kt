package org.pictokeyboard

import android.app.Application
import org.pictokeyboard.di.ServiceLocator

class App : Application() {

    lateinit var locator: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        locator = ServiceLocator(this)
    }

    companion object {
        lateinit var instance: App
            private set

        fun locator(): ServiceLocator = instance.locator
    }
}
