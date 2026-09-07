package com.minima-AI.ai.safety;

import com.minima-AI.ai.safety.dto.SafetyCheckRequest;
import com.minima-AI.ai.safety.dto.SafetyCheckResponse;
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
@RequestMapping("${api.prefix}/ai/safety")
@RequiredArgsConstructor
@Tag(name = "Safety Controller")
public class SafetyController {

    private final SafetyService safetyService;

    @Operation(
            summary = "Check URL safety",
            description = "The AI agent checks whether the URL is safe or malicious"
    )
    @PostMapping("/check")
    public ResponseEntity<SafetyCheckResponse> check(
            @Valid @RequestBody
            SafetyCheckRequest request
    ) {

        return ResponseEntity.status(HttpStatus.OK)
                .body(
                        safetyService.checkSafety(request)
                                .join()
                );
    }
}

