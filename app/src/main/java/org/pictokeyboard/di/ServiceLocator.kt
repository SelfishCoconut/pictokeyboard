package org.pictokeyboard.di

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import org.pictokeyboard.BuildConfig
import org.pictokeyboard.data.arasaac.ArasaacApi
import org.pictokeyboard.data.arasaac.ArasaacRepository
import org.pictokeyboard.data.arasaac.ImageCache
import org.pictokeyboard.data.backup.BackupManager
import org.pictokeyboard.data.db.AppDatabase
import org.pictokeyboard.data.pkb.PkbBackup
import org.pictokeyboard.data.prefs.SettingsStore
import org.pictokeyboard.data.repo.PictoRepository
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Lightweight manual dependency container. Avoids the build complexity of a DI
 * framework while keeping a single shared graph for the activity and the IME.
 */
class ServiceLocator(context: Context) {

    /**
     * Application context. Exposed because the first board is named after the
     * app, so seeding needs to resolve a string resource — the same name the
     * v3→v4 migration writes, read from the same place rather than duplicated
     * as a literal.
     */
    val appContext: Context = context.applicationContext

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val arasaacApi: ArasaacApi = Retrofit.Builder()
        .baseUrl("https://api.arasaac.org/")
        .client(httpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(ArasaacApi::class.java)

    private val db = AppDatabase.get(appContext)

    val imageCache = ImageCache(appContext, httpClient)

    val settings = SettingsStore(appContext)

    val arasaacRepository = ArasaacRepository(arasaacApi)

    val pictoRepository = PictoRepository(
        boardDao = db.boardDao(),
        categoryDao = db.categoryDao(),
        pictoDao = db.pictoDao(),
        usageDao = db.usageDao(),
        imageCache = imageCache,
    )

    val backupManager = BackupManager(
        categoryDao = db.categoryDao(),
        pictoDao = db.pictoDao(),
        imageCache = imageCache,
        moshi = moshi,
    )

    /**
     * The backup, whole-device or one board (#88, #119), as opposed to
     * [backupManager]'s legacy one-board JSON. This is the one that carries the
     * photographs, and it is the **only** backup a caregiver has: nothing in
     * this app goes to a server, so a board that was never exported exists on
     * exactly one phone.
     */
    val pkbBackup = PkbBackup(
        db = db,
        settingsStore = settings,
        imageCache = imageCache,
        appVersion = BuildConfig.VERSION_NAME,
    )
}
