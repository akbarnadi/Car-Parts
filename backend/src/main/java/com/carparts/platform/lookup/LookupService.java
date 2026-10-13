package com.carparts.platform.lookup;

import com.carparts.platform.dto.ProductDtos.PhysicalProfile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LookupService {
    private final JdbcTemplate jdbc;

    public LookupService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record LookupOption(UUID id, int code, String descriptionFa, String descriptionEn) {}
    public record VehicleBrandOption(UUID id, UUID vehicleTypeCodeId, int code, String descriptionFa, String descriptionEn) {}
    public record PartBrandOption(UUID id, int code, String descriptionFa, String descriptionEn,
                                  String designCountry, String manufacturingCountry) {}

    public Map<String, Object> all() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("vehicleTypes", options("vehicle_type_codes"));
        result.put("vehicleBrands", vehicleBrands());
        result.put("partBrands", partBrands());
        result.put("packaging", options("packaging_codes"));
        result.put("qualities", options("quality_codes"));
        result.put("productGroups", options("product_group_codes"));
        result.put("units", options("unit_codes"));
        result.put("physicalCodes", physicalProfiles());
        return result;
    }

    public int activeCode(String table, UUID id) {
        if (!ALLOWED_CODE_TABLES.contains(table)) {
            throw new IllegalArgumentException("Unsupported code table");
        }
        List<Integer> codes = jdbc.query("SELECT code FROM " + table + " WHERE id = ? AND is_active = TRUE",
                (rs, rowNum) -> rs.getInt("code"), id);
        if (codes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or inactive code selection: " + table);
        }
        return codes.getFirst();
    }

    public int activeVehicleBrandCode(UUID vehicleBrandId, UUID vehicleTypeId) {
        List<Integer> codes = jdbc.query("""
                SELECT code
                FROM vehicle_brand_codes
                WHERE id = ? AND vehicle_type_code_id = ? AND is_active = TRUE
                """, (rs, rowNum) -> rs.getInt("code"), vehicleBrandId, vehicleTypeId);
        if (codes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Selected vehicle brand does not belong to the selected vehicle type.");
        }
        return codes.getFirst();
    }

    public void assertActive(UUID id, String table, String label) {
        if (!ALLOWED_REFERENCE_TABLES.contains(table)) throw new IllegalArgumentException("Unsupported reference table");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id = ? AND is_active = TRUE", Integer.class, id);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or inactive " + label);
        }
    }

    public PhysicalProfile physicalProfile(UUID id) {
        if (id == null) return null;
        List<PhysicalProfile> rows = jdbc.query("""
                SELECT id, code, description_fa, description_en, weight_kg, length_cm, width_cm, height_cm
                FROM physical_codes WHERE id = ?
                """, (rs, rowNum) -> new PhysicalProfile(
                rs.getObject("id", UUID.class),
                rs.getInt("code"),
                rs.getString("description_fa"),
                rs.getString("description_en"),
                rs.getBigDecimal("weight_kg"),
                rs.getBigDecimal("length_cm"),
                rs.getBigDecimal("width_cm"),
                rs.getBigDecimal("height_cm")
        ), id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public UUID createPhysicalProfile(com.carparts.platform.dto.ProductDtos.PhysicalDetailsInput input) {
        return jdbc.queryForObject("""
                INSERT INTO physical_codes
                    (description_fa, description_en, weight_kg, length_cm, width_cm, height_cm)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """, UUID.class,
                input.descriptionFa(), input.descriptionEn(), input.weightKg(), input.lengthCm(), input.widthCm(), input.heightCm());
    }

    private List<LookupOption> options(String table) {
        return jdbc.query("SELECT id, code, description_fa, description_en FROM " + table + " WHERE is_active = TRUE ORDER BY code",
                (rs, rowNum) -> new LookupOption(
                        rs.getObject("id", UUID.class), rs.getInt("code"),
                        rs.getString("description_fa"), rs.getString("description_en")));
    }

    private List<VehicleBrandOption> vehicleBrands() {
        return jdbc.query("""
                SELECT id, vehicle_type_code_id, code, description_fa, description_en
                FROM vehicle_brand_codes
                WHERE is_active = TRUE
                ORDER BY vehicle_type_code_id, code
                """, (rs, rowNum) -> new VehicleBrandOption(
                rs.getObject("id", UUID.class),
                rs.getObject("vehicle_type_code_id", UUID.class),
                rs.getInt("code"),
                rs.getString("description_fa"),
                rs.getString("description_en")));
    }

    private List<PartBrandOption> partBrands() {
        return jdbc.query("""
                SELECT id, code, description_fa, description_en, design_country, manufacturing_country
                FROM part_brand_codes WHERE is_active = TRUE ORDER BY code
                """, (rs, rowNum) -> new PartBrandOption(
                rs.getObject("id", UUID.class), rs.getInt("code"),
                rs.getString("description_fa"), rs.getString("description_en"),
                rs.getString("design_country"), rs.getString("manufacturing_country")));
    }

    private List<PhysicalProfile> physicalProfiles() {
        return jdbc.query("""
                SELECT id, code, description_fa, description_en, weight_kg, length_cm, width_cm, height_cm
                FROM physical_codes WHERE is_active = TRUE ORDER BY code
                """, (rs, rowNum) -> new PhysicalProfile(
                rs.getObject("id", UUID.class), rs.getInt("code"),
                rs.getString("description_fa"), rs.getString("description_en"),
                rs.getBigDecimal("weight_kg"), rs.getBigDecimal("length_cm"),
                rs.getBigDecimal("width_cm"), rs.getBigDecimal("height_cm")));
    }

    private static final List<String> ALLOWED_CODE_TABLES = List.of(
            "vehicle_type_codes", "part_brand_codes", "packaging_codes", "quality_codes");
    private static final List<String> ALLOWED_REFERENCE_TABLES = List.of(
            "product_group_codes", "unit_codes", "physical_codes");
}
