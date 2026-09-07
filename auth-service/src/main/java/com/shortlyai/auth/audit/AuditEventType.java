package com.minima-AI.auth.audit;

// All security events we track. add new values freely (stored as STRING in DB)
// Never remove values - old DB rows still reference them
public enum AuditEventType {
    LOGIN_SUCCESS,    // valid credentials
    LOGIN_FAILED,     // wrong password or email not found
    REGISTER,         // new account created
    LOGOUT,           // refresh token deleted
    TOKEN_REFRESH,    // new access token issued
    OAUTH2_LOGIN,     // Google OAuth2 flow completed
    REGISTER_FAILED,   // Email already exists
    PASSWORD_RESET_REQUESTED, // forgot password
    PASSWORD_RESET_COMPLETED, // forgot password completed
}

