# MLB Scoreboard ProGuard Rules

# Keep Retrofit interfaces
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Keep Gson model classes
-keep class com.mlb.scoreboard.data.models.** { *; }

# Keep enum classes
-keepclassmembers enum * { *; }
