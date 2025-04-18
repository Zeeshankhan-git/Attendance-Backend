package com.zeeshan.utils;


public class Env {
    public static String get(String key) {
        return System.getenv(key);
    }

    public static String get(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }
}

