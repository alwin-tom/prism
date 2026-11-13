package com.prism.auth;

import com.prism.shared.api.ApiResponse;
import com.prism.shared.security.AuthContext;
import com.prism.shared.util.Requests;
import com.prism.user.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/otp/request")
    public ApiResponse<AuthService.OtpRequestView> request(@Valid @RequestBody OtpRequest request, HttpServletRequest http) {
        return ApiResponse.ok(auth.requestOtp(request.phoneNumber(), Requests.clientIp(http)));
    }

    @PostMapping("/otp/verify")
    public ApiResponse<AccountService.AccountView> verify(@Valid @RequestBody OtpVerify request, HttpServletRequest http, HttpServletResponse response) {
        return ApiResponse.ok(auth.verifyOtp(request.phoneNumber(), request.otp(), request.fullName(), Requests.clientIp(http), response));
    }

    @PostMapping("/login")
    public ApiResponse<AccountService.AccountView> login(@Valid @RequestBody PasswordLogin request, HttpServletRequest http, HttpServletResponse response) {
        return ApiResponse.ok(auth.login(request.phoneNumber(), request.password(), Requests.clientIp(http), response));
    }

    @PostMapping("/refresh")
    public ApiResponse<AccountService.AccountView> refresh(HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.ok(auth.refresh(request, response));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            auth.logout(request, response, AuthContext.require().id(), Requests.clientIp(request));
        } catch (RuntimeException exception) {
            auth.logout(request, response, null, Requests.clientIp(request));
        }
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<AccountService.AccountView> me() {
        return ApiResponse.ok(auth.me(AuthContext.require()));
    }

    @PutMapping("/me")
    public ApiResponse<AccountService.AccountView> update(@RequestBody ProfileUpdate request) {
        return ApiResponse.ok(auth.updateMe(AuthContext.require(), request.fullName(), request.email()));
    }

    public record OtpRequest(@NotBlank String phoneNumber) {
    }

    public record OtpVerify(@NotBlank String phoneNumber, @NotBlank String otp, String fullName) {
    }

    public record PasswordLogin(@NotBlank String phoneNumber, @NotBlank String password) {
    }

    public record ProfileUpdate(String fullName, String email) {
    }
}
