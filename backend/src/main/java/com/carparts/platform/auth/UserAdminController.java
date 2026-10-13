package com.carparts.platform.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {
    private final AuthService auth;
    public UserAdminController(AuthService auth){this.auth=auth;}

    public record CreateUser(
            @NotBlank String username,
            @Email String email,
            @NotBlank String displayName,
            @NotBlank String phone,
            @Size(min=12) String password,
            @Size(min=12) String confirmPassword,
            Set<String> roles,
            Integer managerLevel){}

    public record ProfileUpdate(
            @NotBlank String username,
            @Email String email,
            @NotBlank String displayName,
            @NotBlank String phone,
            Integer managerLevel){}

    public record RoleUpdate(Set<String> roles,Integer managerLevel){}
    public record StatusUpdate(String status){}
    public record PasswordUpdate(@Size(min=12) String password,@Size(min=12) String confirmPassword){}

    @GetMapping
    public List<AuthService.StaffUser> list(){return auth.users();}

    @PostMapping
    public AuthService.StaffUser create(@Valid @RequestBody CreateUser r, Authentication a){
        return auth.createManagedUser(p(a),r.username(),r.email(),r.displayName(),r.phone(),r.password(),r.confirmPassword(),r.roles(),r.managerLevel());
    }

    @PutMapping("/{id}/profile")
    public AuthService.StaffUser profile(@PathVariable UUID id,@Valid @RequestBody ProfileUpdate r,Authentication a){
        return auth.updateProfile(p(a),id,r.username(),r.email(),r.displayName(),r.phone(),r.managerLevel());
    }

    @PutMapping("/{id}/roles")
    public AuthService.StaffUser roles(@PathVariable UUID id,@RequestBody RoleUpdate r,Authentication a){
        return auth.updateRoles(p(a),id,r.roles(),r.managerLevel());
    }

    @PutMapping("/{id}/status")
    public AuthService.StaffUser status(@PathVariable UUID id,@RequestBody StatusUpdate r,Authentication a){
        return auth.setStatus(p(a),id,r.status());
    }

    @PutMapping("/{id}/password")
    public void password(@PathVariable UUID id,@Valid @RequestBody PasswordUpdate r,Authentication a){
        auth.resetPassword(p(a),id,r.password(),r.confirmPassword());
    }

    @GetMapping("/{id}/history")
    public List<Map<String,Object>> history(@PathVariable UUID id,Authentication a){
        return auth.history(p(a),id);
    }

    private AuthPrincipal p(Authentication a){return (AuthPrincipal)a.getPrincipal();}
}
