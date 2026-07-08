package com.scorestv.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleId(String googleId);

    Optional<User> findByAppleId(String appleId);

    boolean existsByEmail(String email);

    /** Panel "Editör Yönetimi" — yalniz staff (EDITOR/ADMIN) hesaplari. */
    List<User> findByRoleInOrderByEmailAsc(Collection<Role> roles);
}
