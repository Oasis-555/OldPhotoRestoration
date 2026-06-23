package com.photorestoration.repository;

import com.photorestoration.entity.User;
import org.apache.ibatis.annotations.*;

import java.util.Optional;

@Mapper
public interface UserRepository {
    @Select("SELECT * FROM users WHERE user_name = #{userName} LIMIT 1")
    Optional<User> findByUserName(String userName);

    @Select("SELECT * FROM users WHERE email = #{email} LIMIT 1")
    Optional<User> findByEmail(String email);

    @Select("SELECT * FROM users WHERE user_id = #{userId} LIMIT 1")
    Optional<User> findByUserId(Long userId);

    @Select("SELECT * FROM users WHERE user_name = #{account} OR email = #{account} LIMIT 1")
    Optional<User> findByUserNameOrEmail(String account);

    @Select("SELECT COUNT(1) > 0 FROM users WHERE user_name = #{userName}")
    boolean existsByUserName(String userName);

    @Select("SELECT COUNT(1) > 0 FROM users WHERE LOWER(email) = LOWER(#{email})")
    boolean existsByEmailIgnoreCase(String email);

    @Insert("INSERT INTO users (user_id, user_name, password, email, user_type, phone, avatar_url, bio, is_active, create_time, update_time, last_login) " +
            "VALUES (#{userId}, #{userName}, #{password}, #{email}, #{userType}, #{phone}, #{avatarUrl}, #{bio}, #{isActive}, #{createTime}, #{updateTime}, #{lastLogin})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Update("UPDATE users SET user_id=#{userId}, user_name=#{userName}, password=#{password}, email=#{email}, user_type=#{userType}, " +
            "phone=#{phone}, avatar_url=#{avatarUrl}, bio=#{bio}, is_active=#{isActive}, create_time=#{createTime}, update_time=#{updateTime}, last_login=#{lastLogin} " +
            "WHERE id=#{id}")
    int update(User user);

    default User save(User user) {
        if (user.getId() == null) {
            insert(user);
        } else {
            update(user);
        }
        return user;
    }
}
