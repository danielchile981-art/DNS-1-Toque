package com.daniel.dnsoneclick;

import java.io.IOException;
import java.util.Objects;

/** The same verification and rollback rules apply to the app and ADB backends. */
final class DnsSettingsWriter {
    static final String MODE = "private_dns_mode";
    static final String HOST = "private_dns_specifier";
    interface Store {
        String read(String key) throws Exception;
        void write(String key, String value) throws Exception;
    }

    static void probe(Store store) throws Exception {
        String host = store.read(HOST);
        String mode = store.read(MODE);
        // Reapply only the existing values; activation does not select a new DNS.
        store.write(HOST, host);
        store.write(MODE, mode);
        verify(store, mode, host);
    }

    static void apply(Store store, String mode, String host) throws Exception {
        if (!"hostname".equals(mode) && !"off".equals(mode) && !"opportunistic".equals(mode))
            throw new IllegalArgumentException("Modo DNS inválido.");
        if ("hostname".equals(mode) && !"dns.adguard.com".equals(host))
            throw new IllegalArgumentException("Servidor DNS inválido.");
        String oldHost = store.read(HOST);
        String oldMode = store.read(MODE);
        try {
            // Configure the hostname before enabling strict mode.
            store.write(HOST, host);
            store.write(MODE, mode);
            verify(store, mode, host);
        } catch (Exception failure) {
            try {
                store.write(HOST, oldHost);
                store.write(MODE, oldMode);
                verify(store, oldMode, oldHost);
            } catch (Exception restoreFailure) {
                throw new IOException("A troca falhou e não foi possível restaurar o DNS anterior. Confira o DNS privado nas Configurações.", restoreFailure);
            }
            throw new IOException("A troca não foi confirmada; o DNS anterior foi restaurado. " + failure.getMessage(), failure);
        }
    }

    private static void verify(Store store, String mode, String host) throws Exception {
        if (!Objects.equals(mode, store.read(MODE)) || !Objects.equals(host, store.read(HOST)))
            throw new IOException("O Android não confirmou os valores do DNS.");
    }

    static String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
