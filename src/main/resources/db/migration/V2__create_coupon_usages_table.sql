CREATE TABLE coupon_usages (
    id         BIGSERIAL PRIMARY KEY,
    coupon_id  BIGINT NOT NULL REFERENCES coupons(id),
    user_id    VARCHAR(255) NOT NULL,
    used_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_coupon_usages_coupon_user UNIQUE (coupon_id, user_id)
);

CREATE INDEX idx_coupon_usages_coupon_id ON coupon_usages (coupon_id);
CREATE INDEX idx_coupon_usages_user_id ON coupon_usages (user_id);
