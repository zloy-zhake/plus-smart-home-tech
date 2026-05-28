CREATE TABLE IF NOT EXISTS carts (
    shopping_cart_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username         VARCHAR NOT NULL,
    active           BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS cart_products (
    cart_id     UUID REFERENCES carts(shopping_cart_id),
    product_id  UUID,
    quantity    BIGINT,
    PRIMARY KEY (cart_id, product_id)
);
