package com.example.supportops.module.auth.service;

import com.example.supportops.module.auth.model.dto.LoginDTO;
import com.example.supportops.module.auth.model.dto.RegisterDTO;
import com.example.supportops.module.auth.model.vo.TokenVO;
import com.example.supportops.module.auth.model.vo.UserVO;

public interface AuthService {
    TokenVO login(LoginDTO loginDTO, String clientIp);

    UserVO register(RegisterDTO registerDTO);

    UserVO currentUser(String username);
}
