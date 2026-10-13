package com.carparts.platform.product;

import com.carparts.platform.auth.AuthPrincipal;
import com.carparts.platform.dto.ProductDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;
    private final JdbcTemplate jdbc;

    public ProductController(ProductService productService,JdbcTemplate jdbc) {
        this.productService=productService;this.jdbc=jdbc;
    }

    @GetMapping public List<ProductResponse> list(){return productService.list();}
    @GetMapping("/{id}") public ProductResponse get(@PathVariable UUID id){return productService.get(id);}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request,Authentication a){
        ProductResponse product=productService.create(request);audit(a,product.id(),"PRODUCT_CREATED","ایجاد محصول و تخصیص سریال "+product.productSerial());return product;
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable UUID id,@Valid @RequestBody UpdateProductRequest request,Authentication a){
        ProductResponse product=productService.update(id,request);audit(a,id,"PRODUCT_UPDATED","ویرایش اطلاعات محصول");return product;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID id,Authentication a){productService.remove(id);audit(a,id,"PRODUCT_REMOVED","حذف نرم محصول");}

    @GetMapping("/code-preview")
    public CodePreview preview(@RequestParam UUID vehicleTypeCodeId,@RequestParam UUID vehicleBrandCodeId,@RequestParam UUID partBrandCodeId,
                               @RequestParam UUID packagingCodeId,@RequestParam UUID qualityCodeId){
        return productService.preview(new CodeSelection(vehicleTypeCodeId,vehicleBrandCodeId,partBrandCodeId,packagingCodeId,qualityCodeId));
    }

    private void audit(Authentication a,UUID id,String action,String reason){
        AuthPrincipal p=(AuthPrincipal)a.getPrincipal();
        jdbc.update("INSERT INTO audit_log(actor_user_id,entity_type,entity_id,action,source,reason) VALUES (?,'products',?,?,'APPLICATION',?)",
                p.id(),id.toString(),action,reason);
    }
}
