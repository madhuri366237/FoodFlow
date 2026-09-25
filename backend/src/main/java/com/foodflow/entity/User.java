package com.foodflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Locale;

/**
 * An account. Table: users ("user" is a reserved word in PostgreSQL).
 *
 * <p>Deliberately has NO collections (addresses, restaurants, orders). Those are
 * loaded through their own repositories, e.g. {@code addressRepository.findByUserId(id)}.
 * See the Phase 2 notes on why the inverse {@code @OneToMany} side is omitted.
 *
 * <p>This class does not implement Spring Security's UserDetails; a separate adapter in
 * the security package (Phase 3) keeps the domain model independent of the framework.
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA, hidden from application code
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Setter(AccessLevel.NONE) // custom setter below normalises the value
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean enabled = true;

    public User(String name, String email, String passwordHash, String phone, Role role) {
        this.name = name;
        setEmail(email);
        this.passwordHash = passwordHash;
        this.phone = phone;
        this.role = role;
    }

    public void setEmail(String email) {
        this.email = normalizeEmail(email);
    }

    /** Single definition of "the same email", used when saving AND when looking users up. */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    // No toString(): it would risk printing passwordHash into logs.
}
