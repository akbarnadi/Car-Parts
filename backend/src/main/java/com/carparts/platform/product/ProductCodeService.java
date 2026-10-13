package com.carparts.platform.product;

import com.carparts.platform.dto.ProductDtos.CodePreview;
import com.carparts.platform.dto.ProductDtos.CodeSelection;
import com.carparts.platform.lookup.LookupService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProductCodeService {
    private final JdbcTemplate jdbc;
    private final LookupService lookups;

    public ProductCodeService(JdbcTemplate jdbc, LookupService lookups) {
        this.jdbc = jdbc;
        this.lookups = lookups;
    }

    public record Allocation(String productCode, int serial) {}

    public CodePreview preview(CodeSelection selection) {
        CodeParts parts = resolve(selection);
        Integer serial = jdbc.query("""
                SELECT last_serial + 1 AS next_serial
                FROM product_serial_counters
                WHERE vehicle_type_code_id = ? AND vehicle_brand_code_id = ?
                  AND part_brand_code_id = ? AND packaging_code_id = ? AND quality_code_id = ?
                """, rs -> rs.next() ? rs.getInt("next_serial") : null,
                selection.vehicleTypeCodeId(), selection.vehicleBrandCodeId(), selection.partBrandCodeId(),
                selection.packagingCodeId(), selection.qualityCodeId());
        int next = serial == null ? 1 : serial;
        ensureSerialAvailable(next);
        return new CodePreview(format(parts, next), next);
    }

    public Allocation allocate(CodeSelection selection) {
        CodeParts parts = resolve(selection);
        List<Integer> serials = jdbc.query("""
                INSERT INTO product_serial_counters
                    (vehicle_type_code_id, vehicle_brand_code_id, part_brand_code_id, packaging_code_id, quality_code_id, last_serial)
                VALUES (?, ?, ?, ?, ?, 1)
                ON CONFLICT (vehicle_type_code_id, vehicle_brand_code_id, part_brand_code_id, packaging_code_id, quality_code_id)
                DO UPDATE SET last_serial = product_serial_counters.last_serial + 1, updated_at = NOW()
                WHERE product_serial_counters.last_serial < 9999
                RETURNING last_serial
                """, (rs, rowNum) -> rs.getInt("last_serial"),
                selection.vehicleTypeCodeId(), selection.vehicleBrandCodeId(), selection.partBrandCodeId(),
                selection.packagingCodeId(), selection.qualityCodeId());
        if (serials.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The 4-digit serial range is exhausted for this code combination.");
        }
        int serial = serials.getFirst();
        return new Allocation(format(parts, serial), serial);
    }

    private CodeParts resolve(CodeSelection selection) {
        int vehicleType = lookups.activeCode("vehicle_type_codes", selection.vehicleTypeCodeId());
        int vehicleBrand = lookups.activeVehicleBrandCode(selection.vehicleBrandCodeId(), selection.vehicleTypeCodeId());
        int partBrand = lookups.activeCode("part_brand_codes", selection.partBrandCodeId());
        int packaging = lookups.activeCode("packaging_codes", selection.packagingCodeId());
        int quality = lookups.activeCode("quality_codes", selection.qualityCodeId());
        return new CodeParts(vehicleType, vehicleBrand, partBrand, packaging, quality);
    }

    private String format(CodeParts p, int serial) {
        ensureSerialAvailable(serial);
        return "%02d%02d%02d%02d%d%04d".formatted(
                p.vehicleType(), p.vehicleBrand(), p.partBrand(), p.packaging(), p.quality(), serial);
    }

    private void ensureSerialAvailable(int serial) {
        if (serial < 1 || serial > 9999) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The 4-digit serial range is exhausted for this code combination.");
        }
    }

    private record CodeParts(int vehicleType, int vehicleBrand, int partBrand, int packaging, int quality) {}
}
