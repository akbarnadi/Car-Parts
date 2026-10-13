package com.carparts.platform.workflow;

import com.carparts.platform.auth.AuthPrincipal;
import com.carparts.platform.notification.NotificationService;
import com.carparts.platform.product.ProductPriceService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class WorkflowAutomationService {
    private final ProductPriceService prices;
    private final NotificationService notifications;
    private final JdbcTemplate jdbc;

    public WorkflowAutomationService(ProductPriceService prices,NotificationService notifications,JdbcTemplate jdbc){
        this.prices=prices;this.notifications=notifications;this.jdbc=jdbc;
    }

    @Transactional
    public void afterRequest(WorkflowService.CaseRow row,AuthPrincipal actor){
        prices.syncCasePriceTag(row.id());
        UUID actorId=actor==null?null:actor.id();
        if(row.productId()==null){
            notifications.notifyRoles(Set.of("SUPER_ADMIN","MANAGER","ADMIN","PRODUCT_ADMIN"),"NEW_REQUEST_PRODUCT_NEEDED",
                    "درخواست جدید؛ محصول باید شناسایی شود","برای درخواست #"+row.requestNo()+" هنوز محصول کاتالوگ تعیین نشده است.",
                    "/admin/product-requests",row.id(),null,actorId);
            systemEvent(row.id(),"SYSTEM_ROUTING","نیازمند شناسایی یا ایجاد محصول",actorId);
            return;
        }
        routeByPrice(row.id(),row.productId(),row.requestNo(),actorId);
    }

    @Transactional
    public void afterProductAttached(UUID caseId,UUID productId,AuthPrincipal actor){
        prices.syncCasePriceTag(caseId);
        Long requestNo=jdbc.queryForObject("SELECT r.request_no FROM workflow_cases w JOIN customer_requests r ON r.id=w.request_id WHERE w.id=?",Long.class,caseId);
        routeByPrice(caseId,productId,requestNo==null?0:requestNo,actor.id());
    }

    @Transactional
    public void afterTagChanged(UUID caseId,AuthPrincipal actor,String description){
        notifications.notifyRoles(Set.of("SUPER_ADMIN","MANAGER","ADMIN"),"CASE_TAG_CHANGED","برچسب پرونده تغییر کرد",
                description,"/admin/operations",caseId,null,actor.id());
    }

    public void afterCustomerQuoteSent(UUID caseId,AuthPrincipal actor){
        notifications.notifyCustomerCase(caseId,"CUSTOMER_QUOTE","قیمت جدید برای درخواست شما",
                "قیمت پیشنهادی جدید برای درخواست شما ثبت شده است. وارد پنل شوید و جزئیات را پیگیری کنید.",
                "/panel/requests","IN_APP",actor);
    }

    public void afterOrderCreated(UUID caseId,AuthPrincipal actor){
        notifications.notifyCustomerCase(caseId,"ORDER_CREATED","سفارش شما ایجاد شد",
                "پس از توافق، سفارش شما در سیستم ایجاد شد و مراحل بعدی از پنل قابل پیگیری است.",
                "/panel/orders","IN_APP",actor);
    }

    public void afterShipmentStatus(UUID shipmentId,String status,AuthPrincipal actor){
        UUID caseId;
        try{caseId=jdbc.queryForObject("SELECT case_id FROM shipments WHERE id=?",UUID.class,shipmentId);}catch(Exception e){return;}
        if(caseId==null)return;
        String text=switch(status==null?"":status.toUpperCase(Locale.ROOT)){
            case "IN_TRANSIT" -> "مرسوله شما وارد مرحله حمل بین‌المللی شد.";
            case "CUSTOMS","ARRIVED_COUNTRY" -> "مرسوله شما به مرحله ورود کشور/گمرک رسیده است.";
            case "RELEASED","LOCAL_DELIVERY" -> "مرسوله از گمرک عبور کرده و وارد مرحله تحویل محلی شده است.";
            case "DELIVERED" -> "مرسوله تحویل شده است.";
            default -> "وضعیت مرسوله شما به‌روزرسانی شد: "+status;
        };
        notifications.notifyCustomerCase(caseId,"SHIPMENT_STATUS","به‌روزرسانی وضعیت ارسال",text,"/panel/orders","IN_APP",actor);
    }

    private void routeByPrice(UUID caseId,UUID productId,long requestNo,UUID actorId){
        ProductPriceService.PriceState state=prices.stateForProduct(productId);
        if("VALID".equals(state.state())){
            notifications.notifyRoles(Set.of("SUPER_ADMIN","MANAGER","ADMIN","FINANCE"),"VALID_PRICE_AVAILABLE",
                    "قیمت معتبر برای درخواست موجود است","درخواست #"+requestNo+" به محصولی با قیمت معتبر متصل است؛ آماده بررسی و ارائه به مشتری.",
                    "/admin/products/"+productId+"/operations",caseId,productId,actorId);
            systemEvent(caseId,"PRICE_ROUTE","قیمت معتبر موجود؛ نیاز به استعلام مجدد نیست مگر با تصمیم مدیر.",actorId);
        }else{
            notifications.notifyRoles(Set.of("SUPER_ADMIN","MANAGER","ADMIN","PROCUREMENT"),"PRICE_INQUIRY_NEEDED",
                    "نیازمند استعلام قیمت","برای درخواست #"+requestNo+" قیمت معتبر فعلی وجود ندارد یا منقضی شده است.",
                    "/manager/cases/"+caseId,caseId,productId,actorId);
            systemEvent(caseId,"PRICE_ROUTE","قیمت معتبر وجود ندارد؛ استعلام قیمت لازم است.",actorId);
        }
    }

    private void systemEvent(UUID caseId,String type,String note,UUID actorId){
        jdbc.update("INSERT INTO workflow_events(case_id,event_type,note,created_by_user_id) VALUES (?,?,?,?)",caseId,type,note,actorId);
    }
}
