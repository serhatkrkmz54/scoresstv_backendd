package com.scorestv.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

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

    /** Admin coin yönetimi — e-posta VEYA görünen ada göre üye arama. */
    @Query("SELECT u FROM User u WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "ORDER BY u.email ASC")
    List<User> searchByEmailOrName(@Param("q") String q, Pageable pageable);
}
