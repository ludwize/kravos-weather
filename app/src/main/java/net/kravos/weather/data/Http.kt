package net.kravos.weather.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpException(val code: Int, val body: String) : IOException("HTTP $code: ${body.take(300)}")

object Http {
    const val TAG = "KravosWeather"
    const val USER_AGENT = "KravosWeather/1.0 (personal app; net.kravos.weather)"

    @Volatile private var client: OkHttpClient? = null

    fun init(context: Context) {
        if (client != null) return
        client = OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "http"), 40L * 1024 * 1024))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
            }
            .build()
    }

    fun client(): OkHttpClient = client ?: OkHttpClient()

    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).build()
        client().newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                Log.w(TAG, "GET ${url.take(160)} -> ${resp.code} ${body.take(300)}")
                throw HttpException(resp.code, body)
            }
            Log.i(TAG, "GET ${url.take(160)} -> ${resp.code} (${body.length} b) ${body.take(220).replace('\n', ' ')}")
            body
        }
    }

    suspend fun bytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            client().newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.bytes() else null
            }
        } catch (e: IOException) {
            null
        }
    }
}
