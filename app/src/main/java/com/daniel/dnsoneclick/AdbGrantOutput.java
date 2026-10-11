package com.daniel.dnsoneclick;

final class AdbGrantOutput {
    static final String RESULT_MARKER = "__DNS1TOQUE_GRANT_RESULT__:";

    private AdbGrantOutput() {
    }

    static Integer getExitCode(String output) {
        if (output == null) return null;
        Integer result = null;
        for (String line : output.split("\\r?\\n")) {
            int marker = line.indexOf(RESULT_MARKER);
            if (marker < 0) continue;
            String value = line.substring(marker + RESULT_MARKER.length()).trim();
            try {
                result = Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                // Ignore partial marker lines when the ADB stream closes mid-response.
            }
        }
        return result;
    }

    static String getResponseText(String output) {
        if (output == null) return "";
        StringBuilder response = new StringBuilder();
        for (String line : output.split("\\r?\\n")) {
            if (line.contains(RESULT_MARKER)) continue;
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (response.length() > 0) response.append(' ');
            response.append(trimmed);
        }
        return response.toString();
    }
}
