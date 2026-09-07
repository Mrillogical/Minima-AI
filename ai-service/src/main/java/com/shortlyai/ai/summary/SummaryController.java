package com.minima-AI.ai.summary;

import com.minima-AI.ai.summary.dto.SummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}/ai/summary")
@RequiredArgsConstructor
@Tag(name = "Summary Controller")
public class SummaryController {

    private final SummaryService summaryService;

    @Operation(
            summary = "Summarize URLs",
            description = "The AI agent gets the URL's stats and generates a summary of number of clicks, its tag etc"
    )
    @GetMapping("/{urlId}")
    public ResponseEntity<SummaryResponse> summarize(
            @PathVariable
            Long urlId,
            @RequestHeader("X-User-Id")
            String userId
    ) {

        return ResponseEntity.status(HttpStatus.OK)
                .body(
                        summaryService.summarize(urlId, userId)
                                .join()
                );
    }
}

