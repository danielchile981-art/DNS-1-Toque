// SPDX-License-Identifier: GPL-3.0-or-later OR Apache-2.0

package io.github.muntashirakon.adb.android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

public class AndroidUtils {
    private static final String GOLDFISH = "goldfish";
    private static final String RANCHU = "ranchu";
    private static final String SDK = "sdk";

    public static boolean isEmulator(@NonNull Context context) {
        if (Build.PRODUCT.contains(SDK)) return true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO
                && (Build.HARDWARE.contains(GOLDFISH) || Build.HARDWARE.contains(RANCHU))) return true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CUPCAKE) {
            @SuppressLint("HardwareIds")
            String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            return androidId == null;
        }
        return false;
    }

    /**
     * Returns this device's Wi-Fi IPv4 address. Wireless debugging listens on the device's
     * network interface; loopback (127.0.0.1) is not a valid substitute on many OEM builds.
     */
    @NonNull
    public static String getHostIpAddress(@NonNull Context context) {
        ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity != null) {
            try {
                Network[] networks = connectivity.getAllNetworks();
                // Prefer Wi-Fi because Android Wireless debugging is normally published there.
                for (Network network : networks) {
                    NetworkCapabilities caps = connectivity.getNetworkCapabilities(network);
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        String address = getIpv4Address(connectivity.getLinkProperties(network));
                        if (address != null) return address;
                    }
                }
                Network active = connectivity.getActiveNetwork();
                String address = active == null ? null : getIpv4Address(connectivity.getLinkProperties(active));
                if (address != null) return address;
            } catch (RuntimeException ignored) {
                // Fall back to the interfaces below on OEM implementations with incomplete network data.
            }
        }

        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!networkInterface.isUp() || networkInterface.isLoopback()) continue;
                String name = networkInterface.getName().toLowerCase();
                if (!name.startsWith("wlan") && !name.startsWith("wifi") && !name.startsWith("eth")) continue;
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()
                            && !address.isLinkLocalAddress() && !address.isAnyLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }

    private static String getIpv4Address(LinkProperties properties) {
        if (properties == null) return null;
        for (LinkAddress linkAddress : properties.getLinkAddresses()) {
            InetAddress address = linkAddress.getAddress();
            if (address instanceof Inet4Address && !address.isLoopbackAddress()
                    && !address.isLinkLocalAddress() && !address.isAnyLocalAddress()) {
                return address.getHostAddress();
            }
        }
        return null;
    }
}
