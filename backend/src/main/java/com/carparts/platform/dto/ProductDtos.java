package com.carparts.platform.dto;

import com.carparts.platform.product.ProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProductDtos {
    private ProductDtos() {}

    public record ImageInput(
            @NotBlank @Size(max = 2000) String imageUrl,
            @Size(max = 255) String altText
    ) {}

    public record PhysicalDetailsInput(
            @NotBlank @Size(max = 255) String descriptionFa,
            @NotBlank @Size(max = 255) String descriptionEn,
            @DecimalMin("0.0") BigDecimal weightKg,
            @DecimalMin("0.0") BigDecimal lengthCm,
            @DecimalMin("0.0") BigDecimal widthCm,
            @DecimalMin("0.0") BigDecimal heightCm
    ) {}

    public record CreateProductRequest(
            @NotBlank String descriptionFa,
            @NotBlank String descriptionEn,
            @NotBlank @Size(max = 100) String realPartNumber,
            @NotNull UUID vehicleTypeCodeId,
            @NotNull UUID vehicleBrandCodeId,
            @NotNull UUID partBrandCodeId,
            @NotNull UUID packagingCodeId,
            @NotNull UUID qualityCodeId,
            @NotNull UUID productGroupCodeId,
            @NotNull UUID unitCodeId,
            UUID physicalCodeId,
            String code1,
            String code2,
            ProductStatus status,
            String subject1,
            String subject2,
            String subject3,
            @Valid @Size(max = 10, message = "A product can have at most 10 images") List<ImageInput> images
    ) {}

    public record UpdateProductRequest(
            @NotBlank String descriptionFa,
            @NotBlank String descriptionEn,
            @NotBlank @Size(max = 100) String realPartNumber,
            @NotNull UUID productGroupCodeId,
            @NotNull UUID unitCodeId,
            UUID physicalCodeId,
            @Valid PhysicalDetailsInput physicalDetails,
            String code1,
            String code2,
            @NotNull ProductStatus status,
            String subject1,
            String subject2,
            String subject3,
            @Valid @Size(max = 10, message = "A product can have at most 10 images") List<ImageInput> images
    ) {}

    public record ImageResponse(String imageUrl, String altText, short sortOrder) {}

    public record PhysicalProfile(
            UUID id,
            int code,
            String descriptionFa,
            String descriptionEn,
            BigDecimal weightKg,
            BigDecimal lengthCm,
            BigDecimal widthCm,
            BigDecimal heightCm
    ) {}

    public record ProductResponse(
            UUID id,
            String descriptionFa,
            String descriptionEn,
            String realPartNumber,
            String productCode,
            int productSerial,
            UUID productGroupCodeId,
            UUID unitCodeId,
            UUID physicalCodeId,
            PhysicalProfile physicalProfile,
            String code1,
            String code2,
            ProductStatus status,
            String subject1,
            String subject2,
            String subject3,
            List<ImageResponse> images,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record CodeSelection(
            UUID vehicleTypeCodeId,
            UUID vehicleBrandCodeId,
            UUID partBrandCodeId,
            UUID packagingCodeId,
            UUID qualityCodeId
    ) {}

    public record CodePreview(String productCode, int nextSerial) {}
}
