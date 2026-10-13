package com.carparts.platform.workflow;

import com.carparts.platform.auth.AuthPrincipal;
import com.carparts.platform.notification.NotificationService;
import com.carparts.platform.product.ProductPriceService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin/operations")
public class AdminOperationsController {
    private final WorkflowService workflow;
    private final WorkflowAutomationService automation;
    private final ProductPriceService prices;
    private final NotificationService notifications;

    public AdminOperationsController(WorkflowService workflow,WorkflowAutomationService automation,ProductPriceService prices,NotificationService notifications){
        this.workflow=workflow;this.automation=automation;this.prices=prices;this.notifications=notifications;
    }

    public record SafeCase(WorkflowService.CaseRow caseInfo,String priceState,ProductPriceService.PriceRow currentPrice){}

    @GetMapping("/cases")
    public List<SafeCase> cases(@RequestParam(required=false) String stage){
        return workflow.cases(stage).stream().map(c->{var s=prices.stateForProduct(c.productId());return new SafeCase(c,s.state(),s.current());}).toList();
    }

    @GetMapping("/tags") public List<WorkflowService.TagRow> tags(){return workflow.availableTags();}

    @PostMapping("/cases/{caseId}/tags/{tagId}")
    public void addTag(@PathVariable UUID caseId,@PathVariable UUID tagId,Authentication a){
        AuthPrincipal actor=p(a);workflow.addManualTag(caseId,tagId,actor);automation.afterTagChanged(caseId,actor,"ادمین برچسبی به پرونده افزود.");
    }

    @DeleteMapping("/cases/{caseId}/tags/{tagId}")
    public void removeTag(@PathVariable UUID caseId,@PathVariable UUID tagId,Authentication a){
        AuthPrincipal actor=p(a);workflow.removeManualTag(caseId,tagId,actor);automation.afterTagChanged(caseId,actor,"ادمین برچسبی را از پرونده حذف کرد.");
    }

    @GetMapping("/notification-outbox")
    public List<Map<String,Object>> outbox(){return notifications.outbox();}

    @PutMapping("/notification-outbox/{id}/sent")
    public void sent(@PathVariable UUID id,Authentication a){notifications.markOutboxSent(id,p(a));}

    private AuthPrincipal p(Authentication a){return (AuthPrincipal)a.getPrincipal();}
}
