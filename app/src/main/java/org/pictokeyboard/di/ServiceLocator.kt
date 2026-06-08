package org.pictokeyboard.di

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import org.pictokeyboard.data.arasaac.ArasaacApi
import org.pictokeyboard.data.arasaac.ArasaacRepository
import org.pictokeyboard.data.arasaac.ImageCache
import org.pictokeyboard.data.backup.BackupManager
import org.pictokeyboard.data.db.AppDatabase
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

    private val appContext = context.applicationContext

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
}
