package com.scorestv.user;

import com.scorestv.common.PageResponse;
import com.scorestv.user.dto.CreateUserRequest;
import com.scorestv.user.dto.LogoutAllResponse;
import com.scorestv.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN'in kullanici hesaplari uzerindeki yonetim islemleri.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AuthService authService;
    private final UserService userService;

    public AdminUserController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    /** Kullanicilari sayfali listeler. Ornek: ?page=0&size=20&sort=email,asc */
    @GetMapping
    public PageResponse<UserResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return userService.listUsers(pageable);
    }

    /** Yeni kullanici olusturur (ADMIN / EDITOR / USER). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.createUser(request);
    }

    /**
     * Bir kullanicinin tum oturumlarini sonlandirir: tum refresh token'lar
     * iptal edilir. Kullanici, elindeki access token suresi dolunca (en fazla
     * 15 dk) tamamen cikis yapmis olur.
     */
    @PostMapping("/{userId}/logout-all")
    public LogoutAllResponse logoutAll(@PathVariable Long userId) {
        int revoked = authService.revokeAllSessions(userId);
        return new LogoutAllResponse(userId, revoked);
    }
}
