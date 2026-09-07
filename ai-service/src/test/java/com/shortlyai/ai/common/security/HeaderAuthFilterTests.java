package com.minima-AI.ai.common.security;

import com.minima-AI.ai.common.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeaderAuthFilterTests {

    @Mock
    JsonMapper jsonMapper;

    @InjectMocks
    HeaderAuthFilter filter;

    @BeforeEach
    void setUp() {

        filter = new HeaderAuthFilter(jsonMapper);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_mcpPath_skipsAuth() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mcp/tools");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200); // untouched
    }

    @Test
    void doFilter_actuatorPath_skipsAuth() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_apiDocsPath_skipsAuth() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v3/api-docs");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_missingUserIdHeader_returns401_chainNeverCalled() throws Exception {

        when(jsonMapper.writeValueAsString(any(ErrorResponse.class)))
                .thenReturn("""
                {"message":"Missing X-User-Id header"}
                """);

        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/ai/agent");

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .contains("Missing X-User-Id header");

        verifyNoInteractions(chain);
    }

    @Test
    void doFilter_blankUserIdHeader_returns401() throws Exception {

        when(jsonMapper.writeValueAsString(any(ErrorResponse.class)))
                .thenReturn("""
                {"message":"Missing X-User-Id header"}
                """);

        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/ai/agent");

        request.addHeader("X-User-Id", "   ");

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .contains("Missing X-User-Id header");

        verifyNoInteractions(chain);
    }

    @Test
    void doFilter_validUserId_setsAuthenticationAndClearsAfter() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/ai/agent");
        request.addHeader("X-User-Id", "user-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] authNameDuringChain = new String[1];

        FilterChain chain = (req, res) ->
                authNameDuringChain[0] = SecurityContextHolder.getContext()
                        .getAuthentication()
                        .getName();

        filter.doFilterInternal(request, response, chain);

        assertThat(authNameDuringChain[0]).isEqualTo("user-123");
        // context must be cleared after the filter chain returns
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}

