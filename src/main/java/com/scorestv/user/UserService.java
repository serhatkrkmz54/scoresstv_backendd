package com.scorestv.user;

import com.scorestv.common.ApiException;
import com.scorestv.common.PageResponse;
import com.scorestv.user.dto.CreateUserRequest;
import com.scorestv.user.dto.UserResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADMIN'in kullanici hesaplari uzerindeki yonetim islemleri
 * (olusturma, sayfali listeleme).
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Yeni kullanici olusturur. Rol (ADMIN/EDITOR/USER) cagiran ADMIN tarafindan secilir. */
    @Transactional
    public UserResponse createUser(CreateUserRequest req) {
        String email = req.email().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("Bu e-posta zaten kayıtlı");
        }
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(req.password()))
                .displayName(req.displayName().trim())
                .role(req.role())
                .birthDate(req.birthDate())
                .country(req.country() != null ? req.country().trim() : null)
                .enabled(true)
                .build();
        return UserResponse.from(userRepository.save(user));
    }

    /** Kullanicilari sayfali doner. */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(Pageable pageable) {
        return PageResponse.from(
                userRepository.findAll(pageable).map(UserResponse::from));
    }
}
