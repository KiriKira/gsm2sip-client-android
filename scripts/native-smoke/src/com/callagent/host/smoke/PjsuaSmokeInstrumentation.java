package com.callagent.host.smoke;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Runs a JNI-only PJSUA2 check against the installed host application's class loader.
 * It does not create an account, make a SIP call, request microphone permission, or
 * record audio. The PJSUA2 null audio device is selected before codec enumeration.
 */
public final class PjsuaSmokeInstrumentation extends Instrumentation {
    private static final String TAG = "Pjsua2Smoke";

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        super.onStart();
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                Bundle result = new Bundle();
                int outcome = Activity.RESULT_CANCELED;
                try {
                    List<String> codecs = probeTargetNativeRuntime();
                    StringBuilder codecText = new StringBuilder();
                    for (String codec : codecs) {
                        if (codecText.length() > 0) codecText.append(',');
                        codecText.append(codec);
                    }
                    result.putString("native_library", "pjsua2");
                    result.putString("audio_device", "null");
                    result.putString("microphone_capture", "not requested");
                    result.putString("sip_call", "not attempted");
                    result.putString("codecs", codecText.toString());
                    outcome = Activity.RESULT_OK;
                    Log.i(TAG, "PASS: PJSUA2 JNI initialized with null audio; codecs=" + codecs);
                } catch (Throwable failure) {
                    Throwable root = unwrap(failure);
                    String description = root.getClass().getName() + ": " + root.getMessage();
                    result.putString("failure", description);
                    Log.e(TAG, "FAIL: PJSUA2 native runtime smoke failed: " + description, root);
                }
                sendStatus(2, result);
                finish(outcome, result);
            }
        }, "pjsua2-native-smoke");
        worker.start();
    }

    private List<String> probeTargetNativeRuntime() throws Exception {
        ClassLoader targetLoader = getTargetContext().getClassLoader();

        // Invoke the host app's own System.loadLibrary("pjsua2") entry point through
        // the target package class loader so Android resolves its packaged .so.
        Class<?> hostEngine = Class.forName(
                "com.callagent.host.sip.Pjsua2SipEngine", true, targetLoader);
        Object companion = hostEngine.getField("Companion").get(null);
        Method load = companion.getClass().getMethod("loadNativeLibrary");
        Object loaded = invoke(load, companion);
        if (!Boolean.TRUE.equals(loaded)) {
            throw new UnsatisfiedLinkError(
                    "host Pjsua2SipEngine.loadNativeLibrary() returned false");
        }

        Class<?> endpointType = Class.forName("org.pjsip.pjsua2.Endpoint", true, targetLoader);
        Class<?> epConfigType = Class.forName("org.pjsip.pjsua2.EpConfig", true, targetLoader);
        Object endpoint = endpointType.getConstructor().newInstance();
        Object epConfig = epConfigType.getConstructor().newInstance();
        boolean created = false;
        Throwable primaryFailure = null;
        try {
            invoke(endpointType.getMethod("libCreate"), endpoint);
            created = true;
            Object uaConfig = invoke(epConfigType.getMethod("getUaConfig"), epConfig);
            invoke(uaConfig.getClass().getMethod("setThreadCnt", long.class), uaConfig, 1L);
            invoke(endpointType.getMethod("libInit", epConfigType), endpoint, epConfig);
            invoke(endpointType.getMethod("libStart"), endpoint);

            Object audioManager = invoke(endpointType.getMethod("audDevManager"), endpoint);
            invoke(audioManager.getClass().getMethod("setNullDev"), audioManager);

            // In PJSUA2 2.17, codecEnum2() returns CodecInfoVector2; its API exposes
            // size() and get(int), with CodecInfo.getCodecId() for each entry.
            Object vector = invoke(endpointType.getMethod("codecEnum2"), endpoint);
            List<String> codecs = new ArrayList<>();
            try {
                Class<?> vectorType = vector.getClass();
                int count = ((Number) invoke(vectorType.getMethod("size"), vector)).intValue();
                Method get = vectorType.getMethod("get", int.class);
                for (int i = 0; i < count; i++) {
                    Object info = invoke(get, vector, i);
                    String id = (String) invoke(info.getClass().getMethod("getCodecId"), info);
                    codecs.add(id);
                }
            } finally {
                deleteIfAvailable(vector);
            }

            requireCodec(codecs, "opus/");
            requireCodec(codecs, "pcmu/");
            requireCodec(codecs, "pcma/");
            requireCodec(codecs, "g722/");
            return codecs;
        } catch (Exception failure) {
            primaryFailure = failure;
            throw failure;
        } catch (Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            Throwable destroyFailure = null;
            if (created) {
                try {
                    invoke(endpointType.getMethod("libDestroy"), endpoint);
                } catch (Throwable failure) {
                    destroyFailure = unwrap(failure);
                }
            }
            deleteIfAvailable(epConfig);
            deleteIfAvailable(endpoint);
            if (destroyFailure != null) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(destroyFailure);
                    Log.e(TAG, "Endpoint.libDestroy also failed after the probe failed", destroyFailure);
                } else {
                    rethrow(destroyFailure);
                }
            }
        }
    }

    private static void requireCodec(List<String> codecs, String expectedPrefix) {
        for (String codec : codecs) {
            if (codec.toLowerCase(Locale.ROOT).startsWith(expectedPrefix)) return;
        }
        throw new IllegalStateException(
                "required codec " + expectedPrefix + " absent from " + codecs);
    }

    private static Object invoke(Method method, Object receiver, Object... args) throws Exception {
        try {
            return method.invoke(receiver, args);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw failure;
        }
    }

    private static void rethrow(Throwable failure) throws Exception {
        if (failure instanceof Exception) throw (Exception) failure;
        if (failure instanceof Error) throw (Error) failure;
        throw new RuntimeException(failure);
    }

    private static void deleteIfAvailable(Object value) {
        if (value == null) return;
        try {
            value.getClass().getMethod("delete").invoke(value);
        } catch (NoSuchMethodException ignored) {
            // Plain Java values need no native cleanup.
        } catch (Throwable failure) {
            Log.w(TAG, "PJSUA2 object delete failed", unwrap(failure));
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while (current instanceof InvocationTargetException
                && ((InvocationTargetException) current).getCause() != null) {
            current = ((InvocationTargetException) current).getCause();
        }
        return current;
    }
}
