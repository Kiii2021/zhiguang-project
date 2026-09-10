package com.tongji.user.mapper;


import com.tongji.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.List;

@Mapper
public interface UserMapper {

    // @Param用于声明Mapper.xml中的#{}引用的对象
    User findByPhone(@Param("phone") String phone);

    User findByEmail(@Param("email") String email);

    User findById(@Param("id") long id);

    boolean existsByPhone(@Param("phone") String phone);

    boolean existsByEmail(@Param("email") String email);

    void insert(User user);

    void updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    void updateProfile(@Param("zgId") User user);

    boolean existsByZgIdExceptId(@Param("zgId") String zgId, @Param("excludeId") Long excludeId);

    List<User> listByIds(@Param("ids") List<Long> ids);
}
