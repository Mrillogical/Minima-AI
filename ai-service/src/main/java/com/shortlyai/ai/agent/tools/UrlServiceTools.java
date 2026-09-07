package com.minima-AI.ai.agent.tools;

import com.minima-AI.ai.operations.ResilientUrlOps;
import com.minima-AI.ai.operations.UrlOperationsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UrlServiceTools {

    private final ResilientUrlOps resilientUrlOps;

    @Tool(description = "Shorten a long URL and return the generated short URL")
    public String shortenUrl(
            @ToolParam(description = "the original long URL") String originalUrl,
            ToolContext toolContext
    ) {

        String userId = (String) toolContext.getContext().get("userId");

        log.info("Tool shortenUrl userId: {} url: {}", userId, originalUrl);

        try {

            UrlOperationsService.ShortenResult result =
                    resilientUrlOps
                            .shorten(originalUrl, userId)
                            .join();

            if (result == null) {
                return "URL shortening temporarily unavailable. Please try again shortly.";
            }

            log.debug(
                    "shortenUrl slug: {} urlId: {}",
                    result.slug(),
                    result.id()
            );

            return "Short URL created: %s (urlId: %d)"
                    .formatted(result.shortUrl(), result.id());

        } catch (Exception ex) {

            log.error(
                    "Failed to shorten URL for userId: {} url: {}",
                    userId,
                    originalUrl,
                    ex
            );

            return "URL shortening temporarily unavailable. Please try again shortly.";
        }
    }

    @Tool(description = "Get details of a shortened URL by its slug")
    public String getUrlDetails(
            @ToolParam(description = "the short slug, e.g. abc123") String slug,
            ToolContext toolContext
    ) {

        String userId = (String) toolContext.getContext().get("userId");

        log.info("Tool getUrlDetails userId: {} slug: {}", userId, slug);

        try {

            UrlOperationsService.UrlDetails details =
                    resilientUrlOps
                            .getDetails(slug, userId)
                            .join();

            if (details == null) {
                return "Could not retrieve details for '%s' - url-service temporarily unavailable."
                        .formatted(slug);
            }

            log.debug(
                    "getUrlDetails slug: {} clicks: {}",
                    slug,
                    details.clickCount()
            );

            return "urlId: %d, slug: %s, original URL: %s, clicks: %d"
                    .formatted(
                            details.id(),
                            details.slug(),
                            details.originalUrl(),
                            details.clickCount()
                    );

        } catch (Exception ex) {

            log.error(
                    "Failed to fetch URL details for userId: {} slug: {}",
                    userId,
                    slug,
                    ex
            );

            return "Could not retrieve details for '%s' - url-service temporarily unavailable."
                    .formatted(slug);
        }
    }

    @Tool(description = """
            Delete a shortened URL by its slug. This is permanent and cannot be undone.
            You MUST ask the user to explicitly confirm before calling this with
            confirmDeletion=true. Never call this with confirmDeletion=true unless
            the user's most recent message explicitly confirms the deletion.
            """)
    public String deleteUrl(
            @ToolParam(description = "the short slug to delete") String slug,
            @ToolParam(description = "true only if the user has explicitly confirmed deletion in their last message") boolean confirmDeletion,
            ToolContext toolContext
    ) {
        String userId = (String) toolContext
                .getContext()
                .get("userId");

        if (!confirmDeletion) {

            UrlOperationsService.UrlDetails details = resilientUrlOps
                    .getDetails(slug, userId)
                    .join();

            String target = details != null ? details.originalUrl() : slug;

            log.info("Tool deleteUrl userId: {} slug: {} - awaiting explicit confirmation", userId, slug);

            return "This will permanently delete '%s' -> %s. Please confirm with the user before calling delete_url again with confirmDeletion=true."
                    .formatted(slug, target);
        }

        log.warn("Tool deleteUrl userId: {} slug: {} - confirmed, deleting", userId, slug);

        try {

            boolean deleted = resilientUrlOps.delete(slug, userId).join();

            return deleted
                    ? "Deleted URL with slug: " + slug
                    : "Could not delete '%s': url-service temporarily unavailable."
                    .formatted(slug);

        } catch (Exception ex) {

            log.error("Failed to delete URL for userId: {} slug: {}", userId, slug, ex);

            return "Could not delete '%s': url-service temporarily unavailable.".formatted(slug);
        }
    }
}

