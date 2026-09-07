package com.minima-AI.ai.mcp.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpUserContextTests {

    @AfterEach
    void tearDown() {
        McpUserContext.clear();
    }

    @Test
    void setAndGet_returnsSameValue() {

        McpUserContext.set("user-1");

        assertThat(McpUserContext.get()).isEqualTo("user-1");
    }

    @Test
    void get_beforeSet_returnsNull() {

        assertThat(McpUserContext.get()).isNull();
    }

    @Test
    void clear_removesValue() {

        McpUserContext.set("user-1");
        McpUserContext.clear();

        assertThat(McpUserContext.get()).isNull();
    }

    @Test
    void threadLocal_isolatedPerThread() throws InterruptedException {

        McpUserContext.set("main-thread-user");

        String[] otherThreadValue = new String[1];

        Thread t = new Thread(() -> otherThreadValue[0] = McpUserContext.get());
        t.start();
        t.join();

        assertThat(otherThreadValue[0]).isNull();
        assertThat(McpUserContext.get()).isEqualTo("main-thread-user");
    }
}

