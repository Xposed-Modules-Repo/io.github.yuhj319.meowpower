# libxposed 官方模板（https://github.com/libxposed/api#integration）：
# 入口类混淆后，R8 会同步改写 META-INF/xposed/java_init.list 里的类名。
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# androidx.window 的扩展类只在设备上存在，编译期没有，R8 会误报 Missing class
-dontwarn androidx.window.**
-keep class androidx.window.** { *; }
