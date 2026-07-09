package com.example.androiddualcast.receiver.adb;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;

public final class AdbMessageTest {
    @Test
    public void stringPayloadAddsTerminatingNull() {
        assertArrayEquals(new byte[] {'h', 'o', 's', 't', ':', ':', 0},
                AdbMessage.stringPayload("host::"));
    }

    @Test
    public void messageRoundTripPreservesHeaderAndPayload() throws Exception {
        byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
        AdbMessage original = AdbMessage.of(AdbMessage.CNXN, AdbMessage.VERSION,
                AdbMessage.MAX_DATA, payload);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        original.write(new DataOutputStream(bytes));

        AdbMessage parsed = AdbMessage.read(new DataInputStream(
                new ByteArrayInputStream(bytes.toByteArray())));

        assertEquals(AdbMessage.CNXN, parsed.command);
        assertEquals(AdbMessage.VERSION, parsed.arg0);
        assertEquals(AdbMessage.MAX_DATA, parsed.arg1);
        assertArrayEquals(payload, parsed.payload);
    }
}

