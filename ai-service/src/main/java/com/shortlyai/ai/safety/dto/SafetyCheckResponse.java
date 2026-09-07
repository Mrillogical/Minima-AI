package com.minima-AI.ai.safety.dto;

public record SafetyCheckResponse(
        boolean safe,
        String riskLevel,   // LOW, MEDIUM, HIGH
        String reasoning
) {}

