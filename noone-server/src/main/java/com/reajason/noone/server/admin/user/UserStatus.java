package com.reajason.noone.server.admin.user;

public enum UserStatus {
    /**
     * 用户已完成注册验证，可以正常登录和使用系统的所有功能
     */
    ENABLED,
    /**
     * 通常用于违规处理、风险控制或管理需要
     */
    DISABLED,
    /**
     * 账号因安全原因被临时锁定,通常是自动触发，可通过特定流程（如密码重置、身份验证）解锁
     */
    LOCKED,
    /**
     * 账号已创建但尚未完成激活流程,新用户注册后等待点击激活链接或输入验证码
     */
    UNACTIVATED
}
