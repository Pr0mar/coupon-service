package com.empik.coupon.repository;

import com.empik.coupon.domain.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCode(String code);

    boolean existsByCode(String code);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Coupon c
           SET c.currentUses = c.currentUses + 1
         WHERE c.code = :code
           AND c.currentUses < c.maxUses
        """)
    int incrementUsesIfAvailable(@Param("code") String code);
}
