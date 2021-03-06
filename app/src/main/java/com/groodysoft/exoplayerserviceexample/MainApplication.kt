package com.groodysoft.exoplayerserviceexample

import android.app.Application
import com.google.gson.Gson

class MainApplication : Application() {

    init {
        instance = this
    }

    companion object {
        var instance: MainApplication? = null

        val context : MainApplication
            get() {
                return instance as MainApplication
            }

        val gson : Gson
            get() {
                return Gson()
            }
    }
}