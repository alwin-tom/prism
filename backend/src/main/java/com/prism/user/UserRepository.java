package com.prism.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            select u from UserAccount u
            where lower(u.fullName) like lower(concat('%', :query, '%'))
               or u.phoneNumber like concat('%', :query, '%')
               or lower(coalesce(u.email, '')) like lower(concat('%', :query, '%'))
            """)
    Page<UserAccount> search(String query, Pageable pageable);
}
