package com.minima-AI.ai.mcp.auth;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class McpKeyFilterTests {

    @Mock
    StringRedisTemplate stringRedisTemplate;

    @Mock
    ValueOperations<String, String> valueOps;

    private McpKeyFilter filter;

    private static final String VALID_RAW_KEY = "sk_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij1234567890ab";

    private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440000";

    @BeforeEach
    void setUp() {

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        filter = new McpKeyFilter(stringRedisTemplate);
    }

    @AfterEach
    void tearDown() {

        // ensure context is always clean between tests
        McpUserContext.clear();
    }

    @Test
    void doFilter_nonMcpPath_passesWithoutAuthCheck() throws Exception {

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/ai/classify");

        MockHttpServletResponse res = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);

        verifyNoInteractions(stringRedisTemplate);          // no Redis lookup for non-MCP paths

        assertThat(res.getStatus()).isEqualTo(200); // no 401 written
    }

    @Test
    void doFilter_actuatorPath_passesWithoutAuthCheck() throws Exception {

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");

        MockHttpServletResponse res = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);

        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    void doFilter_mcpPath_missingHeader_returns401() throws Exception {

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp");

        MockHttpServletResponse res = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);

        assertThat(res.getContentType()).contains("application/json");

        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_mcpPath_blankHeader_returns401() throws Exception {

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp");

        req.addHeader("X-MCP-Key", "   ");

        MockHttpServletResponse res = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);

        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_mcpPath_invalidKey_returns401() throws Exception {

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp");

        req.addHeader("X-MCP-Key", "sk_invalidkeyvalue");

        MockHttpServletResponse res = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);

        when(valueOps.get(anyString())).thenReturn(null); // key not in Redis

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);

        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_mcpPath_validKey_forwardsRequestAndSetsContext() throws Exception {

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp");

        req.addHeader("X-MCP-Key", VALID_RAW_KEY);

        MockHttpServletResponse res = new MockHttpServletResponse();

        // compute hash the same way the filter does
        String hash = sha256Hex(VALID_RAW_KEY);

        when(valueOps.get("mcp:key:" + hash)).thenReturn(USER_ID);

        // capturing FilterChain â€” read McpUserContext while chain is executing
        AtomicReference<String> contextDuringChain = new AtomicReference<>();

        FilterChain capturingChain = (rq, rs) -> contextDuringChain.set(McpUserContext.get());

        filter.doFilter(req, res, capturingChain);

        // userId was in context when chain executed
        assertThat(contextDuringChain.get()).isEqualTo(USER_ID);

        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_mcpPath_validKey_clearsContextAfterRequest() throws Exception {

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp");

        req.addHeader("X-MCP-Key", VALID_RAW_KEY);

        MockHttpServletResponse res = new MockHttpServletResponse();

        String hash = sha256Hex(VALID_RAW_KEY);

        when(valueOps.get("mcp:key:" + hash)).thenReturn(USER_ID);

        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        // context must be cleared after request completes (virtual thread safety)
        assertThat(McpUserContext.get()).isNull();
    }

    @Test
    void doFilter_mcpPath_validKey_redisLookupUsesHashNotRawKey() throws Exception {

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp");

        req.addHeader("X-MCP-Key", VALID_RAW_KEY);

        MockHttpServletResponse res = new MockHttpServletResponse();

        when(valueOps.get(anyString())).thenReturn(null); // key not found, doesn't matter

        filter.doFilter(req, res, mock(FilterChain.class));

        // verify Redis was queried with a hash, not with the raw key
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);

        verify(valueOps).get(captor.capture());

        assertThat(captor.getValue()).startsWith("mcp:key:");

        assertThat(captor.getValue()).doesNotContain(VALID_RAW_KEY); // raw key never hits Redis
    }

    @Test
    void doFilter_mcpNestedPath_alsoGated() throws Exception {

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/mcp/tools/list");

        req.addHeader("X-MCP-Key", VALID_RAW_KEY);

        MockHttpServletResponse res = new MockHttpServletResponse();

        String hash = sha256Hex(VALID_RAW_KEY);

        when(valueOps.get("mcp:key:" + hash)).thenReturn(USER_ID);

        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
    }

    /** Mirror of McpKeyFilter.sha256Hex â€” must stay in sync with filter logic. */
    private static String sha256Hex(String input) {

        try {

            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (Exception e) {

            throw new RuntimeException(e);
        }
    }
}

