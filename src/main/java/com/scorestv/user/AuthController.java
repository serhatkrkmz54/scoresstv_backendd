package com.scorestv.user;

import com.scorestv.common.MessageResponse;
import com.scorestv.security.CurrentUser;
import com.scorestv.user.dto.AppleLoginRequest;
import com.scorestv.user.dto.AuthResponse;
import com.scorestv.user.dto.ChangePasswordRequest;
import com.scorestv.user.dto.ForgotPasswordRequest;
import com.scorestv.user.dto.GoogleLoginRequest;
import com.scorestv.user.dto.LoginRequest;
import com.scorestv.user.dto.RegisterRequest;
import com.scorestv.user.dto.ResetPasswordRequest;
import com.scorestv.user.dto.TokenRequest;
import com.scorestv.user.dto.UpdateProfileRequest;
import com.scorestv.user.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService,
                          PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleLoginRequest request) {
        return authService.loginWithGoogle(request);
    }

    @PostMapping("/apple")
    public AuthResponse apple(@Valid @RequestBody AppleLoginRequest request) {
        return authService.loginWithApple(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody TokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody TokenRequest request) {
        authService.logout(request);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal CurrentUser currentUser) {
        return authService.currentUser(currentUser.id());
    }

    @PutMapping("/me")
    public UserResponse updateMe(@AuthenticationPrincipal CurrentUser currentUser,
                                 @Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(currentUser.id(), request);
    }

    @PostMapping("/change-password")
    public AuthResponse changePassword(@AuthenticationPrincipal CurrentUser currentUser,
                                       @Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(currentUser.id(), request);
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest body,
                                          HttpServletRequest request) {
        passwordResetService.forgotPassword(body.email(), request.getRemoteAddr());
        return new MessageResponse(
                "Eğer bu e-posta kayıtlıysa, şifre sıfırlama bağlantısı gönderildi.");
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return new MessageResponse(
                "Şifreniz güncellendi. Yeni şifrenizle giriş yapabilirsiniz.");
    }
}
