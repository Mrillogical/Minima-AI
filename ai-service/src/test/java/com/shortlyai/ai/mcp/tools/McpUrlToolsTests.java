package com.minima-AI.ai.mcp.tools;

import com.minima-AI.ai.mcp.auth.McpUserContext;
import com.minima-AI.ai.operations.ResilientUrlOps;
import com.minima-AI.ai.operations.UrlOperationsService;
import io.modelcontextprotocol.spec.McpSchema;
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
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.ai.mcp.annotation.context.StructuredElicitResult;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class McpUrlToolsTests {

    @Mock
    ResilientUrlOps resilientUrlOps;

    @Mock
    McpSyncRequestContext context;

    McpUrlTools mcpUrlTools;

    private static final String USER_ID = "user-123";

    @BeforeEach
    void setUp() {

        mcpUrlTools = new McpUrlTools(resilientUrlOps);
        McpUserContext.set(USER_ID);
    }

    @AfterEach
    void tearDown() {
        McpUserContext.clear();
    }

    // CallToolResult can carry multiple Content blocks - concatenate the text ones
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
    void shortenUrl_noAuthenticatedUser_returnsInternalErrorResult() {

        McpUserContext.clear(); // simulate filter misconfiguration

        CallToolResult result = mcpUrlTools.shortenUrl("https://example.com", context);

        assertThat(isError(result)).isTrue();
        assertThat(text(result)).contains("Internal error");
        verifyNoInteractions(resilientUrlOps);
    }

    @Test
    void shortenUrl_elicitationEnabled_invalidUrlFormat_returnsGuardMessageWithoutCallingService() {

        when(context.elicitEnabled()).thenReturn(true);

        CallToolResult result = mcpUrlTools.shortenUrl("not-a-url", context);

        assertThat(isError(result)).isTrue();
        assertThat(text(result)).contains("does not look like a valid URL");
        verifyNoInteractions(resilientUrlOps);
    }

    @Test
    void shortenUrl_success_returnsFormattedShortUrlAndStructuredContent() {

        UrlOperationsService.ShortenResult shortenResult =
                new UrlOperationsService.ShortenResult(
                        7L,
                        "abc123",
                        "http://short.ly/abc123"
                );

        when(resilientUrlOps.shorten("https://example.com", USER_ID))
                .thenReturn(CompletableFuture.completedFuture(shortenResult));

        CallToolResult result =
                mcpUrlTools.shortenUrl("https://example.com", context);

        assertThat(isError(result)).isFalse();

        assertThat(text(result))
                .isEqualTo("Short URL created: http://short.ly/abc123 (slug: abc123)");

        assertThat(result.structuredContent())
                .isEqualTo(
                        new McpUrlTools.ShortenPayload(
                                "http://short.ly/abc123",
                                7L,
                                "abc123"
                        )
                );

        verify(resilientUrlOps).shorten("https://example.com", USER_ID);
    }

    @Test
    void shortenUrl_circuitBreakerOpenReturnsNull_returnsFriendlyUnavailableMessage() {

        when(context.elicitEnabled()).thenReturn(false);

        when(resilientUrlOps.shorten("https://example.com", USER_ID))
                .thenReturn(CompletableFuture.completedFuture(null));

        CallToolResult result = mcpUrlTools.shortenUrl("https://example.com", context);

        assertThat(isError(result)).isTrue();
        assertThat(text(result)).contains("temporarily unavailable");
    }

    @Test
    void shortenUrl_downstream4xx_returnsCleanMessageInsteadOfThrowing() {

        when(context.elicitEnabled()).thenReturn(false);

        HttpClientErrorException httpEx =
                HttpClientErrorException.create(HttpStatusCode.valueOf(400), "Bad Request", null, null, null);

        CompletableFuture<UrlOperationsService.ShortenResult> failed = new CompletableFuture<>();
        failed.completeExceptionally(httpEx);

        when(resilientUrlOps.shorten("https://example.com", USER_ID)).thenReturn(failed);

        CallToolResult result = mcpUrlTools.shortenUrl("https://example.com", context);

        assertThat(isError(result)).isTrue();
        assertThat(text(result)).contains("Could not shorten URL");
    }

    @Test
    void shortenUrl_downstreamNon4xxFailure_rethrowsCompletionException() {

        when(context.elicitEnabled()).thenReturn(false);

        CompletableFuture<UrlOperationsService.ShortenResult> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("connection reset"));

        when(resilientUrlOps.shorten("https://example.com", USER_ID)).thenReturn(failed);

        assertThatThrownBy(() -> mcpUrlTools.shortenUrl("https://example.com", context))
                .isInstanceOf(CompletionException.class);
    }

    @Test
    void getUrlDetails_noAuthenticatedUser_returnsInternalErrorResult() {

        McpUserContext.clear();

        CallToolResult result = mcpUrlTools.getUrlDetails("myslug");

        assertThat(isError(result)).isTrue();
        verifyNoInteractions(resilientUrlOps);
    }

    @Test
    void getUrlDetails_success_returnsFormattedDetailsAndStructuredContent() {

        UrlOperationsService.UrlDetails details =
                new UrlOperationsService.UrlDetails(3L, "myslug", "https://x.com", "http://short.ly/myslug", 42L);

        when(resilientUrlOps.getDetails("myslug", USER_ID))
                .thenReturn(CompletableFuture.completedFuture(details));

        CallToolResult result = mcpUrlTools.getUrlDetails("myslug");

        assertThat(isError(result)).isFalse();
        assertThat(text(result)).contains("urlId: 3").contains("clicks: 42");
        assertThat(result.structuredContent())
                .isEqualTo(new McpUrlTools.UrlDetailsPayload(3L, "myslug", "https://x.com", "http://short.ly/myslug", 42L));
    }

    @Test
    void getUrlDetails_notFound_returnsUnavailableMessage() {

        when(resilientUrlOps.getDetails("ghost", USER_ID))
                .thenReturn(CompletableFuture.completedFuture(null));

        CallToolResult result = mcpUrlTools.getUrlDetails("ghost");

        assertThat(isError(result)).isTrue();
        assertThat(text(result)).contains("temporarily unavailable");
    }

    @Test
    void deleteUrl_noAuthenticatedUser_returnsInternalErrorResult() {

        McpUserContext.clear();

        CallToolResult result = mcpUrlTools.deleteUrl("toDelete", context);

        assertThat(isError(result)).isTrue();
        verifyNoInteractions(resilientUrlOps);
    }

    @Test
    void deleteUrl_slugNotFound_returnsMessageWithoutAttemptingDelete() {

        when(resilientUrlOps.getDetails("ghost", USER_ID))
                .thenReturn(CompletableFuture.completedFuture(null));

        CallToolResult result = mcpUrlTools.deleteUrl("ghost", context);

        assertThat(isError(result)).isTrue();
        assertThat(text(result)).contains("Could not find a URL");
        verify(resilientUrlOps, never()).delete(any(), any());
    }

    @Test
    void deleteUrl_noElicitationSupport_refusesDeletion() {

        UrlOperationsService.UrlDetails details =
                new UrlOperationsService.UrlDetails(
                        4L,
                        "toDelete",
                        "https://x.com",
                        "http://short.ly/toDelete",
                        0L
                );

        when(resilientUrlOps.getDetails("toDelete", USER_ID))
                .thenReturn(CompletableFuture.completedFuture(details));

        when(context.elicitEnabled()).thenReturn(false);

        CallToolResult result = mcpUrlTools.deleteUrl("toDelete", context);

        assertThat(isError(result)).isTrue();

        assertThat(text(result))
                .contains("NOT deleted")
                .contains("can't show a confirmation prompt");

        assertThat(result.structuredContent())
                .isEqualTo(new McpUrlTools.DeletePayload("toDelete", false));

        verify(resilientUrlOps, never()).delete(anyString(), anyString());
    }

    @Test
    void deleteUrl_deleteCallFails_returnsUnavailableMessage() {

        UrlOperationsService.UrlDetails details =
                new UrlOperationsService.UrlDetails(
                        4L,
                        "toDelete",
                        "https://x.com",
                        "http://short.ly/toDelete",
                        0L
                );

        @SuppressWarnings("unchecked")
        StructuredElicitResult<McpUrlTools.DeleteConfirmation> confirmation =
                mock(StructuredElicitResult.class);

        when(resilientUrlOps.getDetails("toDelete", USER_ID))
                .thenReturn(CompletableFuture.completedFuture(details));

        when(context.elicitEnabled()).thenReturn(true);

        when(context.elicit(any(), eq(McpUrlTools.DeleteConfirmation.class)))
                .thenReturn(confirmation);

        when(confirmation.action()).thenReturn(McpSchema.ElicitResult.Action.ACCEPT);

        when(confirmation.structuredContent())
                .thenReturn(new McpUrlTools.DeleteConfirmation(true));

        when(resilientUrlOps.delete("toDelete", USER_ID))
                .thenReturn(CompletableFuture.completedFuture(false));

        CallToolResult result = mcpUrlTools.deleteUrl("toDelete", context);

        assertThat(isError(result)).isTrue();
        assertThat(text(result))
                .contains("temporarily unavailable");

        verify(resilientUrlOps).delete("toDelete", USER_ID);
    }

}

