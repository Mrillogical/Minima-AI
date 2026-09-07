package com.minima-AI.ai.mcp.auth;

// ThreadLocal holder - carries authenticated userId from McpKeyFilter into MCP tool methods.
// Virtual threads (which this service uses) get their own ThreadLocal slot per thread.
// CRITICAL: filter's finally block MUST call clear() - threads go back to pool after request,
// stale userId would bleed into the next unrelated request on that thread.
public final class McpUserContext {

    // ThreadLocal - each thread has its own copy of this value, invisible to other threads
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();

    // Utility class - no instances
    private McpUserContext() {}

    // Called by McpKeyFilter after validating the API key
    public static void set(String userId) {

        USER_ID.set(userId);
    }

    // Called by MCP tool methods to get the authenticated userId
    public static String get() {

        return USER_ID.get();
    }

    // Called by McpKeyFilter in finally block - ALWAYS, even on exceptions
    public static void clear() {

        USER_ID.remove(); // remove() is correct - set(null) leaves a null entry, remove() eliminates it
    }
}

