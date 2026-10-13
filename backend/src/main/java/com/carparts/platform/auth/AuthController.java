package com.carparts.platform.auth;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final boolean secure;
    private final long hours;

    public AuthController(AuthService auth,
                          @Value("${app.auth.secure-cookie:false}") boolean secure,
                          @Value("${app.auth.session-hours:12}") long hours) {
        this.auth=auth; this.secure=secure; this.hours=hours;
    }

    public record LoginRequest(@NotBlank String identifier,@NotBlank String password){}
    public record RegisterRequest(
            @NotBlank String username,
            @Email String email,
            @NotBlank String displayName,
            @NotBlank String phone,
            @Size(min=12) String password,
            @Size(min=12) String confirmPassword){}

    @GetMapping("/csrf")
    public Map<String,String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/login")
    public AuthService.Me login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        var result = auth.login(request.identifier(), request.password());
        setCookie(response, result.token(), Duration.ofHours(hours));
        return result.me();
    }

    @PostMapping("/register")
    public AuthService.Me register(@Valid @RequestBody RegisterRequest request) {
        return auth.registerCustomer(request.username(), request.email(), request.displayName(), request.phone(),
                request.password(), request.confirmPassword());
    }

    @GetMapping("/me")
    public AuthService.Me me(Authentication authentication) {
        AuthPrincipal p = principal(authentication);
        return auth.me(p.id());
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        auth.logout(cookie(request, AuthService.COOKIE_NAME));
        setCookie(response, "", Duration.ZERO);
    }

    private AuthPrincipal principal(Authentication a) {
        if (a == null || !(a.getPrincipal() instanceof AuthPrincipal p))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return p;
    }

    private void setCookie(HttpServletResponse response,String value,Duration age) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                ResponseCookie.from(AuthService.COOKIE_NAME,value)
                        .httpOnly(true).secure(secure).sameSite("Lax").path("/").maxAge(age).build().toString());
    }

    private String cookie(HttpServletRequest request,String name) {
        if (request.getCookies()==null) return null;
        for (Cookie c: request.getCookies()) if (name.equals(c.getName())) return c.getValue();
        return null;
    }
}
