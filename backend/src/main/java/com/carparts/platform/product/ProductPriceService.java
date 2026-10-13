package com.carparts.platform.product;

import com.carparts.platform.auth.AuthPrincipal;
import com.carparts.platform.notification.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class ProductPriceService {
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    public ProductPriceService(JdbcTemplate jdbc,NotificationService notifications){this.jdbc=jdbc;this.notifications=notifications;}

    public record PriceInput(BigDecimal amount,String currency,LocalDate validFrom,LocalDate validUntil,String note){}
    public record PriceRow(UUID id,UUID productId,BigDecimal amount,String currency,LocalDate validFrom,LocalDate validUntil,
                           String status,String note,String createdBy,Instant createdAt){}
    public record PriceState(String state,PriceRow current,PriceRow latest){}

    public List<PriceRow> prices(UUID productId){
        ensureProductExists(productId);
        return jdbc.query("""
                SELECT p.id,p.product_id,p.amount,p.currency,p.valid_from,p.valid_until,p.status,p.note,p.created_at,u.display_name created_by
                FROM product_prices p LEFT JOIN app_users u ON u.id=p.created_by_user_id
                WHERE p.product_id=? ORDER BY p.created_at DESC
                """,this::map,productId);
    }

    public PriceState stateForProduct(UUID productId){
        if(productId==null)return new PriceState("MISSING",null,null);
        List<PriceRow> all=prices(productId);
        PriceRow latest=all.isEmpty()?null:all.getFirst();
        PriceRow valid=all.stream().filter(p->"ACTIVE".equals(p.status())&&!LocalDate.now().isBefore(p.validFrom())&&!LocalDate.now().isAfter(p.validUntil())).findFirst().orElse(null);
        if(valid!=null)return new PriceState("VALID",valid,latest);
        if(latest==null)return new PriceState("MISSING",null,null);
        return new PriceState("EXPIRED",null,latest);
    }

    public PriceState stateForCase(UUID caseId){
        UUID productId;
        try{productId=jdbc.queryForObject("SELECT product_id FROM workflow_cases WHERE id=?",UUID.class,caseId);}catch(Exception e){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"پرونده پیدا نشد.");}
        return stateForProduct(productId);
    }

    public List<PriceRow> publicCurrentPrices(){
        return jdbc.query("""
                SELECT p.id,p.product_id,p.amount,p.currency,p.valid_from,p.valid_until,p.status,p.note,p.created_at,u.display_name created_by
                FROM product_prices p LEFT JOIN app_users u ON u.id=p.created_by_user_id
                JOIN products x ON x.id=p.product_id AND x.deleted_at IS NULL
                WHERE p.status='ACTIVE' AND CURRENT_DATE BETWEEN p.valid_from AND p.valid_until
                ORDER BY p.product_id,p.created_at DESC
                """,this::map);
    }

    @Transactional
    public PriceRow setPrice(UUID productId,PriceInput input,AuthPrincipal actor){
        ensureProductActive(productId);
        if(input.amount()==null||input.amount().signum()<0)throw bad("قیمت معتبر وارد کنید.");
        String currency=input.currency()==null||input.currency().isBlank()?"IRR":input.currency().trim().toUpperCase(Locale.ROOT);
        if(currency.length()!=3)throw bad("کد ارز باید سه حرف باشد.");
        LocalDate from=input.validFrom()==null?LocalDate.now():input.validFrom();
        LocalDate until=input.validUntil()==null?from.plusDays(60):input.validUntil();
        if(until.isBefore(from))throw bad("تاریخ اعتبار قیمت نامعتبر است.");
        jdbc.update("UPDATE product_prices SET status='REPLACED' WHERE product_id=? AND status='ACTIVE'",productId);
        UUID id=jdbc.queryForObject("""
                INSERT INTO product_prices(product_id,amount,currency,valid_from,valid_until,status,note,created_by_user_id)
                VALUES (?,?,?,?,?,'ACTIVE',?,?) RETURNING id
                """,UUID.class,productId,input.amount(),currency,Date.valueOf(from),Date.valueOf(until),clean(input.note()),actor.id());
        jdbc.update("INSERT INTO audit_log(actor_user_id,entity_type,entity_id,action,source,reason) VALUES (?,'product_prices',?,'PRICE_SET','APPLICATION',?)",
                actor.id(),id.toString(),"قیمت محصول تا "+until+" معتبر است");
        List<UUID> cases=jdbc.query("SELECT id FROM workflow_cases WHERE product_id=? AND stage NOT IN ('CLOSED','CANCELLED')",(rs,n)->rs.getObject(1,UUID.class),productId);
        for(UUID caseId:cases) syncCasePriceTag(caseId);
        notifications.notifyRoles(Set.of("SUPER_ADMIN","MANAGER","ADMIN","FINANCE"),"PRODUCT_PRICE_UPDATED",
                "قیمت محصول به‌روز شد","برای یک محصول قیمت معتبر جدید ثبت شد.","/admin/products/"+productId+"/operations",null,productId,actor.id());
        return prices(productId).stream().filter(p->p.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    public void syncCasePriceTag(UUID caseId){
        Map<String,Object> row;
        try{row=jdbc.queryForMap("SELECT product_id FROM workflow_cases WHERE id=?",caseId);}catch(Exception e){return;}
        UUID productId=(UUID)row.get("product_id");
        jdbc.update("DELETE FROM case_tags ct USING tags t WHERE ct.tag_id=t.id AND ct.case_id=? AND ct.source='AUTOMATIC' AND t.slug IN ('price-valid','price-needed')",caseId);
        if(productId==null)return;
        String slug="VALID".equals(stateForProduct(productId).state())?"price-valid":"price-needed";
        jdbc.update("INSERT INTO case_tags(case_id,tag_id,source) SELECT ?,id,'AUTOMATIC' FROM tags WHERE slug=? ON CONFLICT(case_id,tag_id) DO UPDATE SET source='AUTOMATIC'",caseId,slug);
    }

    private PriceRow map(java.sql.ResultSet rs,int n)throws java.sql.SQLException{
        return new PriceRow(rs.getObject("id",UUID.class),rs.getObject("product_id",UUID.class),rs.getBigDecimal("amount"),rs.getString("currency"),
                rs.getDate("valid_from").toLocalDate(),rs.getDate("valid_until").toLocalDate(),rs.getString("status"),rs.getString("note"),
                rs.getString("created_by"),rs.getTimestamp("created_at").toInstant());
    }

    private void ensureProductExists(UUID id){
        Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id=?",Integer.class,id);
        if(n==null||n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"محصول پیدا نشد.");
    }
    private void ensureProductActive(UUID id){
        Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id=? AND deleted_at IS NULL",Integer.class,id);
        if(n==null||n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"محصول فعال پیدا نشد.");
    }
    private String clean(String v){return v==null||v.isBlank()?null:v.trim();}
    private ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
}
