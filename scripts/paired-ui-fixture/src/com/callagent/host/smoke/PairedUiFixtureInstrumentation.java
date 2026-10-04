package com.callagent.host.smoke;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;

/**
 * Seeds and removes a synthetic, encrypted paired-session cache through the
 * installed host APK's own classes. This APK is a CI-only fixture tool; it does
 * not pair with a server or issue SMS/SIP/call requests.
 */
public final class PairedUiFixtureInstrumentation extends Instrumentation {
    private static final String TAG = "PairedUiFixture";
    private static final String API_BASE = "https://ui-smoke.invalid";
    private static final String OWNER_ID = "synthetic-ui-fixture-owner";
    private static final String DEVICE_ID = "synthetic-ui-fixture-client";
    private static final String SESSION_ID = "synthetic-ui-fixture-session";
    private static final String DB_NAME_METHOD = "clientDatabaseName";
    private String mode = "";

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        mode = arguments == null ? "" : arguments.getString("mode", "");
        start();
    }

    @Override
    public void onStart() {
        super.onStart();
        final String operation = mode;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bundle result = new Bundle();
                int outcome = Activity.RESULT_CANCELED;
                try {
                    if ("seed".equals(operation)) {
                        seedFixture();
                        result.putString("fixture", "seeded");
                        Log.i(TAG, "Seeded synthetic offline paired UI cache");
                    } else if ("cleanup".equals(operation)) {
                        cleanupFixture();
                        result.putString("fixture", "cleaned");
                        Log.i(TAG, "Removed synthetic offline paired UI cache");
                    } else {
                        throw new IllegalArgumentException("mode must be seed or cleanup");
                    }
                    outcome = Activity.RESULT_OK;
                } catch (Throwable failure) {
                    String message = failure.getClass().getName() + ": " + failure.getMessage();
                    result.putString("failure", message);
                    Log.e(TAG, "Synthetic paired UI fixture operation failed: " + message, failure);
                }
                sendStatus(2, result);
                finish(outcome, result);
            }
        }, "paired-ui-fixture").start();
    }

    private void seedFixture() throws Exception {
        Context target = getTargetContext();
        ClassLoader loader = target.getClassLoader();

        Class<?> sessionType = hostClass(loader, "com.callagent.host.data.HostSession");
        Class<?> storeType = hostClass(loader, "com.callagent.host.data.SessionStore");
        Object store = storeType.getConstructor(Context.class).newInstance(target);
        Object current = storeType.getMethod("read").invoke(store);
        if (current != null) {
            throw new IllegalStateException("refusing to replace an existing host session");
        }
        Object syntheticSession = sessionType.getConstructor(
                String.class, String.class, String.class, String.class, String.class,
                String.class, String.class, String.class, String.class, boolean.class,
                String.class, String.class
        ).newInstance(
                SESSION_ID, API_BASE, OWNER_ID, DEVICE_ID, "client",
                "synthetic-ui-fixture-access-token", "2099-01-01T00:00:00Z",
                "synthetic-ui-fixture-refresh-token", "2099-01-01T00:00:00Z",
                false, "SYNTHETIC_OFFLINE_FIXTURE", null
        );
        boolean saved = (Boolean) storeType.getMethod("writeIfCurrent", sessionType, sessionType)
                .invoke(store, current, syntheticSession);
        if (!saved) throw new IllegalStateException("SessionStore refused the empty-to-fixture transition");

        String databaseName = databaseName(loader);
        Class<?> databaseType = hostClass(loader, "com.callagent.host.data.ClientDatabase");
        Object database = databaseType.getConstructor(Context.class, String.class)
                .newInstance(target, databaseName);
        try {
            Class<?> gatewayType = hostClass(loader, "com.callagent.host.data.GatewaySnapshot");
            Object gateway = gatewayType.getConstructor(
                    String.class, String.class, boolean.class, String.class, long.class,
                    Boolean.class, Boolean.class, Integer.class, Boolean.class
            ).newInstance("synthetic-gateway", "SYNTHETIC offline gateway", false,
                    "2026-10-04T09:30:00Z", 42L, false, false, 84, false);
            databaseType.getMethod("saveGateway", gatewayType).invoke(database, gateway);

            Class<?> simType = hostClass(loader, "com.callagent.host.data.SimLine");
            Object simA = simType.getConstructor(
                    String.class, int.class, String.class, String.class, String.class,
                    String.class, long.class, boolean.class, String.class
            ).newInstance("synthetic-sim-a", 0, "SYNTHETIC SIM A", "Fixture Carrier",
                    "+15550001001", "active", 42L, true, "in_service");
            Object simB = simType.getConstructor(
                    String.class, int.class, String.class, String.class, String.class,
                    String.class, long.class, boolean.class, String.class
            ).newInstance("synthetic-sim-b", 1, "SYNTHETIC SIM B", "Fixture Carrier",
                    "+15550001002", "active", 42L, true, "in_service");
            databaseType.getMethod("replaceSims", java.util.List.class)
                    .invoke(database, java.util.Arrays.asList(simA, simB));

            Class<?> recordType = hostClass(loader, "com.callagent.host.data.SmsRecord");
            Constructor<?> recordConstructor = recordType.getConstructor(
                    String.class, String.class, String.class, String.class, Long.class,
                    String.class, String.class, String.class, String.class, String.class,
                    String.class, String.class, Integer.class, java.util.List.class,
                    String.class, String.class, String.class
            );
            Object inbound = recordConstructor.newInstance(
                    "synthetic-inbound-local", "synthetic-inbound-server", "synthetic-inbound-command",
                    "synthetic-sim-a", 42L, "inbound", "+15550102001", "+15550001001",
                    "SYNTHETIC UI fixture inbox preview — no SMS was received.", "received",
                    "2026-10-04T09:42:00Z", null, 1, Collections.emptyList(), null, null, null
            );
            Object outbound = recordConstructor.newInstance(
                    "synthetic-outbound-local", "synthetic-outbound-server", "synthetic-outbound-command",
                    "synthetic-sim-a", 42L, "outbound", "+15550001001", "+15550102002",
                    "SYNTHETIC UI fixture queued preview — no SMS was sent.", "queued",
                    "2026-10-04T09:45:00Z", "2099-01-01T00:00:00Z", 1,
                    Collections.emptyList(), null, "synthetic-gateway", null
            );
            databaseType.getMethod("upsertRemoteMessage", recordType).invoke(database, inbound);
            databaseType.getMethod("upsertRemoteMessage", recordType).invoke(database, outbound);

            String pairedHosts = "["
                    + "{\"id\":\"synthetic-host-self\",\"name\":\"SYNTHETIC Foldable Host\","
                    + "\"platform\":\"android\",\"state\":\"active\",\"is_self\":true},"
                    + "{\"id\":\"synthetic-host-tablet\",\"name\":\"SYNTHETIC Tablet Host\","
                    + "\"platform\":\"android\",\"state\":\"active\",\"is_self\":false}"
                    + "]";
            databaseType.getMethod("saveSyncStateValue", String.class, String.class)
                    .invoke(database, "paired_hosts_snapshot", pairedHosts);
        } finally {
            databaseType.getMethod("close").invoke(database);
        }
    }

    private void cleanupFixture() throws Exception {
        Context target = getTargetContext();
        ClassLoader loader = target.getClassLoader();
        Class<?> sessionType = hostClass(loader, "com.callagent.host.data.HostSession");
        Class<?> storeType = hostClass(loader, "com.callagent.host.data.SessionStore");
        Object store = storeType.getConstructor(Context.class).newInstance(target);
        Object current = storeType.getMethod("read").invoke(store);
        if (current != null && SESSION_ID.equals(sessionType.getMethod("getSessionInstanceId").invoke(current))
                && OWNER_ID.equals(sessionType.getMethod("getOwnerId").invoke(current))) {
            storeType.getMethod("clearIfCurrent", sessionType).invoke(store, current);
        }
        target.deleteDatabase(databaseName(loader));
    }

    private String databaseName(ClassLoader loader) throws Exception {
        Class<?> scope = hostClass(loader, "com.callagent.host.data.ClientDatabaseScopeKt");
        return (String) scope.getMethod(DB_NAME_METHOD, String.class, String.class, String.class)
                .invoke(null, API_BASE, OWNER_ID, DEVICE_ID);
    }

    private static Class<?> hostClass(ClassLoader loader, String name) throws ClassNotFoundException {
        return Class.forName(name, true, loader);
    }
}
