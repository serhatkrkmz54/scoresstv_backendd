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
import com.scorestv.user.dto.ResetPasswordCodeRequest;
import com.scorestv.user.dto.ResetPasswordRequest;
import com.scorestv.user.dto.TokenRequest;
import com.scorestv.user.dto.UpdateProfileRequest;
import com.scorestv.user.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final AvatarService avatarService;

    public AuthController(AuthService authService,
                          PasswordResetService passwordResetService,
                          AvatarService avatarService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.avatarService = avatarService;
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

    /**
     * Giris yapmis kullanicinin hesabini ve tum kisisel verilerini KALICI olarak
     * siler (App Store 5.1.1(v) / Google Play zorunlulugu). Geri donusu yoktur;
     * onay istemci tarafinda alinir. 204 No Content doner.
     */
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@AuthenticationPrincipal CurrentUser currentUser) {
        authService.deleteAccount(currentUser.id());
    }

    /**
     * Giris yapmis kullanicinin profil resmini (avatar) yukler/gunceller.
     * multipart/form-data — {@code file} alani. Sunucu gorseli merkezden kare
     * kirpip 256px JPEG'e cevirip depolar. Guncel kullanici (avatarUrl dolu) doner.
     */
    @PostMapping(value = "/me/avatar", consumes = "multipart/form-data")
    public UserResponse uploadAvatar(@AuthenticationPrincipal CurrentUser currentUser,
                                     @RequestParam("file") MultipartFile file) {
        return avatarService.upload(currentUser.id(), file);
    }

    /** Profil resmini kaldirir; avatarUrl null olan guncel kullanici doner. */
    @DeleteMapping("/me/avatar")
    public UserResponse removeAvatar(@AuthenticationPrincipal CurrentUser currentUser) {
        return avatarService.remove(currentUser.id());
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

    // ---- Kod tabanli (OTP) sifre sifirlama — mobil ----

    @PostMapping("/forgot-password-code")
    public MessageResponse forgotPasswordCode(@Valid @RequestBody ForgotPasswordRequest body,
                                              HttpServletRequest request) {
        passwordResetService.requestResetCode(body.email(), request.getRemoteAddr());
        return new MessageResponse(
                "Eğer bu e-posta kayıtlıysa, doğrulama kodu gönderildi.");
    }

    @PostMapping("/reset-password-code")
    public MessageResponse resetPasswordCode(@Valid @RequestBody ResetPasswordCodeRequest request) {
        passwordResetService.resetPasswordWithCode(
                request.email(), request.code(), request.newPassword());
        return new MessageResponse(
                "Şifreniz güncellendi. Yeni şifrenizle giriş yapabilirsiniz.");
    }
}
