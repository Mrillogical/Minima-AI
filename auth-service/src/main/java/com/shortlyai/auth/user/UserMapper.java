package com.minima-AI.auth.user;

import com.minima-AI.auth.dto.UserResponse;
import org.springframework.stereotype.Component;

// Mapper â€” converts internal User entity to outward-facing UserResponse
// @Component â€” Spring manages it, inject wherever needed
@Component
public class UserMapper {

    // Never return entities from controllers or services
    // This method is the single place that controls what fields get exposed
    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getProvider(),
                user.isVerified(),
                user.getCreatedAt()
        );
    }
}

