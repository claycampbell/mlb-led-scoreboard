package com.mlb.scoreboard.data

import com.mlb.scoreboard.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Repository that manages MLB data fetching and caching.
 */
class MlbRepository {

    private val api: MlbApi

    init {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.NONE
            })
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://statsapi.mlb.com/api/v1/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(MlbApi::class.java)
    }

    suspend fun getTodaySchedule(): List<ScheduleGame> = withContext(Dispatchers.IO) {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val today = dateFormat.format(Date())
            val response = api.getSchedule(date = today)
            response.dates?.firstOrNull()?.games ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getScheduleForDate(date: String): List<ScheduleGame> = withContext(Dispatchers.IO) {
        try {
            val response = api.getSchedule(date = date)
            response.dates?.firstOrNull()?.games ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getLiveFeed(gamePk: Int): LiveFeedResponse? = withContext(Dispatchers.IO) {
        try {
            api.getLiveFeed(gamePk)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getStandings(): StandingsResponse? = withContext(Dispatchers.IO) {
        try {
            val year = Calendar.getInstance().get(Calendar.YEAR)
            api.getStandings(season = year)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        @Volatile
        private var instance: MlbRepository? = null

        fun getInstance(): MlbRepository {
            return instance ?: synchronized(this) {
                instance ?: MlbRepository().also { instance = it }
            }
        }
    }
}
