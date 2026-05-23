package com.empik.coupon.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private Integer maxUses;

    @Column(nullable = false)
    private Integer currentUses;

    @Column(nullable = false, length = 2)
    private String country;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (currentUses == null) currentUses = 0;
        if (code != null) code = code.toUpperCase();
    }

    public boolean isExhausted() {
        return currentUses >= maxUses;
    }

    public boolean isAllowedForCountry(String countryCode) {
        return country.equalsIgnoreCase(countryCode);
    }
}
