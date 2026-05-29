CREATE TABLE IF NOT EXISTS orders (
    order_id        UUID PRIMARY KEY,
    username        VARCHAR NOT NULL,
    shopping_cart_id UUID,
    payment_id      UUID,
    delivery_id     UUID,
    state           VARCHAR NOT NULL,
    delivery_weight NUMERIC,
    delivery_volume NUMERIC,
    fragile         BOOLEAN,
    total_price     NUMERIC,
    delivery_price  NUMERIC,
    product_price   NUMERIC
);

CREATE TABLE IF NOT EXISTS order_products (
    order_id   UUID REFERENCES orders(order_id),
    product_id UUID NOT NULL,
    quantity   BIGINT NOT NULL
);
