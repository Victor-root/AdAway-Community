# Temporary fix for androidx preference fragement reference
# See https://issuetracker.google.com/issues/145316223
-keep public class org.adaway.ui.prefs.PrefsBackupRestoreFragment
-keep public class org.adaway.ui.prefs.PrefsUpdateFragment

-dontobfuscate

### Android Jetpack ###
-dontwarn com.google.**

### OkHttp ###
# JSR 305 annotations are for embedding nullability information.
-dontwarn javax.annotation.**
# A resource is loaded with a relative path so the package of this class must be preserved.
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
# Animal Sniffer compileOnly dependency to ensure APIs are compatible with older versions of Java.
-dontwarn org.codehaus.mojo.animal_sniffer.*
# OkHttp platform used only on JVM and when Conscrypt dependency is available.
-dontwarn okhttp3.internal.platform.ConscryptPlatform
# Generated rules from R8
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

### dnsjava ###
# Java 18+ InetAddressResolverProvider, only in the jar's META-INF/versions/18: Android never loads it
-dontwarn org.xbill.DNS.spi.DnsjavaInetAddressResolverProvider
-dontwarn lombok.Generated
-dontwarn sun.net.spi.nameservice.NameServiceDescriptor
