package com.carparts.platform.lookup;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

@Service
public class LookupAdminService {
    private final JdbcTemplate jdbc;
    public LookupAdminService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public record Row(UUID id,int code,String descriptionFa,String descriptionEn,boolean active,UUID vehicleTypeCodeId,String designCountry,String manufacturingCountry,BigDecimal weightKg,BigDecimal lengthCm,BigDecimal widthCm,BigDecimal heightCm){}
    public record Input(Integer code,String descriptionFa,String descriptionEn,Boolean active,UUID vehicleTypeCodeId,String designCountry,String manufacturingCountry,BigDecimal weightKg,BigDecimal lengthCm,BigDecimal widthCm,BigDecimal heightCm){}

    public List<Row> list(String type){
        return switch(type){
            case "vehicle-types"->simple("vehicle_type_codes");
            case "vehicle-brands"->jdbc.query("""
                SELECT id,code,description_fa,description_en,is_active,vehicle_type_code_id,
                       NULL::text design_country,NULL::text manufacturing_country,
                       NULL::numeric weight_kg,NULL::numeric length_cm,NULL::numeric width_cm,NULL::numeric height_cm
                FROM vehicle_brand_codes ORDER BY vehicle_type_code_id NULLS LAST,code
                """,this::map);
            case "part-brands"->jdbc.query("""
                SELECT id,code,description_fa,description_en,is_active,NULL::uuid vehicle_type_code_id,
                       design_country,manufacturing_country,
                       NULL::numeric weight_kg,NULL::numeric length_cm,NULL::numeric width_cm,NULL::numeric height_cm
                FROM part_brand_codes ORDER BY code
                """,this::map);
            case "packaging"->simple("packaging_codes");
            case "qualities"->simple("quality_codes");
            case "product-groups"->simple("product_group_codes");
            case "units"->simple("unit_codes");
            case "physical-codes"->jdbc.query("""
                SELECT id,code,description_fa,description_en,is_active,NULL::uuid vehicle_type_code_id,
                       NULL::text design_country,NULL::text manufacturing_country,weight_kg,length_cm,width_cm,height_cm
                FROM physical_codes ORDER BY code
                """,this::map);
            default->throw notFound();
        };
    }

    @Transactional
    public Row create(String type,Input in,UUID actor){
        validateNames(in);
        if("vehicle-brands".equals(type))requireActiveVehicleType(in.vehicleTypeCodeId());
        int code=allocateNextCode(type,in.vehicleTypeCodeId());
        UUID id=switch(type){
            case "vehicle-types"->insertSimple("vehicle_type_codes",code,in);
            case "vehicle-brands"->jdbc.queryForObject("INSERT INTO vehicle_brand_codes(code,description_fa,description_en,vehicle_type_code_id,is_active) VALUES (?,?,?,?,COALESCE(?,TRUE)) RETURNING id",UUID.class,code,in.descriptionFa().trim(),in.descriptionEn().trim(),in.vehicleTypeCodeId(),in.active());
            case "part-brands"->jdbc.queryForObject("INSERT INTO part_brand_codes(code,description_fa,description_en,design_country,manufacturing_country,is_active) VALUES (?,?,?,?,?,COALESCE(?,TRUE)) RETURNING id",UUID.class,code,in.descriptionFa().trim(),in.descriptionEn().trim(),clean(in.designCountry()),clean(in.manufacturingCountry()),in.active());
            case "packaging"->insertSimple("packaging_codes",code,in);
            case "qualities"->insertSimple("quality_codes",code,in);
            case "product-groups"->insertSimple("product_group_codes",code,in);
            case "units"->insertSimple("unit_codes",code,in);
            case "physical-codes"->jdbc.queryForObject("INSERT INTO physical_codes(code,description_fa,description_en,weight_kg,length_cm,width_cm,height_cm,is_active) VALUES (?,?,?,?,?,?,?,COALESCE(?,TRUE)) RETURNING id",UUID.class,code,in.descriptionFa().trim(),in.descriptionEn().trim(),in.weightKg(),in.lengthCm(),in.widthCm(),in.heightCm(),in.active());
            default->throw notFound();
        };
        audit(actor,type,id,"CREATE");
        return find(type,id);
    }

    @Transactional
    public Row update(String type,UUID id,Input in,UUID actor){
        validateUpdate(type,in);
        Row cur=find(type,id);
        if(cur.code()!=in.code()&&isUsed(type,id))throw bad("کد استفاده‌شده در محصول قابل تغییر نیست؛ رکورد جدید بسازید.");
        int n=switch(type){
            case "vehicle-types"->updateSimple("vehicle_type_codes",id,in);
            case "vehicle-brands"->{
                requireActiveVehicleType(in.vehicleTypeCodeId());
                if(isUsed(type,id)&&!Objects.equals(cur.vehicleTypeCodeId(),in.vehicleTypeCodeId()))throw bad("نوع خودروی برند استفاده‌شده قابل تغییر نیست.");
                yield jdbc.update("UPDATE vehicle_brand_codes SET code=?,description_fa=?,description_en=?,vehicle_type_code_id=?,is_active=COALESCE(?,is_active),updated_at=NOW() WHERE id=?",in.code(),in.descriptionFa().trim(),in.descriptionEn().trim(),in.vehicleTypeCodeId(),in.active(),id);
            }
            case "part-brands"->jdbc.update("UPDATE part_brand_codes SET code=?,description_fa=?,description_en=?,design_country=?,manufacturing_country=?,is_active=COALESCE(?,is_active),updated_at=NOW() WHERE id=?",in.code(),in.descriptionFa().trim(),in.descriptionEn().trim(),clean(in.designCountry()),clean(in.manufacturingCountry()),in.active(),id);
            case "packaging"->updateSimple("packaging_codes",id,in);
            case "qualities"->updateSimple("quality_codes",id,in);
            case "product-groups"->updateSimple("product_group_codes",id,in);
            case "units"->updateSimple("unit_codes",id,in);
            case "physical-codes"->jdbc.update("UPDATE physical_codes SET code=?,description_fa=?,description_en=?,weight_kg=?,length_cm=?,width_cm=?,height_cm=?,is_active=COALESCE(?,is_active),updated_at=NOW() WHERE id=?",in.code(),in.descriptionFa().trim(),in.descriptionEn().trim(),in.weightKg(),in.lengthCm(),in.widthCm(),in.heightCm(),in.active(),id);
            default->throw notFound();
        };
        if(n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"رکورد پیدا نشد.");
        audit(actor,type,id,"UPDATE");
        return find(type,id);
    }

    @Transactional
    public void deactivate(String type,UUID id,UUID actor){
        if(jdbc.update("UPDATE "+table(type)+" SET is_active=FALSE,updated_at=NOW() WHERE id=?",id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"رکورد پیدا نشد.");
        audit(actor,type,id,"DEACTIVATE");
    }

    private int allocateNextCode(String type,UUID vehicleTypeId){
        String table=table(type);
        int max=maxCode(type);
        jdbc.execute("LOCK TABLE "+table+" IN SHARE ROW EXCLUSIVE MODE");
        Integer next;
        if("vehicle-brands".equals(type)){
            if(vehicleTypeId==null)throw bad("نوع خودرو برای برند خودرو الزامی است.");
            next=jdbc.queryForObject("""
                SELECT MIN(g.code)
                FROM generate_series(1,?) AS g(code)
                WHERE NOT EXISTS (
                    SELECT 1 FROM vehicle_brand_codes v
                    WHERE v.vehicle_type_code_id=? AND v.code=g.code
                )
                """,Integer.class,max,vehicleTypeId);
        }else{
            next=jdbc.queryForObject(("""
                SELECT MIN(g.code)
                FROM generate_series(1,?) AS g(code)
                WHERE NOT EXISTS (SELECT 1 FROM %s t WHERE t.code=g.code)
                """).formatted(table),Integer.class,max);
        }
        if(next==null)throw bad("ظرفیت کد این جدول واقعاً تکمیل شده است؛ همه کدهای مجاز استفاده شده‌اند.");
        return next;
    }

    private int maxCode(String type){return switch(type){case "qualities"->9;case "product-groups","physical-codes"->9999;case "vehicle-types","vehicle-brands","part-brands","packaging","units"->99;default->throw notFound();};}
    private void requireActiveVehicleType(UUID id){if(id==null)throw bad("نوع خودرو برای برند خودرو الزامی است.");Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM vehicle_type_codes WHERE id=? AND is_active=TRUE",Integer.class,id);if(count==null||count==0)throw bad("نوع خودرو معتبر یا فعال نیست.");}
    private List<Row> simple(String table){return jdbc.query(("""
        SELECT id,code,description_fa,description_en,is_active,NULL::uuid vehicle_type_code_id,
               NULL::text design_country,NULL::text manufacturing_country,
               NULL::numeric weight_kg,NULL::numeric length_cm,NULL::numeric width_cm,NULL::numeric height_cm
        FROM %s ORDER BY code
        """).formatted(table),this::map);}
    private Row map(java.sql.ResultSet rs,int n)throws java.sql.SQLException{return new Row(rs.getObject("id",UUID.class),rs.getInt("code"),rs.getString("description_fa"),rs.getString("description_en"),rs.getBoolean("is_active"),rs.getObject("vehicle_type_code_id",UUID.class),rs.getString("design_country"),rs.getString("manufacturing_country"),rs.getBigDecimal("weight_kg"),rs.getBigDecimal("length_cm"),rs.getBigDecimal("width_cm"),rs.getBigDecimal("height_cm"));}
    private UUID insertSimple(String table,int code,Input in){return jdbc.queryForObject("INSERT INTO "+table+"(code,description_fa,description_en,is_active) VALUES (?,?,?,COALESCE(?,TRUE)) RETURNING id",UUID.class,code,in.descriptionFa().trim(),in.descriptionEn().trim(),in.active());}
    private int updateSimple(String table,UUID id,Input in){return jdbc.update("UPDATE "+table+" SET code=?,description_fa=?,description_en=?,is_active=COALESCE(?,is_active),updated_at=NOW() WHERE id=?",in.code(),in.descriptionFa().trim(),in.descriptionEn().trim(),in.active(),id);}
    private Row find(String type,UUID id){return list(type).stream().filter(r->r.id().equals(id)).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"رکورد پیدا نشد."));}
    private boolean isUsed(String type,UUID id){String col=switch(type){case "vehicle-types"->"vehicle_type_code_id";case "vehicle-brands"->"vehicle_brand_code_id";case "part-brands"->"part_brand_code_id";case "packaging"->"packaging_code_id";case "qualities"->"quality_code_id";default->null;};if(col==null)return false;Integer c=jdbc.queryForObject("SELECT COUNT(*) FROM product_code_details WHERE "+col+"=?",Integer.class,id);return c!=null&&c>0;}
    private String table(String t){return switch(t){case "vehicle-types"->"vehicle_type_codes";case "vehicle-brands"->"vehicle_brand_codes";case "part-brands"->"part_brand_codes";case "packaging"->"packaging_codes";case "qualities"->"quality_codes";case "product-groups"->"product_group_codes";case "units"->"unit_codes";case "physical-codes"->"physical_codes";default->throw notFound();};}
    private void validateNames(Input in){if(in==null||in.descriptionFa()==null||in.descriptionFa().isBlank()||in.descriptionEn()==null||in.descriptionEn().isBlank())throw bad("نام فارسی و نام انگلیسی الزامی است.");}
    private void validateUpdate(String type,Input in){validateNames(in);if(in.code()==null||in.code()<1||in.code()>maxCode(type))throw bad("کد معتبر نیست.");}
    private String clean(String value){return value==null||value.isBlank()?null:value.trim();}
    private void audit(UUID actor,String type,UUID id,String action){jdbc.update("INSERT INTO audit_log(actor_user_id,entity_type,entity_id,action) VALUES (?,?,?,?)",actor,"lookup:"+type,id.toString(),action);}
    private ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
    private ResponseStatusException notFound(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"جدول کد پیدا نشد.");}
}
