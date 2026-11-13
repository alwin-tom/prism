package com.prism.user;

import com.prism.shared.exception.ApiException;
import com.prism.shared.security.UserPrincipal;
import com.prism.shared.util.PhoneNumbers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private final UserRepository users;
    private final UserRoleRepository roles;

    public AccountService(UserRepository users, UserRoleRepository roles) {
        this.users = users;
        this.roles = roles;
    }

    public UserPrincipal principalOf(UserAccount user) {
        List<String> roleNames = roles.findByUserId(user.id).stream()
                .map(role -> role.role)
                .sorted(Comparator.naturalOrder())
                .toList();
        return new UserPrincipal(user.id, user.phoneNumber, user.fullName, roleNames);
    }

    public AccountView view(UserAccount user) {
        UserPrincipal principal = principalOf(user);
        return new AccountView(
                user.id,
                user.fullName,
                user.phoneNumber,
                PhoneNumbers.display(user.phoneNumber),
                user.email,
                principal.roles(),
                user.status);
    }

    public UserAccount require(UUID id) {
        UserAccount user = users.findById(id).orElseThrow(() -> ApiException.notFound("That account was not found."));
        if (!"ACTIVE".equals(user.status)) {
            throw ApiException.forbidden("This account is disabled.");
        }
        return user;
    }

    @Transactional
    public UserAccount createCustomer(String fullName, String phone) {
        UserAccount user = new UserAccount();
        user.fullName = fullName.trim();
        user.phoneNumber = phone;
        user.status = "ACTIVE";
        users.save(user);
        grant(user.id, "CUSTOMER");
        return user;
    }

    @Transactional
    public void grant(UUID userId, String role) {
        if (!roles.existsByUserIdAndRole(userId, role)) {
            UserRoleEntity entity = new UserRoleEntity();
            entity.userId = userId;
            entity.role = role;
            roles.save(entity);
        }
    }

    public record AccountView(
            UUID id,
            String fullName,
            String phoneNumber,
            String phoneDisplay,
            String email,
            List<String> roles,
            String status
    ) {
    }
}
