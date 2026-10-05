package com.callagent.host.smoke;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collections;
import java.io.File;
import java.io.FileOutputStream;

/**
 * Seeds and removes a synthetic, encrypted paired-session cache through the
 * installed host APK's own classes. This APK is a CI-only fixture tool; it does
 * not pair with a server or issue SMS/SIP/call requests.
 */
public final class PairedUiFixtureInstrumentation extends Instrumentation {
    private static final String TAG = "PairedUiFixture";
    // Production pairing stores ApiClient's canonical API root, including /v1.
    // Keep this synthetic lease identical so offline refresh reaches the network
    // failure path rather than being rejected as a changed paired account.
    private static final String API_BASE = "https://ui-smoke.invalid/v1";
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
                    } else if ("call-screens".equals(operation)) {
                        captureSyntheticCallScreens();
                        result.putString("fixture", "call_screens_captured");
                        Log.i(TAG, "Captured local-only synthetic call UI screens");
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

    private void captureSyntheticCallScreens() throws Exception {
        Context target = getTargetContext();
        Class<?> runtimeType = hostClass(target.getClassLoader(), "com.callagent.host.calls.CallRuntime");
        Object runtime = runtimeType.getField("INSTANCE").get(null);
        Field coordinatorField = runtimeType.getDeclaredField("coordinator");
        coordinatorField.setAccessible(true);
        Object coordinator = coordinatorField.get(runtime);
        String callId = "synthetic-call-ui-fixture";
        String incomingId = "synthetic-call-ui-incoming-fixture";
        final android.app.Activity[] currentActivity = new android.app.Activity[1];
        try {
            long now = System.currentTimeMillis();
            boolean began = (Boolean) coordinator.getClass()
                    .getMethod("beginOutbound", String.class, String.class)
                    .invoke(coordinator, "synthetic-sim-a", "+15550102001");
            boolean authorized = began && (Boolean) coordinator.getClass()
                    .getMethod("outboundAuthorized", String.class, long.class, long.class)
                    .invoke(coordinator, callId, now + 600000L, now);
            boolean ready = authorized && (Boolean) coordinator.getClass()
                    .getMethod("registrationReady", String.class).invoke(coordinator, callId);
            if (!ready) throw new IllegalStateException("could not prepare synthetic outgoing call UI phase");

            UiAutomation automation = getUiAutomation();
            currentActivity[0] = startCallActivity(target, callId, false);
            requireVisibleText(automation, "正在呼叫…", "+15550102001", "挂断");
            saveCallScreenshot(target, automation, "call-synthetic-dialing.png");

            boolean active = (Boolean) coordinator.getClass()
                    .getMethod("connected", String.class).invoke(coordinator, callId);
            if (!active) throw new IllegalStateException("could not prepare synthetic active call UI phase");
            notifyCallUiChanged(target, callId);
            waitForIdleSync();
            requireVisibleText(automation, "通话中", "+15550102001", "挂断");
            saveCallScreenshot(target, automation, "call-synthetic-active.png");

            clickVisibleText(automation, "键盘");
            waitForIdleSync();
            requireVisibleText(automation, "通话中", "1", "2", "#");
            saveCallScreenshot(target, automation, "call-synthetic-keypad.png");
            finishActivity(currentActivity[0]);
            currentActivity[0] = null;

            coordinator.getClass().getMethod("ended", String.class, String.class)
                    .invoke(coordinator, callId, "synthetic UI fixture complete");
            coordinator.getClass().getMethod("clearTerminal", String.class).invoke(coordinator, callId);
            Class<?> inviteType = hostClass(target.getClassLoader(),
                    "com.callagent.host.calls.IncomingInviteIdentity");
            Class<?> authorityType = hostClass(target.getClassLoader(), "com.callagent.host.calls.CallAuthority");
            Object invite = inviteType.getConstructor(String.class, String.class)
                    .newInstance("synthetic-sip-call-id", incomingId);
            Object authority = authorityType.getConstructor(String.class, String.class, String.class,
                    String.class, String.class, long.class)
                    .newInstance(incomingId, "incoming", "ringing", "synthetic-sim-b",
                            "+15550102003", System.currentTimeMillis() + 600000L);
            boolean incoming = (Boolean) coordinator.getClass()
                    .getMethod("incomingInvite", inviteType, authorityType, long.class)
                    .invoke(coordinator, invite, authority, System.currentTimeMillis());
            if (!incoming) throw new IllegalStateException("could not prepare synthetic incoming call UI phase");
            currentActivity[0] = startCallActivity(target, incomingId, true);
            requireVisibleText(automation, "来电", "+15550102003", "接听", "拒接");
            saveCallScreenshot(target, automation, "call-synthetic-incoming.png");
        } finally {
            finishActivity(currentActivity[0]);
            clearSyntheticCallState(coordinator);
        }
    }

    private void finishActivity(final android.app.Activity activity) {
        if (activity == null) return;
        runOnMainSync(new Runnable() {
            @Override
            public void run() {
                activity.finish();
            }
        });
        waitForIdleSync();
    }

    private void clearSyntheticCallState(Object coordinator) throws Exception {
        Object current = coordinator.getClass().getMethod("getCurrent").invoke(coordinator);
        if (current == null) return;
        String callId = (String) current.getClass().getMethod("getCallId").invoke(current);
        coordinator.getClass().getMethod("ended", String.class, String.class)
                .invoke(coordinator, callId, "synthetic UI fixture cleanup");
        coordinator.getClass().getMethod("clearTerminal", String.class).invoke(coordinator, callId);
    }

    private android.app.Activity startCallActivity(Context target, String callId, boolean incoming) {
        Intent intent = new Intent()
                .setClassName(target.getPackageName(), "com.callagent.host.calls.CallActionActivity")
                .setAction(incoming ? "com.callagent.host.calls.SHOW_INCOMING" :
                        "com.callagent.host.calls.SHOW_CALL")
                .putExtra("com.callagent.host.calls.CALL_ID", callId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return startActivitySync(intent);
    }

    private void notifyCallUiChanged(Context target, String callId) {
        target.sendBroadcast(new Intent("com.callagent.host.calls.STATE_CHANGED")
                .setPackage(target.getPackageName())
                .putExtra("com.callagent.host.calls.CHANGED_CALL_ID", callId));
    }

    private void clickVisibleText(UiAutomation automation, String text) {
        AccessibilityNodeInfo root = automation.getRootInActiveWindow();
        if (root == null) throw new IllegalStateException("call UI accessibility root is missing");
        java.util.List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByText(text);
        for (AccessibilityNodeInfo node : matches) {
            if (node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
            AccessibilityNodeInfo parent = node.getParent();
            if (parent != null && parent.isClickable() && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
        }
        throw new IllegalStateException("could not open synthetic call keypad control");
    }

    private void requireVisibleText(UiAutomation automation, String... values) {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            waitForIdleSync();
            AccessibilityNodeInfo root = automation.getRootInActiveWindow();
            if (root != null) {
                boolean complete = true;
                for (String value : values) {
                    if (root.findAccessibilityNodeInfosByText(value).isEmpty()) {
                        complete = false;
                        break;
                    }
                }
                if (complete) return;
            }
            try {
                Thread.sleep(250L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for synthetic call UI", interrupted);
            }
        }
        throw new IllegalStateException("call UI did not expose all expected synthetic text: " +
                java.util.Arrays.toString(values));
    }

    private void saveCallScreenshot(Context target, UiAutomation automation, String filename) throws Exception {
        waitForIdleSync();
        Bitmap image = automation.takeScreenshot();
        if (image == null) throw new IllegalStateException("UiAutomation returned no call UI screenshot");
        File outputDirectory = new File(target.getCacheDir(), "host-ui-call-fixture");
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw new IllegalStateException("could not create synthetic call screenshot directory");
        }
        File output = new File(outputDirectory, filename);
        try (FileOutputStream stream = new FileOutputStream(output)) {
            if (!image.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                throw new IllegalStateException("could not encode synthetic call screenshot");
            }
            stream.flush();
        } finally {
            image.recycle();
        }
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
            Object inboundSimB = recordConstructor.newInstance(
                "synthetic-inbound-sim-b-local", "synthetic-inbound-sim-b-server",
                "synthetic-inbound-sim-b-command", "synthetic-sim-b", 42L, "inbound",
                "+15550102003", "+15550001002",
                "SYNTHETIC SIM B fixture preview — no SMS was received.", "received",
                "2026-10-04T09:40:00Z", null, 1, Collections.emptyList(), null, null, null
            );
            databaseType.getMethod("upsertRemoteMessage", recordType).invoke(database, inbound);
            databaseType.getMethod("upsertRemoteMessage", recordType).invoke(database, outbound);
            databaseType.getMethod("upsertRemoteMessage", recordType).invoke(database, inboundSimB);

            String pairedHosts = "["
                    + "{\"id\":\"synthetic-host-self\",\"name\":\"SYNTHETIC Foldable Host\","
                    + "\"platform\":\"android\",\"state\":\"active\",\"is_self\":true},"
                    + "{\"id\":\"synthetic-host-tablet\",\"name\":\"SYNTHETIC Tablet Host\","
                    + "\"platform\":\"android\",\"state\":\"active\",\"is_self\":false}"
                    + "]";
            databaseType.getMethod("saveSyncStateValue", String.class, String.class)
                    .invoke(database, "paired_hosts_snapshot", pairedHosts);
            String callHistory = "{\"items\":["
                    + "{\"call_id\":\"synthetic-call-history-incoming\",\"gateway_id\":\"synthetic-gateway\","
                    + "\"sim_id\":\"synthetic-sim-a\",\"mapping_revision\":42,\"direction\":\"incoming\","
                    + "\"state\":\"ended\",\"state_revision\":1,\"from\":\"+15550102001\","
                    + "\"to\":\"+15550001001\",\"created_at\":\"2026-10-04T09:42:00Z\","
                    + "\"expires_at\":null,\"answered_at\":null,\"ended_at\":\"2026-10-04T09:43:00Z\",\"reason\":null},"
                    + "{\"call_id\":\"synthetic-call-history-outgoing\",\"gateway_id\":\"synthetic-gateway\","
                    + "\"sim_id\":\"synthetic-sim-b\",\"mapping_revision\":42,\"direction\":\"outgoing\","
                    + "\"state\":\"ended\",\"state_revision\":1,\"from\":\"+15550001002\","
                    + "\"to\":\"+15550102002\",\"created_at\":\"2026-10-04T09:40:00Z\","
                    + "\"expires_at\":null,\"answered_at\":\"2026-10-04T09:41:00Z\","
                    + "\"ended_at\":\"2026-10-04T09:41:30Z\",\"reason\":null}],\"next_cursor\":null}";
            databaseType.getMethod("saveSyncStateValue", String.class, String.class)
                    .invoke(database, "call_history_snapshot_v1", callHistory);
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
        deleteRecursively(new File(target.getCacheDir(), "host-ui-call-fixture"));

        // Paired UI opens the automatic remote-messaging service on newer builds.
        // The fixture radios are offline, so no server/SMS request can succeed;
        // clear the synthetic service binding and stop app-owned services after
        // removing the synthetic session.
        target.getSharedPreferences("host-background", Context.MODE_PRIVATE)
                .edit().putBoolean("enabled", false).commit();
        stopService(target, "com.callagent.host.background.HostBackgroundService");
        stopService(target, "com.callagent.host.calls.HostCallService");
        stopService(target, "com.callagent.host.calls.CallForegroundService");
        target.getSharedPreferences("host-background", Context.MODE_PRIVATE).edit().clear().commit();
    }

    private static void stopService(Context context, String className) {
        context.stopService(new android.content.Intent().setClassName(context.getPackageName(), className));
    }

    private static void deleteRecursively(File path) {
        if (!path.exists()) return;
        if (path.isDirectory()) {
            File[] children = path.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        path.delete();
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
