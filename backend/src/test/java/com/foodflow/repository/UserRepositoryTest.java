package com.foodflow.repository;

import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRepositoryTest extends RepositoryTestBase {

    @Autowired
    private UserRepository userRepository;

    @Test
    void emailIsNormalisedAndFoundByNormalisedLookup() {
        userRepository.saveAndFlush(new User("Asha", "  Asha@Example.COM ", PASSWORD_HASH, null, Role.CUSTOMER));

        assertThat(userRepository.findByEmail(User.normalizeEmail("ASHA@example.com")))
                .isPresent()
                .get()
                .extracting(User::getEmail)
                .isEqualTo("asha@example.com");
        assertThat(userRepository.existsByEmail("asha@example.com")).isTrue();
    }

    @Test
    void duplicateEmailIsRejectedByUniqueConstraint() {
        userRepository.saveAndFlush(new User("First", "dup@example.com", PASSWORD_HASH, null, Role.CUSTOMER));

        // Different case, same normalised email -> uk_users_email violation.
        assertThatThrownBy(() -> userRepository.saveAndFlush(
                new User("Second", "DUP@example.com", PASSWORD_HASH, null, Role.CUSTOMER)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_users_email");
    }

    @Test
    void databaseRejectsUpperCaseEmailEvenWhenJavaIsBypassed() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into users (name, email, password_hash, role) values ('X', 'Upper@Example.com', 'h', 'CUSTOMER')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_users_email_lowercase");
    }

    @Test
    void databaseRejectsUnknownRole() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into users (name, email, password_hash, role) values ('X', 'x@example.com', 'h', 'SUPERUSER')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_users_role");
    }

    @Test
    void roleIsStoredAsItsNameNotItsOrdinal() {
        User owner = userRepository.saveAndFlush(
                new User("Owner", "owner@example.com", PASSWORD_HASH, null, Role.RESTAURANT_OWNER));

        String stored = jdbc.queryForObject("select role from users where id = ?", String.class, owner.getId());

        assertThat(stored).isEqualTo("RESTAURANT_OWNER");
    }

    @Test
    void timestampsAreSetOnInsert() {
        User user = userRepository.saveAndFlush(new User("T", "t@example.com", PASSWORD_HASH, null, Role.CUSTOMER));

        assertThat(user.getId()).isNotNull();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
        assertThat(user.isEnabled()).isTrue();
    }
}
