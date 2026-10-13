package com.carparts.platform.workflow;

import com.carparts.platform.auth.AuthPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/manager")
public class WorkflowController {
    private final WorkflowService service;
    private final WorkflowAutomationService automation;
    public WorkflowController(WorkflowService service,WorkflowAutomationService automation){this.service=service;this.automation=automation;}

    @GetMapping("/overview") public WorkflowService.Overview overview(){return service.overview();}
    @GetMapping("/cases") public List<WorkflowService.CaseRow> cases(@RequestParam(required=false) String stage){return service.cases(stage);}
    @GetMapping("/cases/{id}") public WorkflowService.CaseDetail detail(@PathVariable UUID id){return service.detail(id);}

    @PostMapping("/requests")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER','SUPPORT')")
    public WorkflowService.CaseRow create(@RequestBody WorkflowService.CreateRequest r,Authentication a){
        AuthPrincipal actor=p(a); WorkflowService.CaseRow row=service.createManual(r,actor); automation.afterRequest(row,actor); return row;
    }

    @PostMapping("/cases/{id}/transition")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER')")
    public WorkflowService.CaseRow transition(@PathVariable UUID id,@RequestBody WorkflowService.Transition r,Authentication a){return service.transition(id,r,p(a));}

    @GetMapping("/tags") public List<WorkflowService.TagRow> tags(){return service.availableTags();}

    @PostMapping("/cases/{id}/tags/{tagId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER','SUPPORT')")
    public void addTag(@PathVariable UUID id,@PathVariable UUID tagId,Authentication a){
        AuthPrincipal actor=p(a);service.addManualTag(id,tagId,actor);automation.afterTagChanged(id,actor,"یک برچسب دستی به پرونده افزوده شد.");
    }

    @DeleteMapping("/cases/{id}/tags/{tagId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER','SUPPORT')")
    public void removeTag(@PathVariable UUID id,@PathVariable UUID tagId,Authentication a){
        AuthPrincipal actor=p(a);service.removeManualTag(id,tagId,actor);automation.afterTagChanged(id,actor,"یک برچسب دستی از پرونده حذف شد.");
    }

    @PostMapping("/cases/{id}/supplier-quotes")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER','PROCUREMENT')")
    public Map<String,Object> supplier(@PathVariable UUID id,@RequestBody WorkflowService.SupplierQuoteInput r,Authentication a){return service.addSupplierQuote(id,r,p(a));}

    @PostMapping("/cases/{id}/customer-quotes")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER','FINANCE')")
    public Map<String,Object> customer(@PathVariable UUID id,@RequestBody WorkflowService.CustomerQuoteInput r,Authentication a){
        AuthPrincipal actor=p(a);Map<String,Object> result=service.addCustomerQuote(id,r,actor);if(r.sendNow())automation.afterCustomerQuoteSent(id,actor);return result;
    }

    private AuthPrincipal p(Authentication a){return (AuthPrincipal)a.getPrincipal();}
}
