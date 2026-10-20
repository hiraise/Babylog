package com.kid.babylog.feature.development

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kid.babylog.core.data.DevelopmentSeenStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** Советы и окна навыков из файлов приложения (ADR-0037). */
data class DevelopmentData(val tips: List<Tip>, val milestones: MilestoneTable)

/**
 * Раздел «Развитие» (ADR-0037): читает советы и окна навыков с диска в фоне и помнит, какую неделю
 * жизни раздел уже открывали у каждого ребёнка. Что показать на неделе — чистая [developmentWeek].
 *
 * @param io поток для чтения файлов; в тестах — `Dispatchers.Unconfined`, чтобы данные были сразу.
 */
class DevelopmentViewModel(
    source: TipsSource,
    private val seen: DevelopmentSeenStore,
    io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    /** null — файлы ещё читаются. */
    val data: StateFlow<DevelopmentData?> =
        flow { emit(DevelopmentData(source.tips(), source.milestones())) }
            .flowOn(io)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Последняя открытая неделя жизни по id ребёнка. */
    val seenWeeks: StateFlow<Map<Long, Int>> = seen.seenWeeks

    fun markSeen(childId: Long, week: Int) = seen.markSeen(childId, week)
}
