package com.minima-AI.ai.agent;

import com.minima-AI.ai.agent.dto.AgentRequest;
import com.minima-AI.ai.agent.dto.AgentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}/ai/agent")
@RequiredArgsConstructor
@Tag(name = "AI Agent Controller")
public class AgentController {

    private final AgentService agentService;

    @Operation(
            summary = "Chat with AI agent",
            description = "Users talk to it in plain English about their URLs and get their answers"
    )
    @PostMapping
    public ResponseEntity<AgentResponse> chat(
            @RequestHeader("X-User-Id")
            String userId,
            @Valid @RequestBody
            AgentRequest request
    ) {

        return ResponseEntity.status(HttpStatus.OK)
                .body(
                        agentService.chat(userId, request.message())
                                .join()
                );
    }
}

