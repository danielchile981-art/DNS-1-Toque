package com.daniel.dnsoneclick;

import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbConnection;
import io.github.muntashirakon.adb.AdbStream;

final class DnsAccess {
    static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor();
    private static final String PERMISSION = "android.permission.WRITE_SECURE_SETTINGS";

    static boolean hasPermission(Context context) {
        return context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    // This records a tested backend, not an active connection. Each action reconnects and verifies.
    static boolean adbConfigured(Context context) {
        return context.getSharedPreferences("dns_access", Context.MODE_PRIVATE).getBoolean("adb_tested", false);
    }

    static boolean available(Context context) {
        return hasPermission(context) || adbConfigured(context);
    }

    static boolean activate(Context context, AbsAdbConnectionManager manager) throws Exception {
        context.getSharedPreferences("dns_access", Context.MODE_PRIVATE).edit().remove("adb_tested").apply();
        try {
            boolean granted = DnsActivation.activate(new DnsActivation.Grant() {
                public void attempt() throws Exception {
                    run(manager, "pm grant " + DnsSettingsWriter.quote(context.getPackageName()) + " " + PERMISSION);
                }
                public boolean isGranted() { return hasPermission(context); }
            }, shellStore(manager));
            if (granted) return true;
            context.getSharedPreferences("dns_access", Context.MODE_PRIVATE).edit().putBoolean("adb_tested", true).apply();
            return false;
        } finally {
            manager.disconnect();
        }
    }

    static void apply(Context context, String mode, String host) throws Exception {
        if (hasPermission(context)) {
            DnsSettingsWriter.apply(appStore(context), mode, host);
            return;
        }
        if (!adbConfigured(context)) throw new IOException("Abra o app e faça o pareamento primeiro.");
        AbsAdbConnectionManager manager = AdbConnectionManager.getInstance(context);
        manager.setTimeout(10, TimeUnit.SECONDS);
        try {
            if (!manager.isConnected() && !manager.connectTls(context, 10_000))
                throw new IOException("ADB não conectou.");
            DnsSettingsWriter.apply(shellStore(manager), mode, host);
        } finally {
            manager.disconnect();
        }
    }

    private static DnsSettingsWriter.Store appStore(Context context) {
        return new DnsSettingsWriter.Store() {
            public String read(String key) {
                return Settings.Global.getString(context.getContentResolver(), key);
            }
            public void write(String key, String value) throws IOException {
                if (!Settings.Global.putString(context.getContentResolver(), key, value))
                    throw new IOException("O Android recusou a alteração do DNS.");
            }
        };
    }

    private static DnsSettingsWriter.Store shellStore(AbsAdbConnectionManager manager) {
        return new DnsSettingsWriter.Store() {
            public String read(String key) throws Exception {
                String value = run(manager, "settings get global " + key);
                return "null".equals(value) ? null : value;
            }
            public void write(String key, String value) throws Exception {
                String command = value == null ? "settings delete global " + key
                        : "settings put global " + key + " " + DnsSettingsWriter.quote(value);
                String response = run(manager, command);
                // Some firmware reports a Java error with exit code 0. Reject unexpected output.
                if (!response.isEmpty() && !(value == null && response.matches("Deleted [0-9]+ rows")))
                    throw new IOException("O sistema não aceitou alterar o DNS: " + response);
            }
        };
    }

    private static String run(AbsAdbConnectionManager manager, String command) throws Exception {
        AdbConnection connection = manager.getAdbConnection();
        if (connection == null) throw new IOException("ADB desconectado.");
        AtomicBoolean timedOut = new AtomicBoolean();
        ScheduledFuture<?> timeout = TIMER.schedule(() -> {
            timedOut.set(true);
            try { connection.close(); } catch (IOException ignored) { }
        }, 15, TimeUnit.SECONDS);
        AdbStream stream = null;
        try {
            // Use a noninteractive shell: no terminal echo, prompts or output-stream flush after EOF.
            stream = manager.openStream("shell:" + command + "; result=$?; printf '\\n"
                    + AdbShellResult.MARKER + "%s\\n' \"$result\"");
            InputStream input = stream.openInputStream();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > 16384) throw new IOException("Resposta ADB excedeu o limite.");
                output.write(buffer, 0, count);
            }
            if (timedOut.get()) throw new IOException("ADB não respondeu em 15 segundos.");
            return AdbShellResult.checked(output.toString(StandardCharsets.UTF_8.name()));
        } finally {
            timeout.cancel(false);
            if (stream != null) try { stream.close(); } catch (IOException ignored) { }
        }
    }

    static String error(Exception error) {
        String message = error.getMessage();
        if (message == null) message = error.getClass().getSimpleName();
        String diagnostics = message;
        for (Throwable cause = error.getCause(); cause != null; cause = cause.getCause())
            diagnostics += " " + cause.getMessage();
        if (diagnostics.contains("SecurityException") || diagnostics.contains("Permission denial"))
            return "Este sistema bloqueou também a alteração direta do DNS pelo ADB. Abra Configurações > Conexão e compartilhamento > DNS privado para alterar manualmente.";
        if (message.contains("Timed out") || message.contains("Not connected") || message.contains("Connection refused"))
            return "ADB indisponível. Mantenha o Wi-Fi e a Depuração sem fio ligados. Se removeu o pareamento, pareie novamente.";
        return message;
    }
}
