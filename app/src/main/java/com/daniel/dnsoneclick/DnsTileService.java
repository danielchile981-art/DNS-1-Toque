package com.daniel.dnsoneclick;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class DnsTileService extends TileService {
    private static final String PERMISSION = "android.permission.WRITE_SECURE_SETTINGS";
    private static final String DNS_HOST = "dns.adguard.com";

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        updateTile();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (!DnsAccess.available(this)) {
            openMainApp();
            return;
        }

        if (isLocked()) {
            unlockAndRun(this::cycleDnsMode);
        } else {
            cycleDnsMode();
        }
    }

    private boolean changing;

    private void cycleDnsMode() {
        if (changing) return;
        changing = true;
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setSubtitle("Aplicando…");
            tile.updateTile();
        }
        DnsAccess.WORKER.submit(() -> {
            try {
                String mode = Settings.Global.getString(getContentResolver(), "private_dns_mode");
                String spec = Settings.Global.getString(getContentResolver(), "private_dns_specifier");
                if (isAdGuard(mode, spec)) DnsAccess.apply(getApplicationContext(), "opportunistic", "");
                else if (isAutomatic(mode)) DnsAccess.apply(getApplicationContext(), "off", "");
                else DnsAccess.apply(getApplicationContext(), "hostname", DNS_HOST);
                getMainExecutor().execute(() -> {
                    changing = false;
                    updateTile();
                });
            } catch (Exception error) {
                getMainExecutor().execute(() -> {
                    changing = false;
                    Toast.makeText(this, DnsAccess.error(error), Toast.LENGTH_LONG).show();
                    updateTile();
                    openMainApp();
                });
            }
        });
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        if (!DnsAccess.available(this)) {
            tile.setLabel("DNS 1 Toque");
            tile.setSubtitle("Abra o app para ativar");
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.updateTile();
            return;
        }

        String mode = Settings.Global.getString(getContentResolver(), "private_dns_mode");
        String spec = Settings.Global.getString(getContentResolver(), "private_dns_specifier");

        if (isAdGuard(mode, spec)) {
            tile.setLabel("DNS: AdGuard");
            tile.setSubtitle("Toque → Automático");
            tile.setState(Tile.STATE_ACTIVE);
        } else if (isAutomatic(mode)) {
            tile.setLabel("DNS: Automático");
            tile.setSubtitle("Toque → Desligado");
            tile.setState(Tile.STATE_INACTIVE);
        } else if ("off".equals(mode)) {
            tile.setLabel("DNS: Desligado");
            tile.setSubtitle("Toque → AdGuard");
            tile.setState(Tile.STATE_INACTIVE);
        } else {
            tile.setLabel("DNS: Outro");
            tile.setSubtitle("Toque → AdGuard");
            tile.setState(Tile.STATE_INACTIVE);
        }

        tile.updateTile();
    }

    private boolean isAdGuard(String mode, String spec) {
        return "hostname".equals(mode) && DNS_HOST.equalsIgnoreCase(spec == null ? "" : spec.trim());
    }

    private boolean isAutomatic(String mode) {
        return mode == null || mode.isEmpty() || "opportunistic".equals(mode);
    }

    private boolean hasSecurePermission() {
        return getPackageManager().checkPermission(PERMISSION, getPackageName()) == PackageManager.PERMISSION_GRANTED;
    }

    private void openMainApp() {
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        if (Build.VERSION.SDK_INT >= 34) {
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    1001,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            startActivityAndCollapse(pendingIntent);
        } else {
            startActivityAndCollapse(intent);
        }
    }
}
