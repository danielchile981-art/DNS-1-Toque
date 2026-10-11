package com.daniel.dnsoneclick;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DnsAccessLogicTest {
    static class Store implements DnsSettingsWriter.Store {
        final Map<String,String> values = new HashMap<>();
        int writes;
        boolean denied, ignoreNewMode, failNewMode;
        Store() {
            values.put(DnsSettingsWriter.MODE, "opportunistic");
            values.put(DnsSettingsWriter.HOST, "");
        }
        public String read(String key) { return values.get(key); }
        public void write(String key, String value) throws IOException {
            writes++;
            if (denied) throw new IOException("SecurityException: WRITE_SECURE_SETTINGS");
            if (DnsSettingsWriter.MODE.equals(key) && "hostname".equals(value)) {
                if (failNewMode) throw new IOException("mode denied");
                if (ignoreNewMode) return;
            }
            values.put(key, value);
        }
    }
    static DnsActivation.Grant grant(boolean allowed) {
        return new DnsActivation.Grant() {
            public void attempt() throws IOException {
                if (!allowed) throw new IOException("GRANT_RUNTIME_PERMISSIONS denied");
            }
            public boolean isGranted() { return allowed; }
        };
    }
    @Test public void deniedGrantUsesShellAndPreservesDns() throws Exception {
        Store store = new Store();
        assertFalse(DnsActivation.activate(grant(false), store));
        assertEquals(2, store.writes);
        assertEquals("opportunistic", store.read(DnsSettingsWriter.MODE));
        assertEquals("", store.read(DnsSettingsWriter.HOST));
    }
    @Test public void grantedPermissionDoesNotProbeShell() throws Exception {
        Store store = new Store();
        store.denied = true;
        assertTrue(DnsActivation.activate(grant(true), store));
        assertEquals(0, store.writes);
    }
    @Test public void bothPermissionsDeniedDoNotActivate() throws Exception {
        Store store = new Store();
        store.denied = true;
        try { DnsActivation.activate(grant(false), store); fail("Must reject"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("WRITE_SECURE_SETTINGS")); }
    }
    @Test public void successfulWriteIsReadBack() throws Exception {
        Store store = new Store();
        DnsSettingsWriter.apply(store, "hostname", "dns.adguard.com");
        assertEquals("hostname", store.read(DnsSettingsWriter.MODE));
        assertEquals("dns.adguard.com", store.read(DnsSettingsWriter.HOST));
    }
    @Test public void silentFailureRollsBackBothValues() throws Exception {
        Store store = new Store();
        store.ignoreNewMode = true;
        try { DnsSettingsWriter.apply(store, "hostname", "dns.adguard.com"); fail("Must verify"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("restaurado")); }
        assertEquals("opportunistic", store.read(DnsSettingsWriter.MODE));
        assertEquals("", store.read(DnsSettingsWriter.HOST));
    }
    @Test public void partialWriteRollsBackHostname() throws Exception {
        Store store = new Store();
        store.failNewMode = true;
        try { DnsSettingsWriter.apply(store, "hostname", "dns.adguard.com"); fail("Must reject"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("restaurado")); }
        assertEquals("", store.read(DnsSettingsWriter.HOST));
    }
    @Test public void failedRollbackIsReported() throws Exception {
        Store store = new Store();
        store.denied = true;
        try { DnsSettingsWriter.apply(store, "off", ""); fail("Must reject"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("não foi possível restaurar")); }
    }
    @Test public void probePreservesAbsentValues() throws Exception {
        Store store = new Store();
        store.values.clear();
        DnsSettingsWriter.probe(store);
        assertNull(store.read(DnsSettingsWriter.MODE));
        assertNull(store.read(DnsSettingsWriter.HOST));
    }
    @Test public void shellRequiresCompleteSuccessMarker() throws Exception {
        assertEquals("", AdbShellResult.checked("\n" + AdbShellResult.MARKER + "0\n"));
        for (String output : new String[]{"", "closed", "\n" + AdbShellResult.MARKER + "1\n",
                "SecurityException: denied\n" + AdbShellResult.MARKER + "0\n",
                "Error while accessing settings provider\njava.lang.RemoteException: failed\n" + AdbShellResult.MARKER + "0\n"}) {
            try { AdbShellResult.checked(output); fail("Must reject incomplete or denied result"); }
            catch (IOException expected) { }
        }
    }
    @Test public void shellQuotingProtectsSettingValues() {
        assertEquals("'a'\\''b; $(id)'", DnsSettingsWriter.quote("a'b; $(id)"));
    }
}
