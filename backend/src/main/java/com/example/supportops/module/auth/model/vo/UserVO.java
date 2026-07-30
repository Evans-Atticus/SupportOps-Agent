package com.example.supportops.module.auth.model.vo;

import java.util.List;

public record UserVO(Long id, String username, String displayName, List<String> roles) {
}
