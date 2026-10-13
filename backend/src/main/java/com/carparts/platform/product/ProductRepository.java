package com.carparts.platform.product;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    @EntityGraph(attributePaths = "images")
    Optional<Product> findByIdAndDeletedAtIsNull(UUID id);

    @EntityGraph(attributePaths = "images")
    List<Product> findAllByDeletedAtIsNullOrderByCreatedAtDesc();
}
