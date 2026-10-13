package com.carparts.platform.product;

import com.carparts.platform.auth.AuthPrincipal;
import com.carparts.platform.workflow.WorkflowAutomationService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/admin/product-cases")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER','ADMIN','PRODUCT_ADMIN')")
public class ProductCaseController {
    private final JdbcTemplate jdbc;
    private final WorkflowAutomationService automation;
    public ProductCaseController(JdbcTemplate jdbc,WorkflowAutomationService automation){this.jdbc=jdbc;this.automation=automation;}

    public record ProductLink(UUID productId){}

    @GetMapping
    public List<Map<String,Object>> queue(){return jdbc.queryForList("""
        SELECT c.id AS case_id,c.case_no,c.stage,c.product_id,r.request_no,r.source,r.contact_name,
               r.requested_part_number,r.request_text,r.vehicle_text,r.created_at
        FROM workflow_cases c JOIN customer_requests r ON r.id=c.request_id
        WHERE c.stage IN ('REQUESTED','PRODUCT_CREATED')
        ORDER BY CASE WHEN c.product_id IS NULL THEN 0 ELSE 1 END,r.created_at
        LIMIT 300
        """);}

    @PutMapping("/{caseId}/product")
    @Transactional
    public Map<String,Object> attach(@PathVariable UUID caseId,@RequestBody ProductLink in,Authentication a){
        if(in.productId()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"محصول الزامی است.");
        Integer valid=jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id=? AND deleted_at IS NULL",Integer.class,in.productId());
        if(valid==null||valid==0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"محصول معتبر نیست.");
        Map<String,Object> old;
        try{old=jdbc.queryForMap("SELECT stage,product_id FROM workflow_cases WHERE id=?",caseId);}catch(Exception e){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"پرونده پیدا نشد.");}
        jdbc.update("UPDATE workflow_cases SET product_id=?,stage=CASE WHEN stage='REQUESTED' THEN 'PRODUCT_CREATED' ELSE stage END,updated_at=NOW() WHERE id=?",in.productId(),caseId);
        AuthPrincipal p=(AuthPrincipal)a.getPrincipal();
        jdbc.update("INSERT INTO workflow_events(case_id,event_type,from_stage,to_stage,note,payload,created_by_user_id) VALUES (?,'PRODUCT_ATTACHED',?,CASE WHEN ?='REQUESTED' THEN 'PRODUCT_CREATED' ELSE ? END,'محصول کاتالوگ به درخواست متصل شد.',jsonb_build_object('productId',?::text),?)",caseId,old.get("stage"),old.get("stage"),old.get("stage"),in.productId().toString(),p.id());
        jdbc.update("INSERT INTO audit_log(actor_user_id,entity_type,entity_id,action,after_data,source,reason) VALUES (?,'workflow_case',?,'ATTACH_PRODUCT',jsonb_build_object('productId',?::text),'APPLICATION','اتصال محصول به درخواست')",p.id(),caseId.toString(),in.productId().toString());
        automation.afterProductAttached(caseId,in.productId(),p);
        return jdbc.queryForMap("SELECT id,case_no,product_id,stage,updated_at FROM workflow_cases WHERE id=?",caseId);
    }
}
