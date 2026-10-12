package com.callagent.host.data

import android.content.Context
import org.junit.Test

/**
 * The paired-UI fixture (scripts/paired-ui-fixture) builds these types by
 * reflection against the installed APK. Keep its constructor shapes.
 */
class FixtureBinaryCompatibilityTest {
    @Test
    fun reflectedConstructorsStayAvailable() {
        SessionStore::class.java.getConstructor(Context::class.java)
        HostSession::class.java.getConstructor(
            String::class.java, String::class.java, String::class.java, String::class.java, String::class.java,
            String::class.java, String::class.java, String::class.java, String::class.java,
            Boolean::class.javaPrimitiveType, String::class.java, String::class.java
        )
        GatewaySnapshot::class.java.getConstructor(
            String::class.java, String::class.java, Boolean::class.javaPrimitiveType, String::class.java,
            Long::class.javaPrimitiveType, java.lang.Boolean::class.java, java.lang.Boolean::class.java,
            java.lang.Integer::class.java, java.lang.Boolean::class.java
        )
        ClientDatabase::class.java.getConstructor(Context::class.java, String::class.java)
    }
}
