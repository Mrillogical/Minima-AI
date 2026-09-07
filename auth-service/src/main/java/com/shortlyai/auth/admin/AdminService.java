package com.minima-AI.auth.admin;

import com.minima-AI.auth.dto.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminService {

    Page<UserResponse> getAllUsers(Pageable pageable);
}


