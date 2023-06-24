package org.apache.camel.component.smev3.utils;

import com.fasterxml.uuid.EthernetAddress;
import com.fasterxml.uuid.TimestampSynchronizer;
import com.fasterxml.uuid.UUIDTimer;
import com.fasterxml.uuid.impl.TimeBasedGenerator;

import java.io.IOException;
import java.util.Random;
import java.util.UUID;

public class UUIDGenerator {
    private static final TimeBasedGenerator uuidGenerator;

    public static String generateMessageUUID() {
        return uuidGenerator.generate().toString();
    }

    public static String generateAttachmentUUID() {
        return UUID.randomUUID().toString();
    }

    static {
        Random random = new Random(System.currentTimeMillis());

        UUIDTimer timer;
        try {
            timer = new UUIDTimer(random, (TimestampSynchronizer)null);
        } catch (IOException var3) {
            throw new RuntimeException(var3);
        }

        EthernetAddress addr = EthernetAddress.fromInterface();
        uuidGenerator = new TimeBasedGenerator(addr, timer);
    }
}
