CREATE TABLE IF NOT EXISTS products (
    product_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_name    VARCHAR NOT NULL,
    description     VARCHAR,
    image_src       VARCHAR,
    quantity_state  VARCHAR,
    product_state   VARCHAR,
    product_category VARCHAR,
    price           NUMERIC
);
