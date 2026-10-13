package com.carparts.platform.product;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "description_fa", nullable = false, columnDefinition = "text")
    private String descriptionFa;

    @Column(name = "description_en", nullable = false, columnDefinition = "text")
    private String descriptionEn;

    @Column(name = "real_part_number", nullable = false, length = 100)
    private String realPartNumber;

    @Column(name = "product_code", nullable = false, unique = true, length = 13, updatable = false)
    private String productCode;

    @Column(name = "product_group_code_id", nullable = false)
    private UUID productGroupCodeId;

    @Column(name = "unit_code_id", nullable = false)
    private UUID unitCodeId;

    @Column(name = "physical_code_id")
    private UUID physicalCodeId;

    @Column(name = "code_1", columnDefinition = "text")
    private String code1;

    @Column(name = "code_2", columnDefinition = "text")
    private String code2;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status = ProductStatus.DRAFT;

    @Column(name = "subject_1", columnDefinition = "text")
    private String subject1;
    @Column(name = "subject_2", columnDefinition = "text")
    private String subject2;
    @Column(name = "subject_3", columnDefinition = "text")
    private String subject3;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ProductImage> images = new ArrayList<>();

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public void replaceImages(List<ProductImage> replacements) {
        images.clear();
        replacements.forEach(image -> {
            image.setProduct(this);
            images.add(image);
        });
    }

    public UUID getId() { return id; }
    public String getDescriptionFa() { return descriptionFa; }
    public void setDescriptionFa(String descriptionFa) { this.descriptionFa = descriptionFa; }
    public String getDescriptionEn() { return descriptionEn; }
    public void setDescriptionEn(String descriptionEn) { this.descriptionEn = descriptionEn; }
    public String getRealPartNumber() { return realPartNumber; }
    public void setRealPartNumber(String realPartNumber) { this.realPartNumber = realPartNumber; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public UUID getProductGroupCodeId() { return productGroupCodeId; }
    public void setProductGroupCodeId(UUID productGroupCodeId) { this.productGroupCodeId = productGroupCodeId; }
    public UUID getUnitCodeId() { return unitCodeId; }
    public void setUnitCodeId(UUID unitCodeId) { this.unitCodeId = unitCodeId; }
    public UUID getPhysicalCodeId() { return physicalCodeId; }
    public void setPhysicalCodeId(UUID physicalCodeId) { this.physicalCodeId = physicalCodeId; }
    public String getCode1() { return code1; }
    public void setCode1(String code1) { this.code1 = code1; }
    public String getCode2() { return code2; }
    public void setCode2(String code2) { this.code2 = code2; }
    public ProductStatus getStatus() { return status; }
    public void setStatus(ProductStatus status) { this.status = status; }
    public String getSubject1() { return subject1; }
    public void setSubject1(String subject1) { this.subject1 = subject1; }
    public String getSubject2() { return subject2; }
    public void setSubject2(String subject2) { this.subject2 = subject2; }
    public String getSubject3() { return subject3; }
    public void setSubject3(String subject3) { this.subject3 = subject3; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
    public List<ProductImage> getImages() { return images; }
}
