-- Vehicle-brand codes are scoped by vehicle type.
-- This allows, for example, brand code 01 under Sedan and another brand code 01 under Truck.

INSERT INTO vehicle_type_codes (code, description_fa, description_en, is_active)
VALUES (99, 'قدیمی', 'Legacy', FALSE)
ON CONFLICT (code) DO UPDATE SET is_active = FALSE;

ALTER TABLE vehicle_brand_codes
    ADD COLUMN vehicle_type_code_id UUID;

-- Preserve any old global-brand rows for historical product references, but hide them
-- from new product creation. New active rows below use the scoped model.
UPDATE vehicle_brand_codes
SET vehicle_type_code_id = (SELECT id FROM vehicle_type_codes WHERE code = 99),
    is_active = FALSE
WHERE vehicle_type_code_id IS NULL;

ALTER TABLE vehicle_brand_codes
    ALTER COLUMN vehicle_type_code_id SET NOT NULL;

ALTER TABLE vehicle_brand_codes
    ADD CONSTRAINT fk_vehicle_brand_vehicle_type
        FOREIGN KEY (vehicle_type_code_id) REFERENCES vehicle_type_codes(id);

ALTER TABLE vehicle_brand_codes
    DROP CONSTRAINT IF EXISTS vehicle_brand_codes_code_key;

ALTER TABLE vehicle_brand_codes
    ADD CONSTRAINT uq_vehicle_brand_code_per_type UNIQUE (vehicle_type_code_id, code);

CREATE INDEX idx_vehicle_brand_codes_type
    ON vehicle_brand_codes (vehicle_type_code_id, is_active, code);

-- Add Truck as a dedicated vehicle type without renumbering any existing type codes.
INSERT INTO vehicle_type_codes (code, description_fa, description_en)
VALUES (5, 'کامیون', 'Truck')
ON CONFLICT (code) DO UPDATE
SET description_fa = EXCLUDED.description_fa,
    description_en = EXCLUDED.description_en,
    is_active = TRUE;

-- Fresh active brand lists scoped to each vehicle type.
-- Sedan: 01 Mercedes-Benz, 02 BMW (as requested).
INSERT INTO vehicle_brand_codes (vehicle_type_code_id, code, description_fa, description_en)
SELECT vt.id, b.code, b.description_fa, b.description_en
FROM vehicle_type_codes vt
JOIN (VALUES
    (1, 'مرسدس بنز', 'Mercedes-Benz'),
    (2, 'بی‌ام‌و', 'BMW'),
    (3, 'بی‌وای‌دی', 'BYD'),
    (4, 'تویوتا', 'Toyota')
) AS b(code, description_fa, description_en) ON TRUE
WHERE vt.code = 1
ON CONFLICT (vehicle_type_code_id, code) DO UPDATE
SET description_fa = EXCLUDED.description_fa,
    description_en = EXCLUDED.description_en,
    is_active = TRUE;

INSERT INTO vehicle_brand_codes (vehicle_type_code_id, code, description_fa, description_en)
SELECT vt.id, b.code, b.description_fa, b.description_en
FROM vehicle_type_codes vt
JOIN (VALUES
    (1, 'تویوتا', 'Toyota'),
    (2, 'نیسان', 'Nissan'),
    (3, 'بی‌ام‌و', 'BMW'),
    (4, 'مرسدس بنز', 'Mercedes-Benz')
) AS b(code, description_fa, description_en) ON TRUE
WHERE vt.code = 2
ON CONFLICT (vehicle_type_code_id, code) DO UPDATE
SET description_fa = EXCLUDED.description_fa,
    description_en = EXCLUDED.description_en,
    is_active = TRUE;

INSERT INTO vehicle_brand_codes (vehicle_type_code_id, code, description_fa, description_en)
SELECT vt.id, b.code, b.description_fa, b.description_en
FROM vehicle_type_codes vt
JOIN (VALUES
    (1, 'فولکس‌واگن', 'Volkswagen'),
    (2, 'پژو', 'Peugeot'),
    (3, 'رنو', 'Renault'),
    (4, 'تویوتا', 'Toyota')
) AS b(code, description_fa, description_en) ON TRUE
WHERE vt.code = 3
ON CONFLICT (vehicle_type_code_id, code) DO UPDATE
SET description_fa = EXCLUDED.description_fa,
    description_en = EXCLUDED.description_en,
    is_active = TRUE;

INSERT INTO vehicle_brand_codes (vehicle_type_code_id, code, description_fa, description_en)
SELECT vt.id, b.code, b.description_fa, b.description_en
FROM vehicle_type_codes vt
JOIN (VALUES
    (1, 'تویوتا', 'Toyota'),
    (2, 'فورد', 'Ford'),
    (3, 'نیسان', 'Nissan'),
    (4, 'ایسوزو', 'Isuzu')
) AS b(code, description_fa, description_en) ON TRUE
WHERE vt.code = 4
ON CONFLICT (vehicle_type_code_id, code) DO UPDATE
SET description_fa = EXCLUDED.description_fa,
    description_en = EXCLUDED.description_en,
    is_active = TRUE;

-- Truck: 01 Nissan, 02 Ford (as requested).
INSERT INTO vehicle_brand_codes (vehicle_type_code_id, code, description_fa, description_en)
SELECT vt.id, b.code, b.description_fa, b.description_en
FROM vehicle_type_codes vt
JOIN (VALUES
    (1, 'نیسان', 'Nissan'),
    (2, 'فورد', 'Ford'),
    (3, 'مرسدس بنز', 'Mercedes-Benz'),
    (4, 'ولوو', 'Volvo')
) AS b(code, description_fa, description_en) ON TRUE
WHERE vt.code = 5
ON CONFLICT (vehicle_type_code_id, code) DO UPDATE
SET description_fa = EXCLUDED.description_fa,
    description_en = EXCLUDED.description_en,
    is_active = TRUE;

-- Keep code 1 and code 2 deliberately free-form.
ALTER TABLE products
    DROP CONSTRAINT IF EXISTS products_code_1_check,
    DROP CONSTRAINT IF EXISTS products_code_2_check,
    ALTER COLUMN code_1 TYPE TEXT,
    ALTER COLUMN code_2 TYPE TEXT;

-- Enforce the type/brand relationship at database level for all new code-detail rows.
CREATE OR REPLACE FUNCTION enforce_vehicle_brand_type_match()
RETURNS TRIGGER AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM vehicle_brand_codes vb
        WHERE vb.id = NEW.vehicle_brand_code_id
          AND vb.vehicle_type_code_id = NEW.vehicle_type_code_id
    ) THEN
        RAISE EXCEPTION 'Vehicle brand does not belong to the selected vehicle type';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_product_code_brand_type
BEFORE INSERT OR UPDATE OF vehicle_type_code_id, vehicle_brand_code_id
ON product_code_details
FOR EACH ROW EXECUTE FUNCTION enforce_vehicle_brand_type_match();

CREATE TRIGGER trg_serial_counter_brand_type
BEFORE INSERT OR UPDATE OF vehicle_type_code_id, vehicle_brand_code_id
ON product_serial_counters
FOR EACH ROW EXECUTE FUNCTION enforce_vehicle_brand_type_match();
