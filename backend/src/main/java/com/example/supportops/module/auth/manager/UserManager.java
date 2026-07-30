package com.example.supportops.module.auth.manager;

import com.example.supportops.module.auth.convert.UserConvert;
import com.example.supportops.module.auth.dao.dataobject.SupportUserDO;
import com.example.supportops.module.auth.dao.mapper.SupportUserMapper;
import com.example.supportops.module.auth.model.bo.UserBO;
import com.example.supportops.common.exception.BusinessException;
import com.example.supportops.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class UserManager {
    private final SupportUserMapper supportUserMapper;

    public UserManager(SupportUserMapper supportUserMapper) {
        this.supportUserMapper = supportUserMapper;
    }

    public Optional<UserBO> findByUsername(String username) {
        SupportUserDO user = supportUserMapper.selectByUsername(username);
        return Optional.ofNullable(user).map(UserConvert::toBO);
    }

    /** SecurityContext 保存用户名，业务落库前在此统一解析内部用户主键。 */
    public UserBO getRequiredByUsername(String username) {
        return findByUsername(username).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    public UserBO createRegisteredUser(String username, String passwordHash, String displayName) {
        supportUserMapper.insertRegisteredUser(username, passwordHash, displayName);
        UserBO user = getRequiredByUsername(username);
        String customerNo = "CUST-%010d".formatted(user.id());
        supportUserMapper.insertRegisteredCustomer(customerNo, displayName);
        supportUserMapper.bindRegisteredCustomer(user.id(), customerNo);
        return user;
    }

}
