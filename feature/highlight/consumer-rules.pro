# Keep all sherpa-onnx JNI classes — the native .so calls these by exact
# constructor/method signatures, so R8 must not rename or remove them.
-keep class com.k2fsa.sherpa.onnx.** { *; }
