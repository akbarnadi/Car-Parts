package com.carparts.platform.panel;

import com.carparts.platform.auth.AuthPrincipal;
import com.carparts.platform.notification.NotificationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/panel")
public class PanelController {
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    public PanelController(JdbcTemplate jdbc,NotificationService notifications){this.jdbc=jdbc;this.notifications=notifications;}

    @GetMapping("/summary")
    public Map<String,Object> summary(Authentication a){
        AuthPrincipal p=(AuthPrincipal)a.getPrincipal();
        Map<String,Object> out=new LinkedHashMap<>();
        Map<String,Object> user=new LinkedHashMap<>();
        user.put("id",p.id());user.put("username",p.username());user.put("email",p.email());user.put("phone",p.phone());
        user.put("displayName",p.displayName());user.put("roles",p.roles());user.put("managerLevel",p.managerLevel());user.put("root",p.root());
        out.put("user",user);
        Map<String,Long> m=new LinkedHashMap<>();
        m.put("اعلان خوانده‌نشده",notifications.unreadCount(p.id()));
        if(has(p,"SUPER_ADMIN","MANAGER")){
            m.put("پرونده‌های باز",count("SELECT COUNT(*) FROM workflow_cases WHERE stage NOT IN ('CLOSED','CANCELLED','DELIVERED')"));
            m.put("درخواست جدید",count("SELECT COUNT(*) FROM customer_requests WHERE status='NEW'"));
            m.put("منتظر مشتری",count("SELECT COUNT(*) FROM workflow_cases WHERE stage='CUSTOMER_QUOTED'"));
            m.put("در حمل یا گمرک",count("SELECT COUNT(*) FROM workflow_cases WHERE stage IN ('IN_TRANSIT','CUSTOMS')"));
        }
        if(has(p,"ADMIN")){
            m.put("نیازمند شناسایی محصول",count("SELECT COUNT(*) FROM workflow_cases WHERE product_id IS NULL AND stage='REQUESTED'"));
            m.put("نیازمند استعلام قیمت",count("SELECT COUNT(DISTINCT ct.case_id) FROM case_tags ct JOIN tags t ON t.id=ct.tag_id WHERE t.slug='price-needed'"));
        }
        if(has(p,"PRODUCT_ADMIN")){
            m.put("محصول فعال",count("SELECT COUNT(*) FROM products WHERE deleted_at IS NULL AND status='ACTIVE'"));
            m.put("پیش‌نویس محصول",count("SELECT COUNT(*) FROM products WHERE deleted_at IS NULL AND status='DRAFT'"));
        }
        if(has(p,"PROCUREMENT")){
            m.put("نیازمند استعلام",count("SELECT COUNT(*) FROM workflow_cases WHERE stage IN ('PRODUCT_CREATED','SOURCING')"));
            m.put("قیمت تامین‌کننده",count("SELECT COUNT(*) FROM workflow_cases WHERE stage='SUPPLIER_QUOTED'"));
        }
        if(has(p,"FINANCE")){
            m.put("قیمت مشتری",count("SELECT COUNT(*) FROM customer_quotes WHERE status IN ('DRAFT','SENT')"));
            m.put("پرداخت تایید نشده",count("SELECT COUNT(*) FROM payments WHERE status='PENDING'"));
        }
        if(has(p,"SHIPPING")){
            m.put("در حال حمل",count("SELECT COUNT(*) FROM workflow_cases WHERE stage='IN_TRANSIT'"));
            m.put("گمرک",count("SELECT COUNT(*) FROM workflow_cases WHERE stage='CUSTOMS'"));
            m.put("آماده تحویل",count("SELECT COUNT(*) FROM workflow_cases WHERE stage='READY_FOR_DELIVERY'"));
        }
        if(has(p,"SUPPORT")){
            m.put("درخواست جدید",count("SELECT COUNT(*) FROM customer_requests WHERE status='NEW'"));
            m.put("پیام جدید",count("SELECT COUNT(*) FROM contact_messages WHERE status='NEW'"));
        }
        if(has(p,"CUSTOMER")){
            m.put("درخواست‌های من",count("SELECT COUNT(*) FROM customer_requests r JOIN customers c ON c.id=r.customer_id WHERE c.user_id=?",p.id()));
            m.put("سفارش‌های من",count("SELECT COUNT(*) FROM sales_orders o JOIN customers c ON c.id=o.customer_id WHERE c.user_id=?",p.id()));
        }
        out.put("metrics",m);return out;
    }

    private boolean has(AuthPrincipal p,String...roles){for(String r:roles)if(p.roles().contains(r))return true;return false;}
    private long count(String sql,Object...args){Long v=jdbc.queryForObject(sql,Long.class,args);return v==null?0:v;}
}
