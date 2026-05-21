package com.re.smart_cinema_booking_system.repository;

import com.re.smart_cinema_booking_system.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    /**
     * Đếm tổng số khách hàng (role = CUSTOMER, status = ACTIVE)
     */
    @Query(value = "SELECT COUNT(id) FROM users WHERE role = 'CUSTOMER' AND status = 'ACTIVE'", nativeQuery = true)
    long countActiveCustomers();
}
