package com.minima-AI.ai.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentRequest(
        @NotBlank(message = "message must not be blank")
        @Size(max=2000, message = "Message must not exceed 2000 characters")
        String message
) {}

