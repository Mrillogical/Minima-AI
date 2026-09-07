package com.minima-AI.ai.classification;

import com.minima-AI.ai.classification.dto.ClassificationRequest;
import com.minima-AI.ai.classification.dto.ClassificationResponse;
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
@RequestMapping("${api.prefix}/ai/classify")
@RequiredArgsConstructor
@Tag(name = "Classification Controller")
public class ClassificationController {

    private final ClassificationService classificationService;

    @Operation(
            summary = "Classify URLs",
            description = "The AI agent classifies the URL into a category such as tech, finance etc."
    )
    @PostMapping
    public ResponseEntity<ClassificationResponse> classify(
            @Valid @RequestBody
            ClassificationRequest request
    ) {

        return ResponseEntity.status(HttpStatus.OK)
                .body(classificationService.classify(request)
                        .join()
                );

    }
}

