package com.example.supportops.module.auth.service.impl;

import com.example.supportops.common.exception.BusinessException;
import com.example.supportops.common.exception.ErrorCode;
import com.example.supportops.infrastructure.security.JwtTokenProvider;
import com.example.supportops.module.auth.convert.UserConvert;
import com.example.supportops.module.auth.manager.UserManager;
import com.example.supportops.module.auth.model.bo.UserBO;
import com.example.supportops.module.auth.model.dto.LoginDTO;
import com.example.supportops.module.auth.model.dto.RegisterDTO;
import com.example.supportops.module.auth.model.vo.TokenVO;
import com.example.supportops.module.auth.model.vo.UserVO;
import com.example.supportops.module.auth.service.AuthService;
import com.example.supportops.module.auth.security.LoginIpAccountGuard;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthServiceImpl implements AuthService {
    private final UserManager userManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginIpAccountGuard loginIpAccountGuard;

    public AuthServiceImpl(
            UserManager userManager,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            LoginIpAccountGuard loginIpAccountGuard
    ) {
        this.userManager = userManager;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.loginIpAccountGuard = loginIpAccountGuard;
    }

    @Override
    public TokenVO login(LoginDTO loginDTO, String clientIp) {
        UserBO user = userManager.findByUsername(normalizeUsername(loginDTO.username()))
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误"));
        if (!"ACTIVE".equals(user.status()) || !passwordEncoder.matches(loginDTO.password(), user.passwordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        // 只有凭证验证成功后才记录，错误密码不会消耗该 IP 的账号名额。
        loginIpAccountGuard.checkAndRecord(clientIp, user.username());
        return new TokenVO(jwtTokenProvider.issue(user.id(), user.username(), user.roleCode()),
                "Bearer", jwtTokenProvider.expirationSeconds());
    }

    @Override
    @Transactional
    public UserVO register(RegisterDTO registerDTO) {
        String username = normalizeUsername(registerDTO.username());
        try {
            // 明文密码只在当前调用栈中存在，进入数据层前立即转换为不可逆 BCrypt 哈希。
            UserBO user = userManager.createRegisteredUser(username,
                    passwordEncoder.encode(registerDTO.password()), registerDTO.displayName().trim());
            return UserConvert.toVO(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        }
    }

    @Override
    public UserVO currentUser(String username) {
        UserBO user = userManager.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return UserConvert.toVO(user);
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
