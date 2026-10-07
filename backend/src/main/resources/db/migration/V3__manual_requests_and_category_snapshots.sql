-- Manual requests have their own operation namespace, independent of sales and scanner exits.
ALTER TABLE inventory_movements ADD CONSTRAINT inventory_movement_business_id_unique UNIQUE(business_id, id);
CREATE TABLE manual_inventory_requests (
    business_id UUID NOT NULL REFERENCES businesses(id),
    request_id UUID NOT NULL,
    request_fingerprint VARCHAR(100) NOT NULL,
    movement_id UUID NOT NULL,
    PRIMARY KEY(business_id, request_id),
    FOREIGN KEY(business_id, movement_id) REFERENCES inventory_movements(business_id, id)
);

-- Previous rows only recorded a name: their original category cannot be reconstructed safely.
-- Keep those snapshots unchanged; every new sale records its actual category identifier.
ALTER TABLE sale_items ADD COLUMN category_id_snapshot UUID;
ALTER TABLE sale_items ADD CONSTRAINT sale_item_category_snapshot_fk
    FOREIGN KEY(business_id, category_id_snapshot) REFERENCES categories(business_id, id);
CREATE INDEX sale_items_category_idx ON sale_items(business_id, category_id_snapshot);
