package com.example.supportops.module.trace.integration;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** 生成不携带业务含义、不可枚举且带输入校验码的对外溯源码。 */
@Component
public class TraceCodeGenerator {
    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder body = new StringBuilder(12);
        for (int index = 0; index < 12; index++) {
            body.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return "TR1-" + body + "-" + checksum(body);
    }

    private String checksum(CharSequence body) {
        int value = 0;
        for (int index = 0; index < body.length(); index++) {
            value = (value * 31 + body.charAt(index)) % 97;
        }
        return String.format("%02d", value);
    }
}
