package com.carparts.platform.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class AuthService {
    public static final String COOKIE_NAME = "CP_SESSION";
    private static final Set<String> NON_ROOT_ROLES = Set.of(
            "MANAGER", "ADMIN", "PRODUCT_ADMIN", "PROCUREMENT", "FINANCE", "SHIPPING", "SUPPORT", "CUSTOMER");
    private static final Pattern USERNAME = Pattern.compile("^[\\p{L}\\p{N}._-]{3,80}$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{7,20}$");

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();
    private final Duration ttl;

    public AuthService(JdbcTemplate jdbc, PasswordEncoder encoder,
                       @Value("${app.auth.session-hours:12}") long hours) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.ttl = Duration.ofHours(hours);
    }

    public record Me(UUID id, String username, String email, String phone, String displayName,
                     Set<String> roles, Integer managerLevel, boolean root) {}
    public record LoginResult(String token, Instant expiresAt, Me me) {}
    public record StaffUser(UUID id, String username, String email, String phone, String displayName,
                            String status, Set<String> roles, Integer managerLevel, boolean root, Instant createdAt) {}
    private record UserRow(UUID id, String username, String email, String phone, String displayName,
                           String passwordHash, String status, Integer managerLevel, boolean root) {}

    @Transactional
    public LoginResult login(String rawIdentifier, String password) {
        String identifier = rawIdentifier == null ? "" : rawIdentifier.trim();
        if (identifier.isBlank()) throw bad("نام کاربری، تلفن یا ایمیل را وارد کنید.");
        // Never bind an untyped SQL NULL to the login predicate. PostgreSQL cannot infer
        // the type of a bare `? IS NOT NULL` parameter and older code caused login/session failures.
        String phone = Optional.ofNullable(phoneCandidate(identifier)).orElse("__NOT_A_PHONE__");
        List<UserRow> rows = jdbc.query("""
                SELECT id,username,email,phone,display_name,password_hash,status,manager_level,is_root
                FROM app_users
                WHERE LOWER(username)=LOWER(?)
                   OR (? IS NOT NULL AND phone=?)
                   OR (email IS NOT NULL AND LOWER(email)=LOWER(?))
                LIMIT 2
                """, (rs,n) -> mapUserRow(rs), identifier, phone, phone, identifier);
        if (rows.size() != 1 || !"ACTIVE".equals(rows.getFirst().status())
                || !encoder.matches(password == null ? "" : password, rows.getFirst().passwordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "نام کاربری/تلفن/ایمیل یا رمز عبور صحیح نیست.");
        }
        UserRow u = rows.getFirst();
        String token = newToken();
        Instant expires = Instant.now().plus(ttl);
        jdbc.update("DELETE FROM auth_sessions WHERE expires_at<NOW()");
        jdbc.update("INSERT INTO auth_sessions(token_hash,user_id,expires_at) VALUES (?,?,?)",
                hashToken(token), u.id(), Timestamp.from(expires));
        return new LoginResult(token, expires, me(u.id()));
    }

    @Transactional
    public Me registerCustomer(String username, String rawEmail, String displayName,
                               String phone, String password, String confirmPassword) {
        String user = normalizeUsername(username);
        String email = normalizeEmail(rawEmail);
        String normalizedPhone = normalizePhone(phone);
        requireName(displayName);
        validatePasswordPair(password, confirmPassword);
        ensureIdentityAvailable(user, email, normalizedPhone, null);

        UUID id = jdbc.queryForObject("""
                INSERT INTO app_users(username,email,phone,display_name,password_hash)
                VALUES (?,?,?,?,?) RETURNING id
                """, UUID.class, user, email, normalizedPhone, displayName.trim(), encoder.encode(password));
        replaceRoles(id, Set.of("CUSTOMER"));
        ensureCustomerProfile(id, displayName.trim(), normalizedPhone, email);
        return me(id);
    }

    public Me me(UUID id) {
        UserRow u = requiredUser(id);
        if (!"ACTIVE".equals(u.status())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return new Me(u.id(), u.username(), u.email(), u.phone(), u.displayName(), roles(u.id()),
                u.managerLevel(), u.root());
    }

    public AuthPrincipal findByToken(String token) {
        if (token == null || token.isBlank()) return null;
        List<AuthPrincipal> rows = jdbc.query("""
                SELECT u.id,u.username,u.email,u.phone,u.display_name,u.manager_level,u.is_root
                FROM auth_sessions s
                JOIN app_users u ON u.id=s.user_id
                WHERE s.token_hash=? AND s.expires_at>NOW() AND u.status='ACTIVE'
                """, (rs,n) -> {
            UUID id = rs.getObject("id", UUID.class);
            return new AuthPrincipal(id, rs.getString("username"), rs.getString("email"), rs.getString("phone"),
                    rs.getString("display_name"), roles(id), (Integer)rs.getObject("manager_level"), rs.getBoolean("is_root"));
        }, hashToken(token));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) jdbc.update("DELETE FROM auth_sessions WHERE token_hash=?", hashToken(token));
    }

    public List<StaffUser> users() {
        return jdbc.query("""
                SELECT id,username,email,phone,display_name,status,manager_level,is_root,created_at
                FROM app_users ORDER BY is_root DESC,COALESCE(manager_level,999),created_at
                """, (rs,n) -> {
            UUID id = rs.getObject("id", UUID.class);
            return new StaffUser(id, rs.getString("username"), rs.getString("email"), rs.getString("phone"),
                    rs.getString("display_name"), rs.getString("status"), roles(id),
                    (Integer)rs.getObject("manager_level"), rs.getBoolean("is_root"), rs.getTimestamp("created_at").toInstant());
        });
    }

    @Transactional
    public StaffUser createManagedUser(AuthPrincipal actor, String username, String rawEmail, String name,
                                       String phone, String password, String confirmPassword,
                                       Set<String> requestedRoles, Integer managerLevel) {
        assertAccountManager(actor);
        String user = normalizeUsername(username);
        String email = normalizeEmail(rawEmail);
        String normalizedPhone = normalizePhone(phone);
        requireName(name);
        validatePasswordPair(password, confirmPassword);
        Set<String> roles = sanitizeNonRootRoles(requestedRoles);
        Integer level = normalizedManagerLevel(roles, managerLevel);
        ensureIdentityAvailable(user, email, normalizedPhone, null);

        UUID id = jdbc.queryForObject("""
                INSERT INTO app_users(username,email,phone,display_name,password_hash,manager_level)
                VALUES (?,?,?,?,?,?) RETURNING id
                """, UUID.class, user, email, normalizedPhone, name.trim(), encoder.encode(password), level);
        replaceRoles(id, roles);
        if (roles.contains("CUSTOMER")) ensureCustomerProfile(id, name.trim(), normalizedPhone, email);
        audit(actor, id, "ACCOUNT_CREATED", "ایجاد حساب توسط مدیر");
        return staffUser(id);
    }

    @Transactional
    public StaffUser updateProfile(AuthPrincipal actor, UUID id, String username, String rawEmail,
                                   String name, String phone, Integer managerLevel) {
        UserRow target = requiredUser(id);
        assertCanManage(actor, target);
        String user = normalizeUsername(username);
        String email = normalizeEmail(rawEmail);
        String normalizedPhone = normalizePhone(phone);
        requireName(name);
        ensureIdentityAvailable(user, email, normalizedPhone, id);

        Integer level = target.root() ? 0 : normalizedManagerLevel(roles(id), managerLevel);
        jdbc.update("""
                UPDATE app_users SET username=?,email=?,phone=?,display_name=?,manager_level=?,updated_at=NOW()
                WHERE id=?
                """, user, email, normalizedPhone, name.trim(), level, id);
        if (roles(id).contains("CUSTOMER")) ensureCustomerProfile(id, name.trim(), normalizedPhone, email);
        audit(actor, id, "ACCOUNT_PROFILE_UPDATED", "ویرایش مشخصات حساب");
        return staffUser(id);
    }

    @Transactional
    public StaffUser updateRoles(AuthPrincipal actor, UUID id, Set<String> requestedRoles, Integer managerLevel) {
        UserRow target = requiredUser(id);
        assertCanManage(actor, target);
        if (target.root()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "نقش‌های مدیر صفر قابل تغییر نیست.");
        Set<String> roles = sanitizeNonRootRoles(requestedRoles);
        Integer level = normalizedManagerLevel(roles, managerLevel);
        replaceRoles(id, roles);
        jdbc.update("UPDATE app_users SET manager_level=?,updated_at=NOW() WHERE id=?", level, id);
        if (roles.contains("CUSTOMER")) ensureCustomerProfile(id, target.displayName(), target.phone(), target.email());
        audit(actor, id, "ACCOUNT_ROLES_UPDATED", "تغییر نقش‌ها");
        return staffUser(id);
    }

    @Transactional
    public StaffUser setStatus(AuthPrincipal actor, UUID id, String status) {
        UserRow target = requiredUser(id);
        assertCanManage(actor, target);
        if (target.root()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "مدیر صفر قابل غیرفعال‌سازی نیست.");
        if (!Set.of("ACTIVE", "DISABLED").contains(status)) throw bad("وضعیت نامعتبر است.");
        jdbc.update("UPDATE app_users SET status=?,updated_at=NOW() WHERE id=?", status, id);
        if ("DISABLED".equals(status)) jdbc.update("DELETE FROM auth_sessions WHERE user_id=?", id);
        audit(actor, id, "ACCOUNT_STATUS_UPDATED", status);
        return staffUser(id);
    }

    @Transactional
    public void resetPassword(AuthPrincipal actor, UUID id, String password, String confirmPassword) {
        UserRow target = requiredUser(id);
        assertCanManage(actor, target);
        validatePasswordPair(password, confirmPassword);
        jdbc.update("UPDATE app_users SET password_hash=?,password_changed_at=NOW(),updated_at=NOW() WHERE id=?",
                encoder.encode(password), id);
        jdbc.update("DELETE FROM auth_sessions WHERE user_id=?", id);
        audit(actor, id, "PASSWORD_RESET", target.root() ? "تغییر رمز مدیر صفر" : "بازنشانی رمز حساب");
    }

    public List<Map<String,Object>> history(AuthPrincipal actor, UUID id) {
        UserRow target = requiredUser(id);
        assertCanManage(actor, target);
        return jdbc.queryForList("""
                SELECT l.id,l.entity_type,l.action,l.source,l.reason,
                       l.before_data::text AS before_data,l.after_data::text AS after_data,
                       l.created_at,a.display_name AS actor_name,a.username AS actor_username
                FROM audit_log l
                LEFT JOIN app_users a ON a.id=l.actor_user_id
                WHERE (l.entity_type='app_users' AND l.entity_id=?)
                   OR (l.entity_type='user_roles' AND COALESCE(l.after_data->>'user_id',l.before_data->>'user_id')=?)
                ORDER BY l.created_at DESC,l.id DESC LIMIT 300
                """, id.toString(), id.toString());
    }

    public void bootstrapAdmin(String email, String username, String phone, String password, String name) {
        if (password == null || password.isBlank()) return;
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class);
        if (count != null && count > 0) return;
        validatePassword(password);
        String user = normalizeUsername(username == null || username.isBlank() ? "manager0" : username);
        String normalizedPhone = normalizePhone(phone == null || phone.isBlank() ? "+10000000000" : phone);
        String normalizedEmail = normalizeEmail(email);
        UUID id = jdbc.queryForObject("""
                INSERT INTO app_users(username,email,phone,display_name,password_hash,manager_level,is_root)
                VALUES (?,?,?,?,?,0,TRUE) RETURNING id
                """, UUID.class, user, normalizedEmail, normalizedPhone,
                name == null || name.isBlank() ? "مدیر صفر" : name.trim(), encoder.encode(password));
        replaceRoles(id, Set.of("SUPER_ADMIN", "MANAGER"));
    }

    private UserRow requiredUser(UUID id) {
        List<UserRow> rows = jdbc.query("""
                SELECT id,username,email,phone,display_name,password_hash,status,manager_level,is_root
                FROM app_users WHERE id=?
                """, (rs,n) -> mapUserRow(rs), id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "حساب پیدا نشد.");
        return rows.getFirst();
    }

    private UserRow mapUserRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new UserRow(rs.getObject("id", UUID.class), rs.getString("username"), rs.getString("email"),
                rs.getString("phone"), rs.getString("display_name"), rs.getString("password_hash"),
                rs.getString("status"), (Integer)rs.getObject("manager_level"), rs.getBoolean("is_root"));
    }

    private StaffUser staffUser(UUID id) {
        return users().stream().filter(u -> u.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "حساب پیدا نشد."));
    }

    private void assertAccountManager(AuthPrincipal actor) {
        if (actor == null || (!actor.root() && !actor.roles().contains("MANAGER")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "مجوز مدیریت حساب‌ها را ندارید.");
    }

    private void assertCanManage(AuthPrincipal actor, UserRow target) {
        assertAccountManager(actor);
        if (target.root() && !actor.id().equals(target.id()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "مدیر صفر فقط توسط خودش قابل ویرایش است.");
        if (!actor.root() && target.root())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "دسترسی به مدیر صفر مجاز نیست.");
    }

    private Set<String> sanitizeNonRootRoles(Set<String> input) {
        Set<String> value = input == null || input.isEmpty() ? Set.of("CUSTOMER") : input;
        if (value.contains("SUPER_ADMIN") || !NON_ROOT_ROLES.containsAll(value))
            throw bad("نقش نامعتبر است. SUPER_ADMIN فقط برای مدیر صفر محفوظ است.");
        return new LinkedHashSet<>(value);
    }

    private Integer normalizedManagerLevel(Set<String> roleSet, Integer requested) {
        if (!roleSet.contains("MANAGER")) return null;
        int level = requested == null ? 1 : requested;
        if (level < 1 || level > 99) throw bad("سطح مدیر باید بین ۱ تا ۹۹ باشد. سطح صفر فقط مدیر ریشه است.");
        return level;
    }

    private void ensureCustomerProfile(UUID id, String name, String phone, String email) {
        jdbc.update("""
                INSERT INTO customers(user_id,full_name,phone,email)
                VALUES (?,?,?,?)
                ON CONFLICT(user_id) DO UPDATE
                SET full_name=EXCLUDED.full_name,phone=EXCLUDED.phone,email=EXCLUDED.email,updated_at=NOW()
                """, id, name, phone, email);
    }

    private void replaceRoles(UUID id, Set<String> codes) {
        jdbc.update("DELETE FROM user_roles WHERE user_id=?", id);
        for (String code : codes) {
            if (jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code=?", id, code) == 0)
                throw bad("نقش نامعتبر: " + code);
        }
    }

    private Set<String> roles(UUID id) {
        return new LinkedHashSet<>(jdbc.query("""
                SELECT r.code FROM roles r JOIN user_roles ur ON ur.role_id=r.id
                WHERE ur.user_id=? ORDER BY r.code
                """, (rs,n) -> rs.getString("code"), id));
    }

    private void ensureIdentityAvailable(String username, String email, String phone, UUID exclude) {
        String excludeSql = exclude == null ? "" : " AND id<>?";
        List<Object> uArgs = new ArrayList<>(List.of(username)); if (exclude != null) uArgs.add(exclude);
        if (count("SELECT COUNT(*) FROM app_users WHERE LOWER(username)=LOWER(?)" + excludeSql, uArgs.toArray()) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "این نام کاربری قبلاً استفاده شده است.");

        List<Object> pArgs = new ArrayList<>(List.of(phone)); if (exclude != null) pArgs.add(exclude);
        if (count("SELECT COUNT(*) FROM app_users WHERE phone=?" + excludeSql, pArgs.toArray()) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "این شماره تلفن قبلاً استفاده شده است.");

        if (email != null) {
            List<Object> eArgs = new ArrayList<>(List.of(email)); if (exclude != null) eArgs.add(exclude);
            if (count("SELECT COUNT(*) FROM app_users WHERE email IS NOT NULL AND LOWER(email)=LOWER(?)" + excludeSql, eArgs.toArray()) > 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "این ایمیل قبلاً استفاده شده است.");
        }
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private void audit(AuthPrincipal actor, UUID id, String action, String reason) {
        jdbc.update("""
                INSERT INTO audit_log(actor_user_id,entity_type,entity_id,action,source,reason)
                VALUES (?,'app_users',?,?, 'APPLICATION',?)
                """, actor == null ? null : actor.id(), id.toString(), action, reason);
    }

    private void requireName(String value) {
        if (value == null || value.isBlank()) throw bad("نام الزامی است.");
    }

    private String normalizeUsername(String value) {
        if (value == null) throw bad("نام کاربری الزامی است.");
        String v = value.trim();
        if (!USERNAME.matcher(v).matches()) throw bad("نام کاربری باید ۳ تا ۸۰ کاراکتر و فقط شامل حروف، عدد، نقطه، خط تیره یا زیرخط باشد.");
        return v;
    }

    private String normalizePhone(String value) {
        String v = phoneCandidate(value);
        if (v == null || !PHONE.matcher(v).matches()) throw bad("شماره تلفن معتبر و الزامی است.");
        return v;
    }

    private String phoneCandidate(String value) {
        if (value == null) return null;
        String v = value.trim().replaceAll("[\\s()\\-]", "");
        return v.matches("^\\+?[0-9]+$") ? v : null;
    }

    private String normalizeEmail(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (!v.contains("@") || v.startsWith("@") || v.endsWith("@")) throw bad("ایمیل معتبر نیست.");
        return v;
    }

    private void validatePasswordPair(String password, String confirm) {
        validatePassword(password);
        if (confirm == null || !password.equals(confirm)) throw bad("رمز عبور و تکرار آن یکسان نیستند.");
    }

    private void validatePassword(String p) {
        if (p == null || p.length() < 12) throw bad("رمز عبور باید حداقل ۱۲ کاراکتر باشد.");
    }

    private String newToken() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
