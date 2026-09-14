CREATE TABLE orders (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     VARCHAR(64) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    total_amount    NUMERIC(12,2) NOT NULL,
    failure_reason  VARCHAR(255),
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_orders_customer_id ON orders (customer_id);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_created_at ON orders (created_at);

CREATE TABLE order_items (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    sku         VARCHAR(64) NOT NULL,
    quantity    INTEGER NOT NULL,
    price       NUMERIC(12,2) NOT NULL
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_order_items_sku ON order_items (sku);
