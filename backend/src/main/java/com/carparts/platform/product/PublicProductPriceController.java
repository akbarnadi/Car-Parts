package com.carparts.platform.product;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/public/product-prices")
public class PublicProductPriceController {
    private final ProductPriceService prices;
    public PublicProductPriceController(ProductPriceService prices){this.prices=prices;}
    @GetMapping public List<ProductPriceService.PriceRow> list(){return prices.publicCurrentPrices();}
}
