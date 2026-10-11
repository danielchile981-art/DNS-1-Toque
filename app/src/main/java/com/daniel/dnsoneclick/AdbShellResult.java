package com.daniel.dnsoneclick;

import java.io.IOException;

final class AdbShellResult {
    static final String MARKER = "__DNS1TOQUE_SHELL_EXIT__:";
    static String checked(String output) throws IOException {
        int pos = output.lastIndexOf("\n" + MARKER);
        if (pos < 0) throw new IOException("ADB encerrou sem confirmar o comando.");
        String body = output.substring(0, pos).trim();
        String status = output.substring(pos + 1 + MARKER.length()).trim();
        if (!"0".equals(status) || body.contains("SecurityException")
                || body.contains("Exception:") || body.contains("Exception occurred")
                || body.startsWith("Error") || body.startsWith("Can't") || body.contains("Permission denial"))
            throw new IOException("O sistema recusou o comando ADB: " + body);
        return body;
    }
}
