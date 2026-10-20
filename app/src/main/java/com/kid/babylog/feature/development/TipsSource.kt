package com.kid.babylog.feature.development

import android.content.Context

/** Откуда раздел «Развитие» берёт советы и окна навыков (ADR-0037); в тестах — свои списки. */
interface TipsSource {
    fun tips(): List<Tip>

    fun milestones(): MilestoneTable
}

/**
 * Файлы `assets/development/`: разбираются один раз, при первом обращении. Чтение и разбор — работа с
 * диском, вызывай не с главного потока.
 */
class AssetTipsSource(context: Context) : TipsSource {

    private val assets = context.applicationContext.assets

    private val tips by lazy { parseTips(read(TIPS_FILE)) }

    private val milestones by lazy { parseMilestones(read(MILESTONES_FILE)) }

    override fun tips(): List<Tip> = tips

    override fun milestones(): MilestoneTable = milestones

    private fun read(name: String): String = assets.open(name).bufferedReader().use { it.readText() }

    companion object {
        const val TIPS_FILE = "development/tips_ru.json"
        const val MILESTONES_FILE = "development/who_motor_milestones.json"
    }
}
