package com.minima-AI.auth.user;

// Sealed â€” only these 3 roles exist, compiler enforces exhaustive switch
public enum Role {
    ROLE_FREE,   // default â€” limited URLs, no analytics
    ROLE_PRO,    // paid â€” unlimited URLs, full analytics
    ROLE_ADMIN   // internal â€” full access
}

