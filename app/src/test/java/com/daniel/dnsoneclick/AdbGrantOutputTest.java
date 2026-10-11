package com.daniel.dnsoneclick;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AdbGrantOutputTest {
    @Test
    public void parsesSuccessfulGrantResponse() {
        String output = "Permission granted\\n" + AdbGrantOutput.RESULT_MARKER + "0\\n";
        assertEquals(Integer.valueOf(0), AdbGrantOutput.getExitCode(output));
        assertEquals("Permission granted", AdbGrantOutput.getResponseText(output));
    }

    @Test
    public void parsesAndroidRefusalAndKeepsDiagnostic() {
        String output = "Exception occurred\\n" + AdbGrantOutput.RESULT_MARKER + "1\\n";
        assertEquals(Integer.valueOf(1), AdbGrantOutput.getExitCode(output));
        assertEquals("Exception occurred", AdbGrantOutput.getResponseText(output));
    }

    @Test
    public void incompleteCloseMarkerIsNotMistakenForSuccess() {
        assertNull(AdbGrantOutput.getExitCode(AdbGrantOutput.RESULT_MARKER));
    }
}
