CREATE TABLE coupons (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(100) NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    max_uses     INT NOT NULL CHECK (max_uses > 0),
    current_uses INT NOT NULL DEFAULT 0 CHECK (current_uses >= 0),
    country      VARCHAR(2) NOT NULL,
    CONSTRAINT uq_coupons_code UNIQUE (code),
    CONSTRAINT chk_current_uses_not_exceed_max CHECK (current_uses <= max_uses)
);

CREATE INDEX idx_coupons_code ON coupons (code);
