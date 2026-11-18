package com.swiftdrop.admin;

import com.swiftdrop.admin.dto.CreateStaffRequest;
import com.swiftdrop.audit.AuditService;
import com.swiftdrop.auth.RefreshTokenRepository;
import com.swiftdrop.common.exception.ApiException;
import com.swiftdrop.courier.Courier;
import com.swiftdrop.courier.CourierRepository;
import com.swiftdrop.pricing.ZoneRepository;
import com.swiftdrop.user.Role;
import com.swiftdrop.user.RoleName;
import com.swiftdrop.user.RoleRepository;
import com.swiftdrop.user.User;
import com.swiftdrop.user.UserRepository;
import com.swiftdrop.user.UserStatus;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Admin use case "Manage users" (report 2.7). Every change is written to the audit log. */
@Service
public class AdminUserService {

    private static final Set<RoleName> STAFF_ROLES = EnumSet.of(RoleName.COURIER, RoleName.DISPATCHER, RoleName.ADMIN);

    private final UserRepository users;
    private final RoleRepository roles;
    private final CourierRepository couriers;
    private final ZoneRepository zones;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public AdminUserService(UserRepository users, RoleRepository roles, CourierRepository couriers,
                            ZoneRepository zones, RefreshTokenRepository refreshTokens,
                            PasswordEncoder passwordEncoder, AuditService audit) {
        this.users = users;
        this.roles = roles;
        this.couriers = couriers;
        this.zones = zones;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> list() {
        return users.findAll(Sort.by("createdAt").descending()).stream()
                .map(AdminUserResponse::from)
                .toList();
    }

    @Transactional
    public AdminUserResponse createStaff(CreateStaffRequest request, UUID adminId) {
        if (!STAFF_ROLES.contains(request.role())) {
            throw ApiException.badRequest("Admins can create COURIER, DISPATCHER or ADMIN accounts only");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone());
        user.setRole(role(request.role()));
        users.save(user);

        if (request.role() == RoleName.COURIER) {
            createCourierProfile(user, request.zoneId(), request.vehicleType(), request.vehicleNumber());
        }
        audit.record(adminId, "USER_CREATED", "USER", user.getId(), null, request.role().name());
        return AdminUserResponse.from(user);
    }

    /** Disable or re-enable an account. Disabling also ends all of the user's sessions. */
    @Transactional
    public AdminUserResponse changeStatus(UUID userId, UserStatus status, UUID adminId) {
        User user = load(userId);
        if (user.getId().equals(adminId) && status == UserStatus.DISABLED) {
            throw ApiException.badRequest("You cannot disable your own account");
        }
        UserStatus old = user.getStatus();
        user.setStatus(status);
        if (status == UserStatus.DISABLED) {
            refreshTokens.revokeAllForUser(user.getId(), Instant.now());
        }
        audit.record(adminId, "USER_STATUS_CHANGED", "USER", user.getId(), old.name(), status.name());
        return AdminUserResponse.from(user);
    }

    @Transactional
    public AdminUserResponse changeRole(UUID userId, RoleName newRole, UUID adminId) {
        User user = load(userId);
        if (user.getId().equals(adminId)) {
            throw ApiException.badRequest("You cannot change your own role");
        }
        RoleName old = user.roleName();
        if (old == newRole) {
            return AdminUserResponse.from(user);
        }
        if (old == RoleName.MERCHANT || newRole == RoleName.MERCHANT) {
            throw ApiException.badRequest("Merchant accounts are managed through merchant registration");
        }
        user.setRole(role(newRole));
        if (newRole == RoleName.COURIER && couriers.findByUserId(user.getId()).isEmpty()) {
            createCourierProfile(user, null, null, null);
        }
        // Old access tokens still carry the old role for up to 15 minutes; force a new login.
        refreshTokens.revokeAllForUser(user.getId(), Instant.now());
        audit.record(adminId, "ROLE_CHANGED", "USER", user.getId(), old.name(), newRole.name());
        return AdminUserResponse.from(user);
    }

    private void createCourierProfile(User user, Integer zoneId, String vehicleType, String vehicleNumber) {
        Courier courier = new Courier();
        courier.setUser(user);
        if (zoneId != null) {
            courier.setZone(zones.findById(zoneId).orElseThrow(() -> ApiException.badRequest("Zone not found")));
        }
        courier.setVehicleType(vehicleType);
        courier.setVehicleNumber(vehicleNumber);
        couriers.save(courier);
    }

    private User load(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private Role role(RoleName name) {
        return roles.findByName(name).orElseThrow(() -> new IllegalStateException("Role missing: " + name));
    }

    public record AdminUserResponse(UUID id, String email, String fullName, String phone, String role,
                                    String status, boolean googleLinked, Instant createdAt) {
        static AdminUserResponse from(User u) {
            return new AdminUserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(),
                    u.roleName().name(), u.getStatus().name(), u.getGoogleId() != null, u.getCreatedAt());
        }
    }
}
