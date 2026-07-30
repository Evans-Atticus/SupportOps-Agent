package com.example.supportops.module.auth.model.vo;

public record TokenVO(String accessToken, String tokenType, long expiresIn) {
}
