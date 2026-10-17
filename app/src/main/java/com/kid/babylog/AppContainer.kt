package com.kid.babylog

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kid.babylog.core.data.DiaryRepository
import com.kid.babylog.core.data.PrefsSelectedChildStore
import com.kid.babylog.core.data.PrefsThemeModeStore
import com.kid.babylog.core.data.SelectedChildStore
import com.kid.babylog.core.data.ThemeModeStore
import com.kid.babylog.core.data.room.AppDatabase
import com.kid.babylog.core.data.room.RoomDiaryRepository
import com.kid.babylog.feature.diary.DiaryViewModel
import com.kid.babylog.feature.transfer.ContentResolverDocumentStore
import com.kid.babylog.feature.transfer.DocumentStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Ручная сборка зависимостей: весь граф приложения в одном месте, без DI-фреймворка (ADR-0007). */
class AppContainer(context: Context) {

    /** Системное время в текущем часовом поясе устройства (пояс может смениться на ходу). */
    val clock: Clock = SystemZoneClock

    private val database = AppDatabase.build(context)

    val repository: DiaryRepository = RoomDiaryRepository(database)

    val selectedChild: SelectedChildStore = PrefsSelectedChildStore(context)

    val themeMode: ThemeModeStore = PrefsThemeModeStore(context)

    val documents: DocumentStore = ContentResolverDocumentStore(context)

    val diaryViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { DiaryViewModel(repository, selectedChild, documents, clock) }
    }
}

private object SystemZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = system(zone)

    override fun instant(): Instant = Instant.now()

    override fun millis(): Long = System.currentTimeMillis()
}
