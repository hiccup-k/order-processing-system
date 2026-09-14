CREATE TABLE inventory_items (
    id                  BIGSERIAL PRIMARY KEY,
    sku                 VARCHAR(64) NOT NULL UNIQUE,
    available_quantity  INTEGER NOT NULL,
    reserved_quantity   INTEGER NOT NULL DEFAULT 0,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_inventory_items_sku ON inventory_items (sku);

CREATE TABLE processed_order_events (
    order_id        BIGINT PRIMARY KEY,
    processed_at    TIMESTAMP NOT NULL
);

-- Demo seed data so the happy path works out of the box against docker-compose.
INSERT INTO inventory_items (sku, available_quantity, reserved_quantity) VALUES
    ('SKU-1', 100, 0),
    ('SKU-2', 50, 0),
    ('SKU-3', 5, 0);
