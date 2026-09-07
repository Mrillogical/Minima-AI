package com.minima-AI.ai.slug;

import com.minima-AI.ai.slug.dto.SlugRequest;
import com.minima-AI.ai.slug.dto.SlugResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${api.prefix}/ai/slug")
@RequiredArgsConstructor
@Tag(name = "Slug Controller", description = "Endpoints for slug suggestions")
public class SlugController {

    private final SlugService slugService;

    @Operation(
            summary = "Slug suggestions",
            description = "The AI agent suggests several slugs to an URL depending on its context"
    )
    @PostMapping("/suggest")
    public ResponseEntity<SlugResponse> suggest(
            @Valid @RequestBody
            SlugRequest request
    ) {

        return ResponseEntity.status(HttpStatus.OK)
                .body(
                        slugService.suggest(request)
                                .join()
                );
    }
}

