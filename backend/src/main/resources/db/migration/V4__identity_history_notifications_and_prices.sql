-- Identity hierarchy, durable history, notifications, and price validity.
ALTER TABLE app_users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS username VARCHAR(80);
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS phone VARCHAR(40);
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS manager_level SMALLINT;
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS is_root BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMPTZ;

UPDATE app_users
SET username = 'user_' || substring(replace(id::text,'-','') from 1 for 8)
WHERE username IS NULL OR btrim(username) = '';

UPDATE app_users u
SET phone = c.phone
FROM customers c
WHERE c.user_id = u.id
  AND u.phone IS NULL
  AND c.phone IS NOT NULL
  AND btrim(c.phone) <> '';

CREATE UNIQUE INDEX IF NOT EXISTS uq_app_users_username_ci ON app_users (LOWER(username));
CREATE UNIQUE INDEX IF NOT EXISTS uq_app_users_phone ON app_users (phone) WHERE phone IS NOT NULL;

INSERT INTO roles(code,title_fa,description_fa)
VALUES ('ADMIN','ادمین','مدیریت روزمره کاتالوگ، درخواست مشتری و عملیات غیرمالی')
ON CONFLICT(code) DO UPDATE SET title_fa=EXCLUDED.title_fa,description_fa=EXCLUDED.description_fa;

WITH candidate AS (
    SELECT u.id
    FROM app_users u
    JOIN user_roles ur ON ur.user_id=u.id
    JOIN roles r ON r.id=ur.role_id
    WHERE r.code='SUPER_ADMIN'
    ORDER BY u.created_at,u.id
    LIMIT 1
)
UPDATE app_users u
SET is_root=TRUE, manager_level=0,
    username=CASE WHEN NOT EXISTS(SELECT 1 FROM app_users x WHERE LOWER(x.username)='manager0' AND x.id<>u.id) THEN 'manager0' ELSE u.username END
FROM candidate c
WHERE u.id=c.id;

UPDATE app_users u
SET manager_level=1
WHERE manager_level IS NULL
  AND is_root=FALSE
  AND EXISTS (
      SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id
      WHERE ur.user_id=u.id AND r.code='MANAGER'
  );

ALTER TABLE app_users ADD CONSTRAINT ck_app_users_manager_level CHECK (manager_level IS NULL OR manager_level BETWEEN 0 AND 99);
CREATE UNIQUE INDEX uq_app_users_single_root ON app_users ((is_root)) WHERE is_root=TRUE;

ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS source VARCHAR(20) NOT NULL DEFAULT 'DATABASE';
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS reason TEXT;

CREATE TABLE product_prices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    amount NUMERIC(18,4) NOT NULL CHECK(amount>=0),
    currency VARCHAR(3) NOT NULL,
    valid_from DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_until DATE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','REPLACED','CANCELLED')),
    note TEXT,
    created_by_user_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK(valid_until>=valid_from)
);
CREATE INDEX idx_product_prices_product ON product_prices(product_id,created_at DESC);
CREATE UNIQUE INDEX uq_product_prices_one_active ON product_prices(product_id) WHERE status='ACTIVE';

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_user_id UUID REFERENCES app_users(id) ON DELETE CASCADE,
    recipient_phone VARCHAR(80),
    recipient_email VARCHAR(320),
    audience VARCHAR(16) NOT NULL CHECK(audience IN ('STAFF','CUSTOMER')),
    channel VARCHAR(16) NOT NULL CHECK(channel IN ('IN_APP','MANUAL','EMAIL','SMS','WHATSAPP')),
    status VARCHAR(16) NOT NULL CHECK(status IN ('UNREAD','READ','QUEUED','SENT','CANCELLED')),
    kind VARCHAR(60) NOT NULL,
    title VARCHAR(220) NOT NULL,
    body TEXT NOT NULL,
    link VARCHAR(500),
    case_id UUID REFERENCES workflow_cases(id) ON DELETE CASCADE,
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    created_by_user_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK(recipient_user_id IS NOT NULL OR recipient_phone IS NOT NULL OR recipient_email IS NOT NULL)
);
CREATE INDEX idx_notifications_user_unread ON notifications(recipient_user_id,status,created_at DESC);
CREATE INDEX idx_notifications_case ON notifications(case_id,created_at DESC);

INSERT INTO tags(slug,label_fa,color_key,scope,is_system) VALUES
('price-valid','قیمت معتبر موجود','green','CASE',TRUE),
('price-needed','نیازمند استعلام قیمت','amber','CASE',TRUE),
('product-changed','محصول تغییر کرده','violet','CASE',TRUE)
ON CONFLICT(slug) DO UPDATE SET label_fa=EXCLUDED.label_fa,color_key=EXCLUDED.color_key,is_system=TRUE,is_active=TRUE;

CREATE OR REPLACE FUNCTION write_generic_audit() RETURNS TRIGGER AS $$
DECLARE
    b JSONB;
    a JSONB;
    eid TEXT;
BEGIN
    IF TG_OP='INSERT' THEN b=NULL; a=to_jsonb(NEW);
    ELSIF TG_OP='DELETE' THEN b=to_jsonb(OLD); a=NULL;
    ELSE b=to_jsonb(OLD); a=to_jsonb(NEW);
    END IF;

    eid=COALESCE(a->>'id',b->>'id',
        concat_ws(':',COALESCE(a->>'user_id',b->>'user_id'),COALESCE(a->>'role_id',b->>'role_id')),
        concat_ws(':',COALESCE(a->>'case_id',b->>'case_id'),COALESCE(a->>'tag_id',b->>'tag_id')),
        'unknown');

    INSERT INTO audit_log(entity_type,entity_id,action,before_data,after_data,source)
    VALUES(TG_TABLE_NAME,eid,TG_OP,b,a,'DATABASE');

    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'app_users','user_roles','customers','products','product_images','product_code_details','product_prices',
        'vehicle_type_codes','vehicle_brand_codes','part_brand_codes','packaging_codes','quality_codes','product_group_codes','unit_codes','physical_codes',
        'customer_requests','workflow_cases','tags','case_tags','supplier_quotes','customer_quotes','sales_orders','order_items','payments','shipments'
    ] LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS audit_row_changes ON %I',t);
        EXECUTE format('CREATE TRIGGER audit_row_changes AFTER INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION write_generic_audit()',t);
    END LOOP;
END $$;
