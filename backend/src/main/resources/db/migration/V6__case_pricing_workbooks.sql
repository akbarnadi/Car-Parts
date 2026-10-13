-- Persist the manager pricing spreadsheet separately from immutable customer quote snapshots.
CREATE TABLE case_pricing_workbooks (
    case_id UUID PRIMARY KEY REFERENCES workflow_cases(id) ON DELETE CASCADE,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    actual_supplier NUMERIC(18,4) NOT NULL DEFAULT 0 CHECK(actual_supplier>=0),
    actual_logistics NUMERIC(18,4) NOT NULL DEFAULT 0 CHECK(actual_logistics>=0),
    actual_customs NUMERIC(18,4) NOT NULL DEFAULT 0 CHECK(actual_customs>=0),
    actual_bank NUMERIC(18,4) NOT NULL DEFAULT 0 CHECK(actual_bank>=0),
    actual_local NUMERIC(18,4) NOT NULL DEFAULT 0 CHECK(actual_local>=0),
    actual_other NUMERIC(18,4) NOT NULL DEFAULT 0 CHECK(actual_other>=0),
    charged_supplier NUMERIC(18,4) NOT NULL DEFAULT 0 CHECK(charged_supplier>=0),
    charged_logistics NUMERIC(18,4) NOT NULL DEFAULT 0 CHECK(charged_logistics>=0),
    charged_customs NUMERIC(18,4) NOT NULL DEFAULT 0 CHECK(charged_customs>=0),
    charged_bank NUMERIC(18,4) NOT NULL DEFAULT 0 CHECK(charged_bank>=0),
    charged_local NUMERIC(18,4) NOT NULL DEFAULT 0 CHECK(charged_local>=0),
    charged_other NUMERIC(18,4) NOT NULL DEFAULT 0 CHECK(charged_other>=0),
    markup_percent NUMERIC(7,4) NOT NULL DEFAULT 0 CHECK(markup_percent BETWEEN 0 AND 100),
    tax_amount NUMERIC(18,4) NOT NULL DEFAULT 0 CHECK(tax_amount>=0),
    discount_amount NUMERIC(18,4) NOT NULL DEFAULT 0 CHECK(discount_amount>=0),
    exchange_rate NUMERIC(22,8),
    pricing_formula TEXT NOT NULL DEFAULT 'chargedSupplier + chargedLogistics + chargedCustoms + chargedBank + chargedLocal + chargedOther + ((chargedSupplier + chargedLogistics + chargedCustoms + chargedBank + chargedLocal + chargedOther) * markupPercent / 100) + taxAmount - discountAmount',
    calculated_total NUMERIC(18,4) NOT NULL DEFAULT 0 CHECK(calculated_total>=0),
    gross_profit NUMERIC(18,4) NOT NULL DEFAULT 0,
    updated_by_user_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

DROP TRIGGER IF EXISTS audit_row_changes ON case_pricing_workbooks;
CREATE TRIGGER audit_row_changes AFTER INSERT OR UPDATE OR DELETE ON case_pricing_workbooks
FOR EACH ROW EXECUTE FUNCTION write_generic_audit();
