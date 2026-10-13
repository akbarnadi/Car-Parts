package com.carparts.platform.lookup;

import com.carparts.platform.auth.AuthPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin/lookups")
public class LookupAdminController {
    private final LookupAdminService service; public LookupAdminController(LookupAdminService service){this.service=service;}
    @GetMapping("/{type}") public List<LookupAdminService.Row> list(@PathVariable String type){return service.list(type);}
    @PostMapping("/{type}") public LookupAdminService.Row create(@PathVariable String type,@RequestBody LookupAdminService.Input in,Authentication a){return service.create(type,in,principal(a).id());}
    @PutMapping("/{type}/{id}") public LookupAdminService.Row update(@PathVariable String type,@PathVariable UUID id,@RequestBody LookupAdminService.Input in,Authentication a){return service.update(type,id,in,principal(a).id());}
    @DeleteMapping("/{type}/{id}") public void deactivate(@PathVariable String type,@PathVariable UUID id,Authentication a){service.deactivate(type,id,principal(a).id());}
    private AuthPrincipal principal(Authentication a){return (AuthPrincipal)a.getPrincipal();}
}
