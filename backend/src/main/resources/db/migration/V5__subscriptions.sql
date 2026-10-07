CREATE TABLE subscription_plans (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    monthly_price NUMERIC(19,2) NOT NULL CHECK (monthly_price >= 0),
    billing_period_days INTEGER NOT NULL CHECK (billing_period_days BETWEEN 1 AND 3660),
    grace_period_days INTEGER NOT NULL CHECK (grace_period_days BETWEEN 0 AND 365),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX subscription_plans_name_idx ON subscription_plans(lower(name));

CREATE TABLE subscriptions (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL UNIQUE REFERENCES businesses(id),
    plan_id UUID NOT NULL REFERENCES subscription_plans(id),
    status VARCHAR(30) NOT NULL CHECK (status IN ('TRIAL', 'ACTIVE', 'GRACE_PERIOD', 'SUSPENDED', 'CANCELLED')),
    started_at TIMESTAMPTZ NOT NULL,
    trial_ends_at TIMESTAMPTZ,
    current_period_start TIMESTAMPTZ,
    current_period_end TIMESTAMPTZ,
    grace_ends_at TIMESTAMPTZ,
    next_billing_date TIMESTAMPTZ,
    suspended_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
ALTER TABLE subscriptions ADD CONSTRAINT subscriptions_business_id_id_unique UNIQUE(business_id, id);
CREATE INDEX subscriptions_status_dates_idx ON subscriptions(status, trial_ends_at, current_period_end, grace_ends_at);
CREATE INDEX subscriptions_plan_idx ON subscriptions(plan_id);

CREATE TABLE subscription_payments (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL,
    business_id UUID NOT NULL REFERENCES businesses(id),
    request_id UUID NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    payment_method VARCHAR(30) NOT NULL CHECK (payment_method IN ('MANUAL_TRANSFER', 'MANUAL_CASH', 'OTHER')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'CANCELLED')),
    external_reference VARCHAR(160),
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(business_id, request_id),
    FOREIGN KEY(business_id, subscription_id) REFERENCES subscriptions(business_id, id)
);
CREATE INDEX subscription_payments_subscription_idx ON subscription_payments(subscription_id, created_at DESC);
CREATE INDEX subscription_payments_business_status_idx ON subscription_payments(business_id, status, created_at DESC);

-- A non-expiring legacy plan preserves access for every business that predates billing.
INSERT INTO subscription_plans(
    id, name, description, monthly_price, billing_period_days, grace_period_days,
    active, created_at, updated_at
) VALUES (
    '00000000-0000-0000-0000-000000000101', 'Plan heredado',
    'Acceso conservado al incorporar suscripciones', 0, 3650, 30, TRUE, now(), now()
);
INSERT INTO subscriptions(
    id, business_id, plan_id, status, started_at, current_period_start,
    current_period_end, next_billing_date, created_at, updated_at
)
SELECT gen_random_uuid(), b.id, '00000000-0000-0000-0000-000000000101', 'ACTIVE',
       now(), now(), now() + interval '100 years', now() + interval '100 years', now(), now()
FROM businesses b;
