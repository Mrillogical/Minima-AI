package com.minima-AI.ai.mcp.tools;

import com.minima-AI.ai.mcp.auth.McpUserContext;
import com.minima-AI.ai.operations.ResilientUrlOps;
import com.minima-AI.ai.operations.UrlOperationsService;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.ai.mcp.annotation.context.StructuredElicitResult;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.concurrent.CompletionException;

@Component
@RequiredArgsConstructor
@Slf4j
public class McpUrlTools {

    private final ResilientUrlOps resilientUrlOps;

    // structured payloads - dual text+structured CallToolResult means
    // structured-content-aware clients get typed data, not just a sentence to reparse.
    public record ShortenPayload(String shortUrl, long urlId, String slug) {}

    public record UrlDetailsPayload(long urlId, String slug, String originalUrl, String shortUrl, long clickCount) {}

    public record DeletePayload(String slug, boolean deleted) {}

    // returns null instead of throwing when auth context missing
    private String authenticatedUserIdOrNull() {

        return McpUserContext.get();
    }

    private CallToolResult internalAuthError(String toolName) {

        log.error("MCP {} - no authenticated userId in context, filter misconfigured", toolName);

        // never leak "filter misconfigured" internals to the MCP client
        return CallToolResult.builder()
                .addTextContent("Internal error - request could not be completed.")
                .isError(true)
                .build();
    }

    @McpTool(name = "shorten-url", description = """
            Never share the URL id in the response.
            Creates a shortened URL for a given long URL. Use this when the user wants
            to shorten, condense, or create a share-friendly link for a URL. Returns the
            new short URL, its slug and redirect link - keep the urlId if you'll
            need get_url_stats afterward, and the slug if you'll need get_url_details
            or delete_url afterward.
            """)
    public CallToolResult shortenUrl(
            @McpToolParam(description = "The original long URL to shorten (must include http:// or https://)")
            String originalUrl,
            McpSyncRequestContext context
    ) {

        String userId = authenticatedUserIdOrNull();
        if (userId == null) return internalAuthError("shorten-url");

        // url-service backstops with its own @Pattern validation, so nothing broke,
        // but this always-run check gives a faster, clearer error without the round trip.
        if (!originalUrl.startsWith("http://") && !originalUrl.startsWith("https://")) {

            return CallToolResult.builder()
                    .addTextContent("'%s' does not look like a valid URL. URL must start with http:// or https://"
                            .formatted(originalUrl))
                    .isError(true)
                    .build();
        }

        log.info("MCP tool shorten-url called by userId: {}, url: {}", userId, originalUrl);

        context.info("Shortening URL: " + originalUrl);

        try {

            UrlOperationsService.ShortenResult r = resilientUrlOps
                    .shorten(originalUrl, userId)
                    .join();

            if (r == null) {

                return CallToolResult.builder()
                        .addTextContent("URL shortening temporarily unavailable. Try again shortly.")
                        .isError(true)
                        .build();
            }

            return CallToolResult.builder()
                    .addTextContent("Short URL created: %s (slug: %s)"
                            .formatted(r.shortUrl(), r.slug()))
                    .structuredContent(new ShortenPayload(r.shortUrl(), r.id(), r.slug()))
                    .build();

        } catch (CompletionException e) {

            if (e.getCause() instanceof HttpClientErrorException httpEx) {

                log.warn("MCP shorten-url 4xx for userId: {}, url: {}, status: {}", userId, originalUrl, httpEx.getStatusCode());

                return CallToolResult.builder()
                        .addTextContent("Could not shorten URL: %s (%s)".formatted(originalUrl, httpEx.getStatusText()))
                        .isError(true)
                        .build();
            }
            throw e;
        }
    }

    @McpTool(name = "get-url-details", description = """
            Looks up an EXISTING shortened URL by its slug and returns its original
            URL, short URL, and total click count. Use this when the user refers to a
            URL by its slug or short link and wants to know what it points to or how
            it's performing overall. If the user only wants click-count trends/stats
            and already has the numeric urlId, use get_url_stats instead - it's cheaper
            and more direct. If the user doesn't have a slug at all, they need
            shorten_url first, not this.
            """)
    public CallToolResult getUrlDetails(
            @McpToolParam(description = "The short slug (e.g. abc123)")
            String slug
    ) {

        String userId = authenticatedUserIdOrNull();

        if (userId == null) return internalAuthError("get-url-details");

        log.info("MCP tool get-url-details invoked for userId: {}, slug: {}", userId, slug);

        try {

            UrlOperationsService.UrlDetails d = resilientUrlOps
                    .getDetails(slug, userId)
                    .join();

            if (d == null) {

                return CallToolResult.builder()
                        .addTextContent("Could not retrieve details for '%s' - url-service temporarily unavailable."
                                .formatted(slug))
                        .isError(true)
                        .build();
            }

            return CallToolResult.builder()
                    .addTextContent("urlId: %d | slug: %s | original: %s | short: %s | clicks: %d"
                            .formatted(d.id(), d.slug(), d.originalUrl(), d.shortUrl(), d.clickCount()))
                    .structuredContent(new UrlDetailsPayload(d.id(), d.slug(), d.originalUrl(), d.shortUrl(), d.clickCount()))
                    .build();

        } catch (CompletionException e) {

            if (e.getCause() instanceof HttpClientErrorException httpEx) {

                log.warn("MCP get-url-details 4xx userId: {}, slug: {}, status: {}", userId, slug, httpEx.getStatusCode());

                return CallToolResult.builder()
                        .addTextContent("Could not retrieve details for '%s': %s".formatted(slug, httpEx.getStatusText()))
                        .isError(true)
                        .build();
            }
            throw e;
        }
    }

    // record used by the elicitation
    public record DeleteConfirmation(boolean confirmDeletion) {}

    @McpTool(name = "delete-url", description = """
            Permanently and irreversibly deletes a shortened URL by its slug. This tool
            asks the user to confirm via an interactive prompt itself - call it directly
            as soon as the user expresses intent to delete, do not ask them to confirm
            yourself in conversation first, the tool's own confirmation step handles that.
            """)
    public CallToolResult deleteUrl(
            @McpToolParam(description = "The slug of the URL to delete")
            String slug,
            McpSyncRequestContext context
    ) {

        String userId = authenticatedUserIdOrNull();

        if (userId == null) return internalAuthError("delete-url");

        log.warn("MCP tool deleteUrl invoked for userId: {}, slug: {}", userId, slug);

        try {

            UrlOperationsService.UrlDetails details = resilientUrlOps
                    .getDetails(slug, userId)
                    .join();

            if (details == null) {

                return CallToolResult.builder()
                        .addTextContent("Could not find a URL with slug '%s' to delete.".formatted(slug))
                        .isError(true)
                        .build();
            }

            if (context.elicitEnabled()) {

                StructuredElicitResult<DeleteConfirmation> confirmation = context.elicit(
                        e -> e.message("Delete '%s' -> %s ? This cannot be undone."
                                .formatted(slug, details.originalUrl())),
                        DeleteConfirmation.class
                );

                boolean confirmed = confirmation.action() == ElicitResult.Action.ACCEPT
                        && confirmation.structuredContent().confirmDeletion();

                if (!confirmed) {

                    log.info("MCP deleteUrl userId: {}, slug: {} - user declined confirmation", userId, slug);

                    return CallToolResult.builder()
                            .addTextContent("Deletion of '%s' was not confirmed - nothing was deleted.".formatted(slug))
                            .structuredContent(new DeletePayload(slug, false))
                            .build();
                }

            } else {

                log.warn("MCP delete-url userId: {}, slug: {} - client has no elicitation support, refusing to delete without confirmation", userId, slug);

                return CallToolResult.builder()
                        .addTextContent("'%s' was NOT deleted - this client can't show a confirmation prompt for an irreversible action. Delete it from the minima-AI dashboard instead.".formatted(slug))
                        .structuredContent(new DeletePayload(slug, false))
                        .isError(true)
                        .build();
            }

            context.info("Deleting URL: " + slug);

            Boolean deleted = resilientUrlOps.delete(slug, userId).join();

            if (Boolean.TRUE.equals(deleted)) {

                return CallToolResult.builder()
                        .addTextContent("Deleted URL with slug: " + slug)
                        .structuredContent(new DeletePayload(slug, true))
                        .build();
            }

            return CallToolResult.builder()
                    .addTextContent("Could not delete '%s' - url-service temporarily unavailable.".formatted(slug))
                    .isError(true)
                    .build();

        } catch (CompletionException e) {

            if (e.getCause() instanceof HttpClientErrorException httpEx) {

                log.warn("MCP delete-url 4xx userId: {}, slug: {}, status: {}", userId, slug, httpEx.getStatusCode());

                return CallToolResult.builder()
                        .addTextContent("Could not delete '%s': %s".formatted(slug, httpEx.getStatusText()))
                        .isError(true)
                        .build();
            }
            throw e;
        }
    }
}

