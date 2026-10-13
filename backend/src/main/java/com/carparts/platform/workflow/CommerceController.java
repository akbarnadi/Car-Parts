package com.carparts.platform.workflow;

import com.carparts.platform.auth.AuthPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/manager")
public class CommerceController {
    private final CommerceService service;
    private final WorkflowAutomationService automation;
    public CommerceController(CommerceService service,WorkflowAutomationService automation){this.service=service;this.automation=automation;}

    @GetMapping("/cases/{id}/commerce") public Map<String,Object> commerce(@PathVariable UUID id){return service.commerce(id);}

    @PostMapping("/cases/{id}/order")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER','FINANCE')")
    public Map<String,Object> order(@PathVariable UUID id,@RequestBody CommerceService.OrderInput r,Authentication a){
        AuthPrincipal actor=p(a);Map<String,Object> result=service.createOrder(id,r,actor);automation.afterOrderCreated(id,actor);return result;
    }

    @PostMapping("/orders/{id}/payments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER','FINANCE')")
    public Map<String,Object> payment(@PathVariable UUID id,@RequestBody CommerceService.PaymentInput r,Authentication a){return service.addPayment(id,r,p(a));}

    @PostMapping("/cases/{id}/shipments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER','SHIPPING','PROCUREMENT')")
    public Map<String,Object> shipment(@PathVariable UUID id,@RequestBody CommerceService.ShipmentInput r,Authentication a){return service.addShipment(id,r,p(a));}

    @PutMapping("/shipments/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER','SHIPPING')")
    public Map<String,Object> shipmentStatus(@PathVariable UUID id,@RequestBody CommerceService.ShipmentStatus r,Authentication a){
        AuthPrincipal actor=p(a);Map<String,Object> result=service.shipmentStatus(id,r,actor);automation.afterShipmentStatus(id,r.status(),actor);return result;
    }

    private AuthPrincipal p(Authentication a){return (AuthPrincipal)a.getPrincipal();}
}
