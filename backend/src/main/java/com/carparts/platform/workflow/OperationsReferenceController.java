package com.carparts.platform.workflow;

import com.carparts.platform.auth.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/manager")
public class OperationsReferenceController {
    private final JdbcTemplate jdbc; public OperationsReferenceController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record SupplierInput(String name,String country,String contactName,String email,String phone,String currencies,Integer rating,String notes,Boolean active){}
    public record TagInput(String slug,String labelFa,String colorKey){}
    public record ContactStatus(String status){}
    @GetMapping("/suppliers") @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER','PROCUREMENT')") public List<Map<String,Object>> suppliers(){return jdbc.queryForList("SELECT * FROM suppliers ORDER BY is_active DESC,name");}
    @PostMapping("/suppliers") @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER','PROCUREMENT')") public Map<String,Object> createSupplier(@RequestBody SupplierInput in,Authentication a){validateSupplier(in);UUID id=jdbc.queryForObject("INSERT INTO suppliers(name,country,contact_name,email,phone,currencies,rating,notes,is_active) VALUES (?,?,?,?,?,?,?,?,COALESCE(?,TRUE)) RETURNING id",UUID.class,in.name().trim(),blank(in.country()),blank(in.contactName()),blank(in.email()),blank(in.phone()),blank(in.currencies()),in.rating(),in.notes(),in.active());audit(a,"supplier",id,"CREATE");return jdbc.queryForMap("SELECT * FROM suppliers WHERE id=?",id);}
    @PutMapping("/suppliers/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER','PROCUREMENT')") public Map<String,Object> updateSupplier(@PathVariable UUID id,@RequestBody SupplierInput in,Authentication a){validateSupplier(in);if(jdbc.update("UPDATE suppliers SET name=?,country=?,contact_name=?,email=?,phone=?,currencies=?,rating=?,notes=?,is_active=COALESCE(?,is_active),updated_at=NOW() WHERE id=?",in.name().trim(),blank(in.country()),blank(in.contactName()),blank(in.email()),blank(in.phone()),blank(in.currencies()),in.rating(),in.notes(),in.active(),id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"تامین‌کننده پیدا نشد.");audit(a,"supplier",id,"UPDATE");return jdbc.queryForMap("SELECT * FROM suppliers WHERE id=?",id);}
    @GetMapping("/contacts") @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER','SUPPORT')") public List<Map<String,Object>> contacts(){return jdbc.queryForList("SELECT * FROM contact_messages ORDER BY CASE status WHEN 'NEW' THEN 1 WHEN 'IN_PROGRESS' THEN 2 ELSE 3 END,created_at DESC LIMIT 300");}
    @PutMapping("/contacts/{id}/status") @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER','SUPPORT')") public void contactStatus(@PathVariable UUID id,@RequestBody ContactStatus in){if(in.status()==null||!Set.of("NEW","IN_PROGRESS","ANSWERED","CLOSED").contains(in.status()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"وضعیت نامعتبر است.");if(jdbc.update("UPDATE contact_messages SET status=? WHERE id=?",in.status(),id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
    @PostMapping("/tags") @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER')") public Map<String,Object> createTag(@RequestBody TagInput in,Authentication a){if(in.slug()==null||!in.slug().matches("[a-z0-9-]{2,80}")||in.labelFa()==null||in.labelFa().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"شناسه و نام برچسب معتبر نیست.");UUID id=jdbc.queryForObject("INSERT INTO tags(slug,label_fa,color_key,scope,is_system) VALUES (?, ?, COALESCE(NULLIF(?,''),'slate'),'CASE',FALSE) RETURNING id",UUID.class,in.slug(),in.labelFa().trim(),in.colorKey());audit(a,"tag",id,"CREATE");return jdbc.queryForMap("SELECT * FROM tags WHERE id=?",id);}
    private void validateSupplier(SupplierInput in){if(in==null||in.name()==null||in.name().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"نام تامین‌کننده الزامی است.");if(in.rating()!=null&&(in.rating()<1||in.rating()>5))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"امتیاز باید بین ۱ تا ۵ باشد.");}
    private void audit(Authentication a,String entity,UUID id,String action){AuthPrincipal p=(AuthPrincipal)a.getPrincipal();jdbc.update("INSERT INTO audit_log(actor_user_id,entity_type,entity_id,action) VALUES (?,?,?,?)",p.id(),entity,id.toString(),action);}
    private String blank(String v){return v==null||v.isBlank()?null:v.trim();}
}
