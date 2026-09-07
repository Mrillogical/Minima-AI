package com.minima-AI.auth.admin;

import com.minima-AI.auth.dto.UserResponse;
import com.minima-AI.auth.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminServiceImplTests {

    @Mock
    UserRepository userRepository;

    @Mock
    UserMapper userMapper;

    AdminServiceImpl adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminServiceImpl(userRepository, userMapper);
    }

    @Test
    void getAllUsers_mapsEachUserThroughMapper() {

        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        User user1 = User.builder().id(id1).email("a@example.com").role(Role.ROLE_FREE)
                .provider(Provider.LOCAL).verified(true).createdAt(Instant.now()).build();
        User user2 = User.builder().id(id2).email("b@example.com").role(Role.ROLE_PRO)
                .provider(Provider.GOOGLE).verified(true).createdAt(Instant.now()).build();

        Pageable pageable = PageRequest.of(0, 20);
        Page<User> userPage = new PageImpl<>(List.of(user1, user2), pageable, 2);

        UserResponse resp1 = new UserResponse(id1, "A", "a@example.com", Role.ROLE_FREE, Provider.LOCAL, true, Instant.now());
        UserResponse resp2 = new UserResponse(id2, "B", "b@example.com", Role.ROLE_PRO, Provider.GOOGLE, true, Instant.now());

        when(userRepository.findAll(pageable)).thenReturn(userPage);
        when(userMapper.toResponse(user1)).thenReturn(resp1);
        when(userMapper.toResponse(user2)).thenReturn(resp2);

        Page<UserResponse> result = adminService.getAllUsers(pageable);

        assertThat(result.getContent()).containsExactly(resp1, resp2);
        assertThat(result.getTotalElements()).isEqualTo(2);
        verify(userMapper).toResponse(user1);
        verify(userMapper).toResponse(user2);
    }

    @Test
    void getAllUsers_emptyRepository_returnsEmptyPage() {

        Pageable pageable = PageRequest.of(0, 20);
        when(userRepository.findAll(pageable)).thenReturn(Page.empty(pageable));

        Page<UserResponse> result = adminService.getAllUsers(pageable);

        assertThat(result.getContent()).isEmpty();
    }
}

