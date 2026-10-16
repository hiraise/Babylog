package com.kid.babylog

import android.app.Application

class App : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
