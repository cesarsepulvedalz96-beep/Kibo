-- Commercial roles are intentionally collapsed into one role without losing users or history.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_check;
UPDATE users SET role = 'BUSINESS_USER' WHERE role IN ('BUSINESS_ADMIN', 'CASHIER');
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('SUPER_ADMIN', 'BUSINESS_USER'));
ALTER TABLE users ADD CONSTRAINT users_business_role_check CHECK (
    (role = 'SUPER_ADMIN' AND business_id IS NULL)
    OR (role = 'BUSINESS_USER' AND business_id IS NOT NULL)
);
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMPTZ;

-- Scheduled transitions have no human user. Keeping the actor kind explicit avoids a fake user.
ALTER TABLE audit_events ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE audit_events ADD COLUMN actor_type VARCHAR(20) NOT NULL DEFAULT 'USER';
ALTER TABLE audit_events ADD CONSTRAINT audit_actor_check CHECK (
    (actor_type = 'USER' AND user_id IS NOT NULL)
    OR (actor_type = 'SYSTEM' AND user_id IS NULL)
);

