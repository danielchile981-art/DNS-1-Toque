package com.daniel.dnsoneclick;

final class DnsActivation {
    interface Grant {
        void attempt() throws Exception;
        boolean isGranted();
    }
    static boolean activate(Grant grant, DnsSettingsWriter.Store shell) throws Exception {
        try { grant.attempt(); } catch (java.io.IOException denied) { }
        if (grant.isGranted()) return true;
        DnsSettingsWriter.probe(shell);
        return false;
    }
}
