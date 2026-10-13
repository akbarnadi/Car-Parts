package com.carparts.platform.panel;

import com.carparts.platform.auth.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/panel")
public class PanelDataController {
    private final JdbcTemplate jdbc;
    public PanelDataController(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @GetMapping("/orders")
    public List<Map<String,Object>> orders(Authentication a){
        AuthPrincipal p=(AuthPrincipal)a.getPrincipal();
        if(customerOnly(p)) return jdbc.queryForList("""
            SELECT o.* FROM sales_orders o
            JOIN customers c ON c.id=o.customer_id
            WHERE c.user_id=? ORDER BY o.created_at DESC
            """,p.id());
        return jdbc.queryForList("SELECT * FROM sales_orders ORDER BY created_at DESC LIMIT 200");
    }

    @GetMapping("/orders/{id}")
    public Map<String,Object> order(@PathVariable UUID id,Authentication a){
        AuthPrincipal p=(AuthPrincipal)a.getPrincipal();
        List<Map<String,Object>> rows;
        if(customerOnly(p)){
            rows=jdbc.queryForList("""
                SELECT o.*,wc.case_no,wc.stage,wc.agreement_status
                FROM sales_orders o
                JOIN customers c ON c.id=o.customer_id
                LEFT JOIN workflow_cases wc ON wc.id=o.case_id
                WHERE o.id=? AND c.user_id=?
                """,id,p.id());
        }else{
            rows=jdbc.queryForList("""
                SELECT o.*,wc.case_no,wc.stage,wc.agreement_status
                FROM sales_orders o LEFT JOIN workflow_cases wc ON wc.id=o.case_id
                WHERE o.id=?
                """,id);
        }
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"سفارش پیدا نشد.");
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("order",rows.getFirst());
        result.put("items",jdbc.queryForList("SELECT id,product_id,part_number_snapshot,description_snapshot,quantity,unit_price FROM order_items WHERE order_id=? ORDER BY id",id));
        result.put("payments",jdbc.queryForList("SELECT id,amount,currency,method,reference,status,received_at,created_at FROM payments WHERE order_id=? ORDER BY created_at DESC",id));
        result.put("shipments",jdbc.queryForList("SELECT id,tracking_number,carrier,origin_country,destination_country,status,customs_status,eta,departed_at,arrived_at,created_at,updated_at FROM shipments WHERE order_id=? ORDER BY created_at DESC",id));
        Object caseId=rows.getFirst().get("case_id");
        if(caseId!=null){
            result.put("timeline",jdbc.queryForList("SELECT event_type,from_stage,to_stage,note,created_at FROM workflow_events WHERE case_id=? ORDER BY created_at DESC LIMIT 100",caseId));
        }else result.put("timeline",List.of());
        return result;
    }

    @GetMapping("/requests")
    public List<Map<String,Object>> requests(Authentication a){
        AuthPrincipal p=(AuthPrincipal)a.getPrincipal();
        String select="""
            SELECT r.id,r.request_no,r.source,r.requested_part_number,r.request_text,r.vehicle_text,
                   r.status AS request_status,r.created_at,r.updated_at,
                   wc.id AS case_id,wc.case_no,wc.stage AS workflow_stage,wc.agreement_status,
                   wc.updated_at AS workflow_updated_at,
                   pr.id AS product_id,pr.description_fa AS product_name_fa,pr.description_en AS product_name_en,
                   (SELECT q.total_price FROM customer_quotes q WHERE q.case_id=wc.id AND q.status<>'DRAFT' ORDER BY q.created_at DESC LIMIT 1) AS latest_quote_total,
                   (SELECT q.currency FROM customer_quotes q WHERE q.case_id=wc.id AND q.status<>'DRAFT' ORDER BY q.created_at DESC LIMIT 1) AS latest_quote_currency,
                   (SELECT q.status FROM customer_quotes q WHERE q.case_id=wc.id AND q.status<>'DRAFT' ORDER BY q.created_at DESC LIMIT 1) AS latest_quote_status,
                   (SELECT q.valid_until FROM customer_quotes q WHERE q.case_id=wc.id AND q.status<>'DRAFT' ORDER BY q.created_at DESC LIMIT 1) AS latest_quote_valid_until
            FROM customer_requests r
            LEFT JOIN workflow_cases wc ON wc.request_id=r.id
            LEFT JOIN products pr ON pr.id=wc.product_id
            """;
        if(customerOnly(p)) return jdbc.queryForList(select+" JOIN customers c ON c.id=r.customer_id WHERE c.user_id=? ORDER BY r.created_at DESC",p.id());
        return jdbc.queryForList(select+" ORDER BY r.created_at DESC LIMIT 200");
    }

    @GetMapping("/requests/{id}")
    public Map<String,Object> request(@PathVariable UUID id,Authentication a){
        AuthPrincipal p=(AuthPrincipal)a.getPrincipal();
        String base="""
            SELECT r.id,r.request_no,r.source,r.contact_name,r.contact_phone,r.contact_email,
                   r.requested_part_number,r.request_text,r.vehicle_text,r.status AS request_status,
                   r.created_at,r.updated_at,wc.id AS case_id,wc.case_no,wc.stage AS workflow_stage,
                   wc.agreement_status,wc.updated_at AS workflow_updated_at,
                   pr.id AS product_id,pr.description_fa AS product_name_fa,pr.description_en AS product_name_en,
                   pr.real_part_number,pr.product_code
            FROM customer_requests r
            LEFT JOIN workflow_cases wc ON wc.request_id=r.id
            LEFT JOIN products pr ON pr.id=wc.product_id
            """;
        List<Map<String,Object>> rows;
        if(customerOnly(p)) rows=jdbc.queryForList(base+" JOIN customers c ON c.id=r.customer_id WHERE r.id=? AND c.user_id=?",id,p.id());
        else rows=jdbc.queryForList(base+" WHERE r.id=?",id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"درخواست پیدا نشد.");

        Map<String,Object> request=rows.getFirst();
        Object caseId=request.get("case_id");
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("request",request);
        if(caseId==null){
            out.put("quotes",List.of());out.put("orders",List.of());out.put("timeline",List.of());return out;
        }
        UUID cid=(UUID)caseId;
        String quoteSql=customerOnly(p)
                ?"SELECT id,quote_no,currency,total_price,valid_until,status,created_at FROM customer_quotes WHERE case_id=? AND status<>'DRAFT' ORDER BY created_at DESC"
                :"SELECT id,quote_no,currency,total_price,valid_until,status,created_at FROM customer_quotes WHERE case_id=? ORDER BY created_at DESC";
        out.put("quotes",jdbc.queryForList(quoteSql,cid));
        out.put("orders",jdbc.queryForList("SELECT id,order_no,status,currency,total_amount,paid_amount,created_at FROM sales_orders WHERE case_id=? ORDER BY created_at DESC",cid));
        out.put("timeline",jdbc.queryForList("SELECT event_type,from_stage,to_stage,created_at FROM workflow_events WHERE case_id=? ORDER BY created_at DESC LIMIT 100",cid));
        return out;
    }

    private boolean customerOnly(AuthPrincipal p){return p.roles().contains("CUSTOMER")&&p.roles().size()==1;}
}
