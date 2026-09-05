# ProGuard / R8 rules for MemoStamp

# Keep generic signature and annotation attributes for reflection and serialization
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep Gson serialization fields and annotations
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @com.google.gson.annotations.Expose <fields>;
}

# Keep Supabase remote DTO models used in serialization
-keep class com.mipastudio.memostamp.data.remote.supabase.Supabase*Record { *; }
-keep class com.mipastudio.memostamp.data.remote.CloudTradePayload { *; }

# Keep domain models serialized/deserialized with Gson
-keep class com.mipastudio.memostamp.domain.model.UserProfile { *; }
-keep class com.mipastudio.memostamp.domain.model.FriendRequest { *; }
-keep class com.mipastudio.memostamp.domain.model.DirectMessage { *; }
-keep class com.mipastudio.memostamp.core.processor.CameraFilterSpec { *; }

# Keep Room entities, DAOs, and databases
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.RoomDatabase

# Keep KMP shared domain models
-keep class com.mipastudio.memostamp.domain.model.** { *; }
