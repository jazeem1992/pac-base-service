-- =====================================================================================
-- V1: customer table (sample feature)
-- Flyway migrations are immutable once applied: never edit, always add a new V<n>__*.sql file.
-- =====================================================================================

-- Pooled sequence: increment must equal the JPA allocationSize (50) to allow batched inserts
CREATE SEQUENCE IF NOT EXISTS customer_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS customer
(
    id           BIGINT                   NOT NULL,
    first_name   VARCHAR(100)             NOT NULL,
    last_name    VARCHAR(100)             NOT NULL,
    email        VARCHAR(255)             NOT NULL,
    phone_number VARCHAR(20),
    status       VARCHAR(20)              NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by   VARCHAR(100)             NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by   VARCHAR(100)             NOT NULL,
    version      BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_customer PRIMARY KEY (id),
    CONSTRAINT uk_customer_email UNIQUE (email)
);

CREATE INDEX IF NOT EXISTS idx_customer_status ON customer (status);
