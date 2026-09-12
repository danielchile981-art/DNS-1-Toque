package com.daniel.dnsoneclick;

import android.app.Activity;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.service.quicksettings.TileService;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbStream;
import io.github.muntashirakon.adb.LocalServices;
import io.github.muntashirakon.adb.android.AdbMdns;
import io.github.muntashirakon.adb.android.AndroidUtils;

public class MainActivity extends Activity {
    private static final String PERMISSION = "android.permission.WRITE_SECURE_SETTINGS";
    private static final String DNS_HOST = "dns.adguard.com";
    private static final int BG = Color.rgb(8, 21, 34);
    private static final int CARD = Color.rgb(16, 40, 58);
    private static final int ACCENT = Color.rgb(48, 242, 106);
    private static final int CYAN = Color.rgb(22, 228, 196);
    private static final int TEXT = Color.rgb(246, 255, 249);
    private static final int MUTED = Color.rgb(168, 185, 199);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView permissionStatus, dnsStatus, setupStatus;
    private EditText portInput, codeInput;
    private LinearLayout setupBox;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(buildUi());
        refreshStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(26), dp(20), dp(28));
        scroll.addView(root);

        TextView title = text("DNS 1 Toque", 30, TEXT, true);
        root.addView(title);
        TextView subtitle = text("AdGuard, Automático ou Desligado sem abrir a tela de DNS depois da ativação inicial.", 15, MUTED, false);
        subtitle.setPadding(0, dp(6), 0, dp(18));
        root.addView(subtitle);

        LinearLayout status = card();
        permissionStatus = text("", 17, TEXT, true);
        dnsStatus = text("", 14, MUTED, false);
        dnsStatus.setPadding(0, dp(7), 0, 0);
        status.addView(permissionStatus);
        status.addView(dnsStatus);
        root.addView(status);

        TextView section = text("Trocar DNS", 19, TEXT, true);
        section.setPadding(0, dp(22), 0, dp(8));
        root.addView(section);
        root.addView(actionButton("ATIVAR ADGUARD", () -> setPrivateDns("hostname", DNS_HOST, "AdGuard ativado")));
        root.addView(actionButton("DNS AUTOMÁTICO", () -> setPrivateDns("opportunistic", "", "DNS automático ativado")));
        root.addView(actionButton("DESLIGAR DNS PRIVADO", () -> setPrivateDns("off", "", "DNS privado desligado")));

        TextView tileSection = text("Atalho no painel rápido", 19, TEXT, true);
        tileSection.setPadding(0, dp(22), 0, dp(4));
        root.addView(tileSection);
        TextView tileHelp = text("Adiciona um botão na área de atalhos do Galaxy. Cada toque alterna: AdGuard → Automático → Desligado → AdGuard.", 14, MUTED, false);
        tileHelp.setPadding(0, 0, 0, dp(4));
        root.addView(tileHelp);
        root.addView(actionButton("ADICIONAR AO PAINEL RÁPIDO", this::requestQuickSettingsTile));

        setupBox = card();
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(-1, -2);
        boxLp.topMargin = dp(22);
        setupBox.setLayoutParams(boxLp);
        setupBox.addView(text("Ativação inicial — sem PC e sem Termux", 18, TEXT, true));
        TextView help = text("1. Abra a Depuração sem fio.\n2. Toque em Parear dispositivo com código.\n3. Mantenha a janela de pareamento aberta.\n4. Digite a porta e o código abaixo.\n5. Toque em Parear e liberar permissão.", 14, MUTED, false);
        help.setPadding(0, dp(8), 0, dp(10));
        setupBox.addView(help);
        setupBox.addView(actionButton("ABRIR DEPURAÇÃO SEM FIO", this::openWirelessDebugging));
        portInput = input("Porta de pareamento (ex.: 37145)", false);
        codeInput = input("Código de 6 números", true);
        codeInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        setupBox.addView(portInput);
        setupBox.addView(codeInput);
        setupBox.addView(actionButton("DETECTAR PORTA", this::detectPort));
        setupBox.addView(actionButton("PAREAR E LIBERAR PERMISSÃO", this::pairAndGrant));
        setupStatus = text("", 14, MUTED, false);
        setupStatus.setPadding(0, dp(9), 0, 0);
        setupBox.addView(setupStatus);
        root.addView(setupBox);

        TextView note = text("Depois de aparecer ‘Permissão liberada’, você pode desligar a Depuração sem fio. A troca de DNS e o botão do painel continuam funcionando.", 13, MUTED, false);
        note.setPadding(0, dp(16), 0, 0);
        root.addView(note);
        return scroll;
    }

    private Button actionButton(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(BG);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(ACCENT);
        bg.setCornerRadius(dp(14));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void setPrivateDns(String mode, String host, String success) {
        if (!hasSecurePermission()) {
            Toast.makeText(this, "Faça a ativação inicial primeiro.", Toast.LENGTH_SHORT).show();
            setupBox.setVisibility(View.VISIBLE);
            return;
        }
        try {
            Settings.Global.putString(getContentResolver(), "private_dns_mode", mode);
            Settings.Global.putString(getContentResolver(), "private_dns_specifier", host);
            Toast.makeText(this, success, Toast.LENGTH_SHORT).show();
            refreshStatus();
            refreshQuickTile();
        } catch (SecurityException e) {
            Toast.makeText(this, "A permissão especial ainda não foi liberada.", Toast.LENGTH_LONG).show();
        }
    }

    private void requestQuickSettingsTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            StatusBarManager statusBarManager = getSystemService(StatusBarManager.class);
            if (statusBarManager == null) {
                showManualTileInstructions();
                return;
            }

            ComponentName componentName = new ComponentName(this, DnsTileService.class);
            Icon icon = Icon.createWithResource(this, R.drawable.ic_dns_tile);
            Toast.makeText(this, "Confirme a adição do botão DNS 1 Toque.", Toast.LENGTH_SHORT).show();
            statusBarManager.requestAddTileService(
                    componentName,
                    "DNS 1 Toque",
                    icon,
                    getMainExecutor(),
                    result -> refreshQuickTile()
            );
        } else {
            showManualTileInstructions();
        }
    }

    private void showManualTileInstructions() {
        Toast.makeText(this, "Abra o painel rápido > Editar (lápis) > procure DNS 1 Toque e arraste para os atalhos.", Toast.LENGTH_LONG).show();
    }

    private void refreshQuickTile() {
        try {
            TileService.requestListeningState(this, new ComponentName(this, DnsTileService.class));
        } catch (Exception ignored) {
        }
    }

    private boolean hasSecurePermission() {
        return getPackageManager().checkPermission(PERMISSION, getPackageName()) == PackageManager.PERMISSION_GRANTED;
    }

    private void refreshStatus() {
        if (permissionStatus == null) return;
        boolean granted = hasSecurePermission();
        permissionStatus.setText(granted ? "✓ Permissão liberada" : "⚠ Permissão inicial pendente");
        permissionStatus.setTextColor(granted ? ACCENT : Color.rgb(255, 124, 124));
        setupBox.setVisibility(granted ? View.GONE : View.VISIBLE);
        String mode = Settings.Global.getString(getContentResolver(), "private_dns_mode");
        String spec = Settings.Global.getString(getContentResolver(), "private_dns_specifier");
        if ("hostname".equals(mode)) dnsStatus.setText("DNS atual: " + (spec == null || spec.isEmpty() ? "hostname privado" : spec));
        else if ("off".equals(mode)) dnsStatus.setText("DNS atual: desligado");
        else dnsStatus.setText("DNS atual: automático");
    }

    private void openWirelessDebugging() {
        try {
            startActivity(new Intent("android.settings.ADB_WIFI_SETTINGS"));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
            } catch (Exception ignored) {
                Toast.makeText(this, "Abra Opções do desenvolvedor > Depuração sem fio.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void detectPort() {
        setupStatus.setText("Procurando a porta… mantenha a janela de pareamento aberta.");
        executor.submit(() -> {
            int port = discoverPairingPort(25);
            runOnUiThread(() -> {
                if (port > 0) {
                    portInput.setText(String.valueOf(port));
                    setupStatus.setText("Porta detectada: " + port + ". Agora digite o código.");
                } else setupStatus.setText("Não encontrei a porta. Digite manualmente o número após os dois-pontos.");
            });
        });
    }

    private int discoverPairingPort(int seconds) {
        AtomicInteger port = new AtomicInteger(-1);
        CountDownLatch latch = new CountDownLatch(1);
        AdbMdns mdns = new AdbMdns(this, AdbMdns.SERVICE_TYPE_TLS_PAIRING, (host, foundPort) -> {
            if (foundPort > 0) {
                port.set(foundPort);
                latch.countDown();
            }
        });
        mdns.start();
        try {
            latch.await(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            mdns.stop();
        }
        return port.get();
    }

    private void pairAndGrant() {
        String code = codeInput.getText().toString().trim();
        String portText = portInput.getText().toString().trim();
        if (!code.matches("\\d{6}")) {
            Toast.makeText(this, "Digite o código de 6 números.", Toast.LENGTH_SHORT).show();
            return;
        }
        setupStatus.setText("Pareando… não feche a janela de pareamento.");
        executor.submit(() -> {
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) throw new Exception("Requer Android 11 ou superior.");
                int port = portText.isEmpty() ? discoverPairingPort(25) : Integer.parseInt(portText);
                if (port <= 0 || port > 65535) throw new Exception("Porta de pareamento inválida.");
                AbsAdbConnectionManager manager = AdbConnectionManager.getInstance(getApplicationContext());
                String host = AndroidUtils.getHostIpAddress(getApplicationContext());
                if (!manager.pair(host, port, code)) throw new Exception("Pareamento recusado. Confira porta e código.");

                runOnUiThread(() -> setupStatus.setText("Pareado. Conectando ao ADB local…"));
                if (!manager.autoConnect(getApplicationContext(), 10_000)) throw new Exception("Pareou, mas não conectou. Mantenha a Depuração sem fio ligada e tente de novo.");

                AdbStream shell = manager.openStream(LocalServices.SHELL);
                try (OutputStream out = shell.openOutputStream(); BufferedReader in = new BufferedReader(new InputStreamReader(shell.openInputStream()))) {
                    String cmd = "pm grant " + getPackageName() + " " + PERMISSION + "\nexit\n";
                    out.write(cmd.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    while (in.readLine() != null) { }
                } finally {
                    shell.close();
                }
                Thread.sleep(500);
                manager.disconnect();
                boolean granted = hasSecurePermission();
                runOnUiThread(() -> {
                    refreshStatus();
                    refreshQuickTile();
                    if (granted) {
                        setupStatus.setText("✓ Pronto. Permissão liberada. Pode desligar a Depuração sem fio.");
                        Toast.makeText(this, "Pronto! Agora é 1 toque.", Toast.LENGTH_LONG).show();
                    } else setupStatus.setText("Comando enviado. Feche e abra o app uma vez para atualizar o estado.");
                });
            } catch (Throwable e) {
                runOnUiThread(() -> setupStatus.setText("Erro: " + safeMessage(e)));
            }
        });
    }

    private String safeMessage(Throwable e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty() ? e.getClass().getSimpleName() : message;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(CARD);
        bg.setStroke(dp(1), CYAN);
        bg.setCornerRadius(dp(18));
        box.setBackground(bg);
        return box;
    }

    private EditText input(String hint, boolean password) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(120, 140, 154));
        e.setTextColor(TEXT);
        e.setTextSize(16);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | (password ? InputType.TYPE_NUMBER_VARIATION_PASSWORD : 0));
        e.setPadding(dp(14), 0, dp(14), 0);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.rgb(6, 16, 26));
        bg.setStroke(dp(1), Color.rgb(55, 90, 100));
        bg.setCornerRadius(dp(12));
        e.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.topMargin = dp(8);
        e.setLayoutParams(lp);
        return e;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
