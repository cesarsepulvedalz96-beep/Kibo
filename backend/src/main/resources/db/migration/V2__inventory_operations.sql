CREATE TABLE inventory_operations (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id),
    request_id UUID NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    performed_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(business_id, request_id),
    FOREIGN KEY(business_id, performed_by) REFERENCES users(business_id, id)
);
CREATE INDEX inventory_operations_created_idx ON inventory_operations(business_id, created_at DESC);
