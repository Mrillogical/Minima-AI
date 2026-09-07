package com.minima-AI.ai.operations;

import com.minima-AI.ai.mcp.resources.UrlResources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResilientUrlOpsTests {

    @Mock
    UrlOperationsService urlOperationsService;

    // Runs supplyAsync synchronously on the calling thread so tests don't need
    // to block on real thread-pool scheduling
    private final Executor directExecutor = Runnable::run;

    ResilientUrlOps resilientUrlOps;

    @BeforeEach
    void setUp() {
        resilientUrlOps = new ResilientUrlOps(urlOperationsService, directExecutor);
    }

    @Test
    void shorten_delegatesToUrlOpsAndReturnsResult() throws Exception {

        UrlOperationsService.ShortenResult expected =
                new UrlOperationsService.ShortenResult(1L, "abc123", "http://short.ly/abc123");

        when(urlOperationsService.shorten("https://example.com", "user-1")).thenReturn(expected);

        CompletableFuture<UrlOperationsService.ShortenResult> result =
                resilientUrlOps.shorten("https://example.com", "user-1");

        assertThat(result.get()).isEqualTo(expected);
    }

    @Test
    void shortenFallback_returnsCompletedFutureWithNull_neverThrows() throws Exception {

        CompletableFuture<UrlOperationsService.ShortenResult> fallback =
                resilientUrlOps.shortenFallback("https://example.com", "user-1", new RuntimeException("down"));

        assertThat(fallback.get()).isNull();
        assertThat(fallback.isCompletedExceptionally()).isFalse();
    }

    @Test
    void getDetails_delegatesToUrlOps() throws Exception {

        UrlOperationsService.UrlDetails details =
                new UrlOperationsService.UrlDetails(1L, "slug", "https://x.com", "http://short.ly/slug", 5L);

        when(urlOperationsService.getDetails("slug", "user-1")).thenReturn(details);

        CompletableFuture<UrlOperationsService.UrlDetails> result =
                resilientUrlOps.getDetails("slug", "user-1");

        assertThat(result.get()).isEqualTo(details);
    }

    @Test
    void getDetailsFallback_returnsNullWithoutThrowing() throws Exception {

        CompletableFuture<UrlOperationsService.UrlDetails> fallback =
                resilientUrlOps.getDetailsFallback("slug", "user-1", new RuntimeException("timeout"));

        assertThat(fallback.get()).isNull();
    }

    @Test
    void delete_success_returnsTrue() throws Exception {

        CompletableFuture<Boolean> result = resilientUrlOps.delete("slug", "user-1");

        assertThat(result.get()).isTrue();
    }

    @Test
    void deleteFallback_returnsFalseWithoutThrowing() throws Exception {

        CompletableFuture<Boolean> fallback =
                resilientUrlOps.deleteFallback("slug", "user-1", new RuntimeException("down"));

        assertThat(fallback.get()).isFalse();
    }

    @Test
    void getDetailsById_delegatesToUrlOps() throws Exception {

        UrlOperationsService.UrlDetails details =
                new UrlOperationsService.UrlDetails(2L, "slug2", "https://y.com", "http://short.ly/slug2", 0L);

        when(urlOperationsService.getDetailsById(2L, "user-1")).thenReturn(details);

        CompletableFuture<UrlOperationsService.UrlDetails> result =
                resilientUrlOps.getDetailsById(2L, "user-1");

        assertThat(result.get()).isEqualTo(details);
    }

    @Test
    void getAllForUser_delegatesToUrlOps() throws Exception {

        List<UrlResources.UrlResource> urls = List.of();
        when(urlOperationsService.getAllUrlsForUser("user-1")).thenReturn(urls);

        CompletableFuture<List<UrlResources.UrlResource>> result =
                resilientUrlOps.getAllForUser("user-1");

        assertThat(result.get()).isEqualTo(urls);
    }

    @Test
    void getAllForUserFallback_returnsNullWithoutThrowing() throws Exception {

        CompletableFuture<List<UrlResources.UrlResource>> fallback =
                resilientUrlOps.getAllForUserFallback("user-1", new RuntimeException("down"));

        assertThat(fallback.get()).isNull();
    }

    @Test
    void getDetails_delegatesToUrlOpsAndReturnsResult() throws Exception {

        UrlOperationsService.UrlDetails expected =
                new UrlOperationsService.UrlDetails(1L, "abc123", "https://example.com", "http://short.ly/abc123", 5L);

        when(urlOperationsService.getDetails("abc123", "user-1")).thenReturn(expected);

        CompletableFuture<UrlOperationsService.UrlDetails> result =
                resilientUrlOps.getDetails("abc123", "user-1");

        assertThat(result.get()).isEqualTo(expected);
    }

    @Test
    void getDetailsFallback_returnsNull_neverThrows() throws Exception {

        CompletableFuture<UrlOperationsService.UrlDetails> fallback =
                resilientUrlOps.getDetailsFallback("abc123", "user-1", new RuntimeException("down"));

        assertThat(fallback.get()).isNull();
        assertThat(fallback.isCompletedExceptionally()).isFalse();
    }

    @Test
    void delete_delegatesToUrlOps_returnsTrue() throws Exception {

        CompletableFuture<Boolean> result = resilientUrlOps.delete("abc123", "user-1");

        assertThat(result.get()).isTrue();
        verify(urlOperationsService).delete("abc123", "user-1");
    }

    @Test
    void deleteFallback_returnsFalse_neverThrows() throws Exception {

        CompletableFuture<Boolean> fallback =
                resilientUrlOps.deleteFallback("abc123", "user-1", new RuntimeException("down"));

        assertThat(fallback.get()).isFalse();
        assertThat(fallback.isCompletedExceptionally()).isFalse();
    }

    @Test
    void getDetailsById_delegatesAndReturnsResult() throws Exception {

        UrlOperationsService.UrlDetails expected =
                new UrlOperationsService.UrlDetails(7L, "xyz", "https://x.com", "http://short.ly/xyz", 3L);

        when(urlOperationsService.getDetailsById(7L, "user-1")).thenReturn(expected);

        CompletableFuture<UrlOperationsService.UrlDetails> result =
                resilientUrlOps.getDetailsById(7L, "user-1");

        assertThat(result.get()).isEqualTo(expected);
    }

    @Test
    void getDetailsByIdFallback_returnsNull_neverThrows() throws Exception {

        CompletableFuture<UrlOperationsService.UrlDetails> fallback =
                resilientUrlOps.getDetailsByIdFallback(7L, "user-1", new RuntimeException("down"));

        assertThat(fallback.get()).isNull();
        assertThat(fallback.isCompletedExceptionally()).isFalse();
    }

    @Test
    void getAllForUser_delegatesAndReturnsResult() throws Exception {

        List<UrlResources.UrlResource> expected =
                List.of(new UrlResources.UrlResource("abc", "https://a.com", "http://s.ly/abc", 1L));

        when(urlOperationsService.getAllUrlsForUser("user-1")).thenReturn(expected);

        CompletableFuture<List<UrlResources.UrlResource>> result =
                resilientUrlOps.getAllForUser("user-1");

        assertThat(result.get()).isEqualTo(expected);
    }

    @Test
    void getAllForUserFallback_returnsNull_neverThrows() throws Exception {

        CompletableFuture<List<UrlResources.UrlResource>> fallback =
                resilientUrlOps.getAllForUserFallback("user-1", new RuntimeException("down"));

        assertThat(fallback.get()).isNull();
        assertThat(fallback.isCompletedExceptionally()).isFalse();
    }
}

