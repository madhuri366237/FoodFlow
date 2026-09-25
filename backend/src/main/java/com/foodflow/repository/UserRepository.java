package com.foodflow.repository;

import com.foodflow.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * Spring Data generates the implementation at startup by parsing method names:
 * findByEmail -> "select u from User u where u.email = ?1".
 *
 * <p>Callers must pass emails through {@link User#normalizeEmail(String)} first; the
 * column only ever contains lower-case values (enforced by a CHECK constraint).
 */
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

}
