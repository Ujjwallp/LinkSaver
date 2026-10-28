# LinkVault R8 / Proguard Security & Stability Rules

# Keep Room database schemas, DAOs, and entities
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# Keep Data Models
-keep class com.example.data.** { *; }
-keepclassmembers class com.example.data.** { *; }

# Keep BiometricPrompt and Cryptographic Providers
-keep class androidx.biometric.** { *; }
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }

# Keep Coroutines Internal reflection
-keep class kotlinx.coroutines.** { *; }

# Strip line numbers and source file names in release for anti-reverse engineering
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
