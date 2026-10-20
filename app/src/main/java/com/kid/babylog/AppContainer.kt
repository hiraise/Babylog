package com.kid.babylog

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kid.babylog.core.data.DevelopmentSeenStore
import com.kid.babylog.core.data.DiaryRepository
import com.kid.babylog.core.data.PrefsDevelopmentSeenStore
import com.kid.babylog.core.data.PrefsRecoveryNotices
import com.kid.babylog.core.data.PrefsSelectedChildStore
import com.kid.babylog.core.data.PrefsThemeModeStore
import com.kid.babylog.core.data.RecoveryNotices
import com.kid.babylog.core.data.SelectedChildStore
import com.kid.babylog.core.data.ThemeModeStore
import com.kid.babylog.core.data.room.AppDatabase
import com.kid.babylog.core.data.room.RoomDiaryRepository
import com.kid.babylog.core.ui.photo.sweepPhotoCache
import com.kid.babylog.feature.development.AssetTipsSource
import com.kid.babylog.feature.development.TipsSource
import com.kid.babylog.feature.diary.DiaryViewModel
import com.kid.babylog.feature.transfer.ContentResolverDocumentStore
import com.kid.babylog.feature.transfer.DocumentStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor

/** Ручная сборка зависимостей: весь граф приложения в одном месте, без DI-фреймворка (ADR-0007). */
class AppContainer(context: Context) {

    /** Системное время в текущем часовом поясе устройства (пояс может смениться на ходу). */
    val clock: Clock = SystemZoneClock

    /** Сообщение о восстановлении повреждённой базы (ADR-0033): показывает `MainActivity`. */
    val recoveryNotices: RecoveryNotices = PrefsRecoveryNotices(context)

    private val database = AppDatabase.build(context, recoveryNotices, Dispatchers.IO.asExecutor(), clock::millis)

    init {
        // Снимки и копии фото, не дошедшие до базы, и копии для «Поделиться» (ADR-0024)
        sweepPhotoCache(context, clock.millis())
    }

    val repository: DiaryRepository = RoomDiaryRepository(database)

    val selectedChild: SelectedChildStore = PrefsSelectedChildStore(context)

    val themeMode: ThemeModeStore = PrefsThemeModeStore(context)

    val documents: DocumentStore = ContentResolverDocumentStore(context)

    /** Советы и окна навыков раздела «Развитие» (ADR-0037). */
    val tips: TipsSource = AssetTipsSource(context)

    val developmentSeen: DevelopmentSeenStore = PrefsDevelopmentSeenStore(context)

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
