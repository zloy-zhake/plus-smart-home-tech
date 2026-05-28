CREATE TABLE IF NOT EXISTS products (
    product_id  UUID PRIMARY KEY,
    fragile     BOOLEAN DEFAULT FALSE,
    width       DOUBLE PRECISION,
    height      DOUBLE PRECISION,
    depth       DOUBLE PRECISION,
    weight      DOUBLE PRECISION,
    quantity    BIGINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS order_bookings (
    id          UUID PRIMARY KEY,
    order_id    UUID NOT NULL,
    delivery_id UUID
);

CREATE TABLE IF NOT EXISTS order_booking_products (
    booking_id  UUID REFERENCES order_bookings(id),
    product_id  UUID NOT NULL,
    quantity    BIGINT NOT NULL
);
