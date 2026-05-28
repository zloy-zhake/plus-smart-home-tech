CREATE TABLE IF NOT EXISTS payments (
    payment_id     UUID PRIMARY KEY,
    order_id       UUID NOT NULL,
    product_price  NUMERIC,
    delivery_price NUMERIC,
    fee_total      NUMERIC,
    total_payment  NUMERIC,
    state          VARCHAR NOT NULL
);
