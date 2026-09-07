package com.minima-AI.ai.mcp.tools;

import com.minima-AI.ai.mcp.auth.McpUserContext;
import com.minima-AI.ai.operations.AnalyticsOperationsService;
import com.minima-AI.ai.operations.ResilientAnalyticsOps;
import com.minima-AI.ai.operations.ResilientUrlOps;
import com.minima-AI.ai.operations.UrlOperationsService;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class McpAnalyticsToolsTests {

    @Mock
    ResilientAnalyticsOps resilientAnalyticsOps;

    @Mock
    ResilientUrlOps resilientUrlOps;

    McpAnalyticsTools mcpAnalyticsTools;

    private static final String USER_ID = "user-123";

    @BeforeEach
    void setUp() {

        mcpAnalyticsTools = new McpAnalyticsTools(resilientAnalyticsOps, resilientUrlOps);
        McpUserContext.set(USER_ID);
    }

    @AfterEach
    void tearDown() {
        McpUserContext.clear();
    }

    private static String text(CallToolResult result) {

        return result.content().stream()
                .filter(c -> c instanceof TextContent)
                .map(c -> ((TextContent) c).text())
                .collect(Collectors.joining());
    }

    private static boolean isError(CallToolResult result) {
        return Boolean.TRUE.equals(result.isError());
    }

    @Test
    void getUrlStats_noAuthenticatedUser_returnsInternalErrorResult() {

        McpUserContext.clear();

        CallToolResult result = mcpAnalyticsTools.getUrlStats("abc123");

        assertThat(isError(result)).isTrue();
        assertThat(text(result)).contains("Internal error");
        verifyNoInteractions(resilientUrlOps, resilientAnalyticsOps);
    }

    @Test
    void getUrlStats_success_returnsFormattedMessageAndStructuredContent() {

        UrlOperationsService.UrlDetails details =
                new UrlOperationsService.UrlDetails(1L, "abc123", "https://example.com", "http://short.ly/abc123", 0L);

        when(resilientUrlOps.getDetails("abc123", USER_ID))
                .thenReturn(CompletableFuture.completedFuture(details));

        when(resilientAnalyticsOps.getStats(1L, USER_ID))
                .thenReturn(CompletableFuture.completedFuture(
                        new AnalyticsOperationsService.StatsResult(1L, 42L, "ok")
                ));

        CallToolResult result = mcpAnalyticsTools.getUrlStats("abc123");

        assertThat(isError(result)).isFalse();
        assertThat(text(result)).contains("abc123").contains("42");
        assertThat(result.structuredContent())
                .isEqualTo(new McpAnalyticsTools.UrlStatsPayload("abc123", 42L));
    }

    @Test
    void getUrlStats_slugNotFound_returnsNotFoundMessage() {

        when(resilientUrlOps.getDetails("missing", USER_ID))
                .thenReturn(CompletableFuture.completedFuture(null));

        CallToolResult result = mcpAnalyticsTools.getUrlStats("missing");

        assertThat(isError(result)).isTrue();
        assertThat(text(result)).contains("Could not find");
    }

    @Test
    void getUrlStats_statsUnavailable_returnsUnavailableMessage() {

        UrlOperationsService.UrlDetails details =
                new UrlOperationsService.UrlDetails(1L, "abc123", "https://example.com", "http://short.ly/abc123", 0L);

        when(resilientUrlOps.getDetails("abc123", USER_ID))
                .thenReturn(CompletableFuture.completedFuture(details));

        when(resilientAnalyticsOps.getStats(1L, USER_ID))
                .thenReturn(CompletableFuture.completedFuture(null));

        CallToolResult result = mcpAnalyticsTools.getUrlStats("abc123");

        assertThat(isError(result)).isTrue();
        assertThat(text(result)).contains("temporarily unavailable");
    }

    @Test
    void getUrlStats_httpClientError_returnsCleanMessage() {

        HttpClientErrorException httpEx = HttpClientErrorException.create(
                org.springframework.http.HttpStatus.NOT_FOUND, "Not Found",
                null, null, null
        );

        when(resilientUrlOps.getDetails("abc123", USER_ID))
                .thenThrow(new CompletionException(httpEx));

        CallToolResult result = mcpAnalyticsTools.getUrlStats("abc123");

        assertThat(isError(result)).isTrue();
        assertThat(text(result)).contains("Could not retrieve stats");
    }

    @Test
    void getTopUrls_noAuthenticatedUser_returnsInternalErrorResult() {

        McpUserContext.clear();

        CallToolResult result = mcpAnalyticsTools.getTopUrls(5);

        assertThat(isError(result)).isTrue();
        verifyNoInteractions(resilientAnalyticsOps);
    }

    @Test
    void getTopUrls_failure_returnsUnavailableMessage() {

        when(resilientAnalyticsOps.getTopUrls(5, USER_ID))
                .thenReturn(CompletableFuture.completedFuture(null));

        CallToolResult result = mcpAnalyticsTools.getTopUrls(5);

        assertThat(isError(result)).isTrue();
        assertThat(text(result)).contains("temporarily unavailable");
    }

    @Test
    void getTopUrls_noData_returnsNoDataMessage() {

        when(resilientAnalyticsOps.getTopUrls(5, USER_ID))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        CallToolResult result = mcpAnalyticsTools.getTopUrls(5);

        assertThat(isError(result)).isFalse();
        assertThat(text(result)).contains("don't have any URLs");
        assertThat(result.structuredContent()).isEqualTo(List.of());
    }

    @Test
    void getTopUrls_withData_returnsFormattedListAndStructuredContent() {

        when(resilientAnalyticsOps.getTopUrls(5, USER_ID))
                .thenReturn(CompletableFuture.completedFuture(
                        List.of(new AnalyticsOperationsService.TopUrlResult(1L, 99L))
                ));

        CallToolResult result = mcpAnalyticsTools.getTopUrls(5);

        assertThat(isError(result)).isFalse();
        assertThat(text(result)).contains("urlId 1").contains("99 clicks");
        assertThat(result.structuredContent())
                .isEqualTo(List.of(new McpAnalyticsTools.TopUrlPayload(1L, 99L)));
    }
}

