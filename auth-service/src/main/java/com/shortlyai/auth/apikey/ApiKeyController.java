package com.minima-AI.auth.apikey;

import com.minima-AI.auth.apikey.dto.ApiKeyGenerateRequest;
import com.minima-AI.auth.apikey.dto.ApiKeyMetadataResponse;
import com.minima-AI.auth.apikey.dto.ApiKeyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("${api.prefix}/auth/apikeys")
@RequiredArgsConstructor
@Tag(name = "Api Keys Controller")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @Operation(
            summary = "API key",
            description = "Allows users to generate an API key for MCP connectors"
    )
    @PostMapping
    public ResponseEntity<ApiKeyResponse> generate(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody ApiKeyGenerateRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        apiKeyService.generate(
                                UUID.fromString(userId),
                                request)
                );
    }

    @Operation(
            summary = "Get API keys",
            description = "Retrieves all the API keys of the user"
    )
    @GetMapping
    public ResponseEntity<List<ApiKeyMetadataResponse>> list(
            @AuthenticationPrincipal String userId
    ) {

        return ResponseEntity.ok(
                apiKeyService.list(
                        UUID.fromString(userId)
                )
        );
    }

    @Operation(
            summary = "Delete API key",
            description = "Allows users to revoke their API key"
    )
    // {id} = key UUID from the list response
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID id
    ) {

        apiKeyService.revoke(UUID.fromString(userId), id);

        return ResponseEntity.noContent().build();
    }
}

