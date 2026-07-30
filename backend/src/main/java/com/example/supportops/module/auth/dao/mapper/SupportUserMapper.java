package com.example.supportops.module.auth.dao.mapper;

import com.example.supportops.module.auth.dao.dataobject.SupportUserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;

import org.apache.ibatis.annotations.Select;

@Mapper
public interface SupportUserMapper {

    @Select("""
            SELECT id, username, password_hash AS passwordHash, display_name AS displayName,
                   role_code AS roleCode, status, daily_quota AS dailyQuota
              FROM support_users
             WHERE username = #{username}
            """)
    SupportUserDO selectByUsername(@Param("username") String username);

    /** 公开注册只创建客户账号；客服账号必须由管理员人员管理模块创建。 */
    @Insert("""
            INSERT INTO support_users
              (username, password_hash, display_name, role_code, status, daily_quota)
            VALUES
              (#{username}, #{passwordHash}, #{displayName}, 'CUSTOMER', 'ACTIVE', 10)
            """)
    int insertRegisteredUser(@Param("username") String username,
                             @Param("passwordHash") String passwordHash,
                             @Param("displayName") String displayName);

    @Insert("""
            INSERT INTO customers (customer_no, customer_name, status)
            VALUES (#{customerNo}, #{displayName}, 'ACTIVE')
            """)
    int insertRegisteredCustomer(@Param("customerNo") String customerNo,
                                 @Param("displayName") String displayName);

    @Insert("""
            INSERT INTO customer_accounts (user_id, customer_id)
            SELECT #{userId}, id FROM customers WHERE customer_no = #{customerNo}
            """)
    int bindRegisteredCustomer(@Param("userId") long userId,
                               @Param("customerNo") String customerNo);

}
