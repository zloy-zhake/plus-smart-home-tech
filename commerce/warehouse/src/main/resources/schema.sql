CREATE TABLE IF NOT EXISTS products (
    product_id  UUID PRIMARY KEY,
    fragile     BOOLEAN DEFAULT FALSE,
    width       DOUBLE PRECISION,
    height      DOUBLE PRECISION,
    depth       DOUBLE PRECISION,
    weight      DOUBLE PRECISION,
    quantity    BIGINT DEFAULT 0
);
