package com.carparts.platform.product;

import com.carparts.platform.auth.AuthPrincipal;
import com.carparts.platform.notification.NotificationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin/product-operations")
public class ProductOperationsController {
    private final ProductPriceService prices;
    private final NotificationService notifications;
    private final JdbcTemplate jdbc;
    public ProductOperationsController(ProductPriceService prices,NotificationService notifications,JdbcTemplate jdbc){this.prices=prices;this.notifications=notifications;this.jdbc=jdbc;}

    public record CustomerNotice(String title,String message,String channel){}

    @GetMapping("/{productId}/prices")
    public List<ProductPriceService.PriceRow> priceHistory(@PathVariable UUID productId){return prices.prices(productId);}

    @GetMapping("/{productId}/price-state")
    public ProductPriceService.PriceState priceState(@PathVariable UUID productId){return prices.stateForProduct(productId);}

    @PostMapping("/{productId}/prices")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER','ADMIN','FINANCE')")
    public ProductPriceService.PriceRow setPrice(@PathVariable UUID productId,@RequestBody ProductPriceService.PriceInput input,Authentication a){
        return prices.setPrice(productId,input,p(a));
    }

    @GetMapping("/{productId}/history")
    public List<Map<String,Object>> history(@PathVariable UUID productId){
        return jdbc.queryForList("""
                SELECT l.id,l.entity_type,l.action,l.source,l.reason,
                       l.before_data::text before_data,l.after_data::text after_data,l.created_at,
                       actor.display_name actor_name,actor.username actor_username
                FROM audit_log l LEFT JOIN app_users actor ON actor.id=l.actor_user_id
                WHERE (l.entity_type='products' AND l.entity_id=?::text)
                   OR (l.entity_type IN ('product_images','product_code_details','product_prices')
                       AND COALESCE(l.after_data->>'product_id',l.before_data->>'product_id')=?::text)
                ORDER BY l.created_at DESC,l.id DESC LIMIT 500
                """,productId.toString(),productId.toString());
    }

    @PostMapping("/{productId}/notify-customers")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER','ADMIN','PRODUCT_ADMIN')")
    @Transactional
    public Map<String,Object> notifyCustomers(@PathVariable UUID productId,@RequestBody CustomerNotice input,Authentication a){
        AuthPrincipal actor=p(a);
        String title=input.title()==null||input.title().isBlank()?"تغییر اطلاعات محصول":input.title().trim();
        String message=input.message()==null||input.message().isBlank()?"اطلاعات محصول مرتبط با درخواست شما به‌روزرسانی شد.":input.message().trim();
        jdbc.update("""
                INSERT INTO case_tags(case_id,tag_id,source,applied_by_user_id)
                SELECT w.id,t.id,'AUTOMATIC',? FROM workflow_cases w CROSS JOIN tags t
                WHERE w.product_id=? AND w.stage NOT IN ('CLOSED','CANCELLED') AND t.slug='product-changed'
                ON CONFLICT(case_id,tag_id) DO UPDATE SET source='AUTOMATIC',applied_by_user_id=EXCLUDED.applied_by_user_id
                """,actor.id(),productId);
        int count=notifications.notifyCustomersForProduct(productId,title,message,input.channel(),actor);
        return Map.of("created",count);
    }

    private AuthPrincipal p(Authentication a){return (AuthPrincipal)a.getPrincipal();}
}
