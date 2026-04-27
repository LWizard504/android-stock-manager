package com.stakia.stockmanager

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.okhttp.OkHttp
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

import android.content.Context
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.SettingsSessionManager
import com.russhwolf.settings.SharedPreferencesSettings

object SupabaseManager {
    lateinit var client: SupabaseClient
    
    fun init(context: Context) {
        val settings = SharedPreferencesSettings(context.getSharedPreferences("supabase_auth", Context.MODE_PRIVATE))
        client = createSupabaseClient(
            supabaseUrl = "https://fctewfmsofdcqrwlxoyo.supabase.co",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImZjdGV3Zm1zb2ZkY3Fyd2x4b3lvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzY3NDYzNDUsImV4cCI6MjA5MjMyMjM0NX0.efBvCsV_hfUYWskDoVmc5PPEB97RoCsDkgctj7X8g4g"
        ) {
            httpEngine = OkHttp.create()
            defaultSerializer = KotlinXSerializer(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                encodeDefaults = true
            })
            install(Auth) {
                sessionManager = SettingsSessionManager(settings)
            }
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }
    }
}
