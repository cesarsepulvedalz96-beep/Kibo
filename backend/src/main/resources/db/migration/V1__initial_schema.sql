CREATE TABLE businesses (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    tax_id VARCHAR(40),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    business_id UUID REFERENCES businesses(id),
    email VARCHAR(254) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(30) NOT NULL CHECK (role IN ('SUPER_ADMIN', 'BUSINESS_ADMIN', 'CASHIER')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    access_version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((role = 'SUPER_ADMIN' AND business_id IS NULL) OR (role <> 'SUPER_ADMIN' AND business_id IS NOT NULL)),
    UNIQUE(business_id, id)
);
CREATE INDEX users_business_idx ON users(business_id);

CREATE TABLE business_settings (
    business_id UUID PRIMARY KEY REFERENCES businesses(id),
    operating_mode VARCHAR(30) NOT NULL DEFAULT 'POS' CHECK (operating_mode IN ('POS', 'INVENTORY_ONLY')),
    scanner_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    stock_alerts BOOLEAN NOT NULL DEFAULT TRUE,
    tax_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    tax_rate NUMERIC(5,4) NOT NULL DEFAULT 0.19 CHECK(tax_rate BETWEEN 0 AND 1),
    generate_receipt BOOLEAN NOT NULL DEFAULT TRUE,
    timezone VARCHAR(80) NOT NULL DEFAULT 'America/Santiago'
);

CREATE TABLE categories (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id),
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(business_id, id)
);
CREATE UNIQUE INDEX categories_name_idx ON categories(business_id, lower(name));

CREATE TABLE products (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id),
    category_id UUID NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(2000),
    barcode VARCHAR(80),
    sale_price NUMERIC(19,2) NOT NULL CHECK(sale_price >= 0),
    cost_price NUMERIC(19,2) CHECK(cost_price >= 0),
    stock INTEGER NOT NULL DEFAULT 0 CHECK(stock >= 0),
    minimum_stock INTEGER NOT NULL DEFAULT 0 CHECK(minimum_stock >= 0),
    image_key VARCHAR(512),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(business_id, id),
    UNIQUE(business_id, barcode),
    FOREIGN KEY(business_id, category_id) REFERENCES categories(business_id, id)
);
CREATE INDEX products_category_idx ON products(business_id, category_id);
CREATE INDEX products_low_stock_idx ON products(business_id) WHERE active AND stock <= minimum_stock;
CREATE INDEX products_name_idx ON products(business_id, lower(name));

CREATE TABLE sales (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id),
    user_id UUID NOT NULL,
    subtotal NUMERIC(19,2) NOT NULL CHECK(subtotal >= 0),
    tax_amount NUMERIC(19,2) NOT NULL CHECK(tax_amount >= 0),
    total NUMERIC(19,2) NOT NULL CHECK(total = subtotal + tax_amount),
    payment_method VARCHAR(20) NOT NULL CHECK(payment_method IN ('CASH', 'CARD', 'TRANSFER', 'EXTERNAL')),
    payment_mode VARCHAR(30) NOT NULL CHECK(payment_mode IN ('MANUAL', 'EXTERNAL_RECORDED')),
    payment_reference VARCHAR(120),
    status VARCHAR(20) NOT NULL CHECK(status IN ('PENDING', 'CONFIRMED', 'CANCELLED')),
    request_id UUID NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE(business_id, id),
    UNIQUE(business_id, request_id),
    FOREIGN KEY(business_id, user_id) REFERENCES users(business_id, id)
);
CREATE INDEX sales_created_idx ON sales(business_id, created_at) WHERE status = 'CONFIRMED';

CREATE TABLE sale_items (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id),
    sale_id UUID NOT NULL,
    product_id UUID NOT NULL,
    product_name_snapshot VARCHAR(160) NOT NULL,
    category_name_snapshot VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL CHECK(quantity > 0),
    unit_price NUMERIC(19,2) NOT NULL CHECK(unit_price >= 0),
    tax_amount NUMERIC(19,2) NOT NULL CHECK(tax_amount >= 0),
    subtotal NUMERIC(19,2) NOT NULL CHECK(subtotal >= 0),
    FOREIGN KEY(business_id, sale_id) REFERENCES sales(business_id, id),
    FOREIGN KEY(business_id, product_id) REFERENCES products(business_id, id)
);
CREATE INDEX sale_items_sale_idx ON sale_items(business_id, sale_id);
CREATE INDEX sale_items_product_idx ON sale_items(business_id, product_id);

CREATE TABLE inventory_movements (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id),
    product_id UUID NOT NULL,
    type VARCHAR(30) NOT NULL CHECK(type IN ('PURCHASE_ENTRY', 'SALE', 'MANUAL_ENTRY', 'MANUAL_EXIT', 'ADJUSTMENT', 'RETURN')),
    quantity INTEGER NOT NULL,
    previous_stock INTEGER NOT NULL CHECK(previous_stock >= 0),
    new_stock INTEGER NOT NULL CHECK(new_stock >= 0),
    reference_id UUID,
    performed_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CHECK(new_stock = previous_stock + quantity),
    FOREIGN KEY(business_id, product_id) REFERENCES products(business_id, id),
    FOREIGN KEY(business_id, performed_by) REFERENCES users(business_id, id)
);
CREATE INDEX inventory_created_idx ON inventory_movements(business_id, created_at DESC);
CREATE INDEX inventory_product_idx ON inventory_movements(business_id, product_id, created_at DESC);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id),
    product_id UUID NOT NULL,
    product_name VARCHAR(160) NOT NULL,
    stock INTEGER NOT NULL,
    read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY(business_id, product_id) REFERENCES products(business_id, id)
);
CREATE INDEX notifications_unread_idx ON notifications(business_id, created_at DESC) WHERE NOT read;

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    business_id UUID REFERENCES businesses(id),
    user_id UUID NOT NULL REFERENCES users(id),
    action VARCHAR(80) NOT NULL,
    entity VARCHAR(80) NOT NULL,
    entity_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_business_created_idx ON audit_events(business_id, created_at DESC);

CREATE TABLE spring_session (
    primary_id CHAR(36) PRIMARY KEY,
    session_id CHAR(36) NOT NULL UNIQUE,
    creation_time BIGINT NOT NULL,
    last_access_time BIGINT NOT NULL,
    max_inactive_interval INTEGER NOT NULL,
    expiry_time BIGINT NOT NULL,
    principal_name VARCHAR(100)
);
CREATE INDEX spring_session_expiry_idx ON spring_session(expiry_time);
CREATE INDEX spring_session_principal_idx ON spring_session(principal_name);
CREATE TABLE spring_session_attributes (
    session_primary_id CHAR(36) NOT NULL REFERENCES spring_session(primary_id) ON DELETE CASCADE,
    attribute_name VARCHAR(200) NOT NULL,
    attribute_bytes BYTEA NOT NULL,
    PRIMARY KEY(session_primary_id, attribute_name)
);
