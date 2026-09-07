package com.minima-AI.analytics.clicks;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("${api.prefix}/analytics")
@RequiredArgsConstructor
@Validated
@Tag(name = "Click Statistics Controller")
public class ClickStatsController {

    private final ClickService clickService;

    @Operation(
            summary = "Get an URL's total clicks",
            description = "Fetches the total clicks of an shortened URL"
    )
    // GET /api/v1/analytics/{urlId} -> returns total click count
    @GetMapping("/{urlId}")
    public ResponseEntity<ClickStatsResponse> getStats(
            @PathVariable Long urlId,
            @AuthenticationPrincipal UUID userId
    ) {

        long total = clickService.getTotalClicks(urlId, userId);

        return ResponseEntity.ok(new ClickStatsResponse(urlId, total, "OK"));
    }

    @Operation(
            summary = "Hourly breakdown",
            description = "Fetches the changes happened in an hour"
    )
    // hourly breakdown
    @GetMapping("/{urlId}/hourly")
    public ResponseEntity<List<HourlyBreakdownResponse>> getHourlyBreakdown(
            @PathVariable Long urlId,
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "24") int hours
    ) {

        return ResponseEntity.ok(
                clickService.getHourlyBreakdown(urlId, userId, hours)
        );
    }

    @Operation(
            summary = "Get top URLs",
            description = "Fetches the URLs which have the top most clicks"
    )
    // Top N URLs
    @GetMapping("/top")
    public ResponseEntity<List<TopUrlResponse>> topUrls(
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "Limit should be more than 1 and less than 50")
            @Max(value = 50, message = "Limit should be more than 1 and less than 50")
            int limit,
            @AuthenticationPrincipal
            UUID userId
    ) {

        return ResponseEntity.ok(
                clickService.getTopUrls(userId, limit)
        );
    }
}

