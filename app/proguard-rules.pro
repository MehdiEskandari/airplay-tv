# Keep JNI callback interface (called from native code)
-keep class io.github.mehdieskandari.airplaytv.bridge.RaopCallbackHandler { *; }
-keep class * implements io.github.mehdieskandari.airplaytv.bridge.RaopCallbackHandler { *; }
-keep class io.github.mehdieskandari.airplaytv.bridge.LogListener { *; }
-keep class * implements io.github.mehdieskandari.airplaytv.bridge.LogListener { *; }

# Keep NativeBridge native methods
-keep class io.github.mehdieskandari.airplaytv.bridge.NativeBridge { *; }
