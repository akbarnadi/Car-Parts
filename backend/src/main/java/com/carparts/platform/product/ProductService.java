package com.carparts.platform.product;

import com.carparts.platform.dto.ProductDtos.*;
import com.carparts.platform.lookup.LookupService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ProductService {
    private final ProductRepository products;
    private final ProductCodeService productCodes;
    private final LookupService lookups;
    private final JdbcTemplate jdbc;

    public ProductService(ProductRepository products, ProductCodeService productCodes, LookupService lookups, JdbcTemplate jdbc) {
        this.products = products;
        this.productCodes = productCodes;
        this.lookups = lookups;
        this.jdbc = jdbc;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        validateReferences(request.productGroupCodeId(), request.unitCodeId(), request.physicalCodeId());

        CodeSelection selection = new CodeSelection(
                request.vehicleTypeCodeId(), request.vehicleBrandCodeId(), request.partBrandCodeId(),
                request.packagingCodeId(), request.qualityCodeId());
        ProductCodeService.Allocation allocation = productCodes.allocate(selection);

        Product product = new Product();
        product.setDescriptionFa(request.descriptionFa().trim());
        product.setDescriptionEn(request.descriptionEn().trim());
        product.setRealPartNumber(request.realPartNumber().trim());
        product.setProductCode(allocation.productCode());
        product.setProductGroupCodeId(request.productGroupCodeId());
        product.setUnitCodeId(request.unitCodeId());
        product.setPhysicalCodeId(request.physicalCodeId());
        product.setCode1(clean(request.code1()));
        product.setCode2(clean(request.code2()));
        product.setStatus(request.status() == null ? ProductStatus.DRAFT : request.status());
        product.setSubject1(clean(request.subject1()));
        product.setSubject2(clean(request.subject2()));
        product.setSubject3(clean(request.subject3()));
        product.replaceImages(toImages(request.images()));

        Product saved = products.saveAndFlush(product);
        jdbc.update("""
                INSERT INTO product_code_details
                    (product_id, vehicle_type_code_id, vehicle_brand_code_id, part_brand_code_id,
                     packaging_code_id, quality_code_id, product_serial)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, saved.getId(), selection.vehicleTypeCodeId(), selection.vehicleBrandCodeId(),
                selection.partBrandCodeId(), selection.packagingCodeId(), selection.qualityCodeId(), allocation.serial());
        return toResponse(saved, allocation.serial());
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list() {
        return products.findAllByDeletedAtIsNullOrderByCreatedAtDesc().stream()
                .map(product -> toResponse(product, serialFor(product.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        Product product = getEntity(id);
        return toResponse(product, serialFor(id));
    }

    @Transactional
    public ProductResponse update(UUID id, UpdateProductRequest request) {
        Product product = getEntity(id);
        UUID physicalCodeId = request.physicalCodeId();
        if (request.physicalDetails() != null) {
            physicalCodeId = lookups.createPhysicalProfile(request.physicalDetails());
        }
        validateReferences(request.productGroupCodeId(), request.unitCodeId(), physicalCodeId);

        product.setDescriptionFa(request.descriptionFa().trim());
        product.setDescriptionEn(request.descriptionEn().trim());
        product.setRealPartNumber(request.realPartNumber().trim());
        product.setProductGroupCodeId(request.productGroupCodeId());
        product.setUnitCodeId(request.unitCodeId());
        product.setPhysicalCodeId(physicalCodeId);
        product.setCode1(clean(request.code1()));
        product.setCode2(clean(request.code2()));
        product.setStatus(request.status());
        product.setSubject1(clean(request.subject1()));
        product.setSubject2(clean(request.subject2()));
        product.setSubject3(clean(request.subject3()));

        // Flush orphan removals before inserting the replacement image set. This keeps
        // the database-level 10-image guard and unique sort-order constraint reliable.
        product.replaceImages(List.of());
        products.saveAndFlush(product);
        product.replaceImages(toImages(request.images()));

        Product saved = products.saveAndFlush(product);
        return toResponse(saved, serialFor(id));
    }

    @Transactional
    public void remove(UUID id) {
        Product product = getEntity(id);
        product.setDeletedAt(Instant.now());
        product.setStatus(ProductStatus.INACTIVE);
        products.save(product);
    }

    public CodePreview preview(CodeSelection selection) {
        return productCodes.preview(selection);
    }

    private void validateReferences(UUID groupId, UUID unitId, UUID physicalId) {
        lookups.assertActive(groupId, "product_group_codes", "product group");
        lookups.assertActive(unitId, "unit_codes", "unit");
        if (physicalId != null) lookups.assertActive(physicalId, "physical_codes", "physical code");
    }

    private Product getEntity(UUID id) {
        return products.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    private int serialFor(UUID productId) {
        Integer serial = jdbc.queryForObject("SELECT product_serial FROM product_code_details WHERE product_id = ?", Integer.class, productId);
        if (serial == null) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Product code details are missing");
        return serial;
    }

    private ProductResponse toResponse(Product p, int serial) {
        return new ProductResponse(
                p.getId(), p.getDescriptionFa(), p.getDescriptionEn(), p.getRealPartNumber(), p.getProductCode(), serial,
                p.getProductGroupCodeId(), p.getUnitCodeId(), p.getPhysicalCodeId(), lookups.physicalProfile(p.getPhysicalCodeId()),
                p.getCode1(), p.getCode2(), p.getStatus(), p.getSubject1(), p.getSubject2(), p.getSubject3(),
                p.getImages().stream().map(i -> new ImageResponse(i.getImageUrl(), i.getAltText(), i.getSortOrder())).toList(),
                p.getCreatedAt(), p.getUpdatedAt());
    }

    private List<ProductImage> toImages(List<ImageInput> inputs) {
        if (inputs == null) return List.of();
        if (inputs.size() > 10) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A product can have at most 10 images");
        return java.util.stream.IntStream.range(0, inputs.size()).mapToObj(index -> {
            ImageInput input = inputs.get(index);
            ProductImage image = new ProductImage();
            image.setImageUrl(input.imageUrl().trim());
            image.setAltText(clean(input.altText()));
            image.setSortOrder((short) (index + 1));
            return image;
        }).toList();
    }

    private String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
