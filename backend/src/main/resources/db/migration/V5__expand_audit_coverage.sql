-- Expand audit coverage and ensure password hashes are never retained in audit JSON.
UPDATE audit_log
SET before_data = CASE WHEN before_data IS NULL THEN NULL ELSE before_data - 'password_hash' END,
    after_data  = CASE WHEN after_data  IS NULL THEN NULL ELSE after_data  - 'password_hash' END
WHERE entity_type='app_users';

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

    IF TG_TABLE_NAME='app_users' THEN
        IF b IS NOT NULL THEN b=b-'password_hash'; END IF;
        IF a IS NOT NULL THEN a=a-'password_hash'; END IF;
    END IF;

    eid=COALESCE(a->>'id',b->>'id',
        concat_ws(':',COALESCE(a->>'user_id',b->>'user_id'),COALESCE(a->>'role_id',b->>'role_id')),
        concat_ws(':',COALESCE(a->>'case_id',b->>'case_id'),COALESCE(a->>'tag_id',b->>'tag_id')),
        COALESCE(a->>'prefix',b->>'prefix'),
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
        'product_serial_counters','suppliers','contact_messages','news_posts','notifications'
    ] LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS audit_row_changes ON %I',t);
        EXECUTE format('CREATE TRIGGER audit_row_changes AFTER INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION write_generic_audit()',t);
    END LOOP;
END $$;
