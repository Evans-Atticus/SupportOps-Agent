package com.example.supportops.module.auth.convert;

import com.example.supportops.module.auth.dao.dataobject.SupportUserDO;
import com.example.supportops.module.auth.model.bo.UserBO;
import com.example.supportops.module.auth.model.vo.UserVO;

import java.util.List;

public final class UserConvert {
    private UserConvert() {
    }

    public static UserBO toBO(SupportUserDO source) {
        return new UserBO(source.id(), source.username(), source.passwordHash(), source.displayName(),
                source.roleCode(), source.status(), source.dailyQuota());
    }

    public static UserVO toVO(UserBO source) {
        return new UserVO(source.id(), source.username(), source.displayName(), List.of(source.roleCode()));
    }
}
