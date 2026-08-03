package org.pictokeyboard.di

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.pictokeyboard.BuildConfig
import org.pictokeyboard.data.arasaac.ArasaacApi
import org.pictokeyboard.data.arasaac.ArasaacRepository
import org.pictokeyboard.data.arasaac.ImageCache
import org.pictokeyboard.data.auth.AuthRepository
import org.pictokeyboard.data.auth.SupabaseConfig
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
     * The whole-device backup (#88), as opposed to [backupManager]'s one-board
     * JSON. This is the one that carries the photographs, and the only backup
     * a caregiver has — nothing goes to a server.
     */
    val pkbBackup = PkbBackup(
        db = db,
        settingsStore = settings,
        imageCache = imageCache,
        appVersion = BuildConfig.VERSION_NAME,
    )

    /**
     * Caregiver accounts (#79).
     *
     * On its own scope rather than a screen's, because the session flow has to
     * outlive any one screen. Constructed even on a build with no credentials,
     * where it holds no client and reports `Unavailable` forever.
     *
     * The IME shares this locator, so this property *existing* is not the same
     * as the keyboard using it — and nothing under `ime/` may touch it.
     * `ImeHasNoSupabaseTest` proves none does.
     */
    val authRepository = AuthRepository(
        config = SupabaseConfig.fromBuildConfig(),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )
}
