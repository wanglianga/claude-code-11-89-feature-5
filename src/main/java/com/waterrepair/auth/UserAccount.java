package com.waterrepair.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 值守/班组账号。仅用于接口鉴权与测试账号体系，不承接细粒度权限模型。
 */
@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    @Column(length = 64)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 64)
    private String displayName;

    /** DISPATCHER / CREW / VIEWER */
    @Column(nullable = false, length = 32)
    private String role;

    /** CREW 角色可绑定到具体班组编码，便于回执归属校验与展示 */
    @Column(length = 64)
    private String crewCode;

    public UserAccount() {
    }

    public UserAccount(String username, String passwordHash, String displayName, String role, String crewCode) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.crewCode = crewCode;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRole() {
        return role;
    }

    public String getCrewCode() {
        return crewCode;
    }
}
