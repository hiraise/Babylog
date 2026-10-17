package com.kid.babylog.testing

import com.github.takahirom.roborazzi.RoborazziOptions

/**
 * Параметры сверки эталонных скриншотов (ADR-0013). Mac (эталоны) и Linux (CI) сглаживают шрифты
 * чуть по-разному: допускаем 1 % отличающихся пикселей.
 */
val SCREENSHOT_OPTIONS = RoborazziOptions(
    compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
)
