package com.attendance.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class IdGenerator {
    
    private static final Logger log = LoggerFactory.getLogger(IdGenerator.class);

    public static String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String generateTaskId() {
        return "T" + System.currentTimeMillis() + generateShortId();
    }

    private static String generateShortId() {
        return Long.toHexString(System.nanoTime()).toUpperCase();
    }
}