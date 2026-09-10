package com.tongji.auth.model;

// 枚举类, 用于验证用户是用手机号还是邮箱登录
public enum IdentifierType {
    PHONE,
    EMAIL;

    public static IdentifierType fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("identifier type required");
        }
        return switch (value.toLowerCase()) {
            case "phone", "mobile" -> PHONE;
            case "email" -> EMAIL;
            default -> throw new IllegalArgumentException("Unsupported identifier type: " + value);
        };
    }
}
