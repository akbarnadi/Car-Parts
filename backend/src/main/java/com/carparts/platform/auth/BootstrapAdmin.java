package com.carparts.platform.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdmin implements CommandLineRunner {
    private final AuthService auth;
    private final String email;
    private final String username;
    private final String phone;
    private final String password;
    private final String name;

    public BootstrapAdmin(
            AuthService auth,
            @Value("${app.bootstrap.admin-email:}") String email,
            @Value("${app.bootstrap.admin-username:manager0}") String username,
            @Value("${app.bootstrap.admin-phone:+10000000000}") String phone,
            @Value("${app.bootstrap.admin-password:}") String password,
            @Value("${app.bootstrap.admin-name:مدیر صفر}") String name) {
        this.auth=auth; this.email=email; this.username=username; this.phone=phone; this.password=password; this.name=name;
    }

    @Override
    public void run(String... args) {
        auth.bootstrapAdmin(email,username,phone,password,name);
    }
}
