CREATE TABLE IF NOT EXISTS deliveries (
    delivery_id    UUID PRIMARY KEY,
    order_id       UUID NOT NULL,
    delivery_state VARCHAR NOT NULL,
    delivery_weight NUMERIC,
    delivery_volume NUMERIC,
    fragile        BOOLEAN,
    from_country   VARCHAR,
    from_city      VARCHAR,
    from_street    VARCHAR,
    from_house     VARCHAR,
    from_flat      VARCHAR,
    to_country     VARCHAR,
    to_city        VARCHAR,
    to_street      VARCHAR,
    to_house       VARCHAR,
    to_flat        VARCHAR
);
