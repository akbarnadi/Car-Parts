package com.carparts.platform.workflow;

import com.carparts.platform.auth.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/manager/cases/{caseId}/pricing-workbook")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER','FINANCE')")
public class PricingWorkbenchController {
    private final JdbcTemplate jdbc;
    public PricingWorkbenchController(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public record Input(
            String currency,
            BigDecimal actualSupplier,BigDecimal actualLogistics,BigDecimal actualCustoms,BigDecimal actualBank,BigDecimal actualLocal,BigDecimal actualOther,
            BigDecimal chargedSupplier,BigDecimal chargedLogistics,BigDecimal chargedCustoms,BigDecimal chargedBank,BigDecimal chargedLocal,BigDecimal chargedOther,
            BigDecimal markupPercent,BigDecimal taxAmount,BigDecimal discountAmount,BigDecimal exchangeRate,
            String pricingFormula,BigDecimal calculatedTotal,BigDecimal grossProfit){}

    @GetMapping
    public Map<String,Object> get(@PathVariable UUID caseId){
        ensureCase(caseId);
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM case_pricing_workbooks WHERE case_id=?",caseId);
        if(rows.isEmpty()) return Map.of();
        return rows.getFirst();
    }

    @PutMapping
    public Map<String,Object> save(@PathVariable UUID caseId,@RequestBody Input in,Authentication auth){
        ensureCase(caseId);
        AuthPrincipal actor=(AuthPrincipal)auth.getPrincipal();
        BigDecimal pct=zero(in.markupPercent());
        if(pct.compareTo(BigDecimal.ZERO)<0||pct.compareTo(new BigDecimal("100"))>0)
            throw bad("درصد سود باید بین ۰ و ۱۰۰ باشد.");
        String currency=(in.currency()==null||in.currency().isBlank()?"USD":in.currency().trim().toUpperCase(Locale.ROOT));
        String formula=(in.pricingFormula()==null||in.pricingFormula().isBlank()?defaultFormula():in.pricingFormula().trim());
        jdbc.update("""
            INSERT INTO case_pricing_workbooks(
              case_id,currency,actual_supplier,actual_logistics,actual_customs,actual_bank,actual_local,actual_other,
              charged_supplier,charged_logistics,charged_customs,charged_bank,charged_local,charged_other,
              markup_percent,tax_amount,discount_amount,exchange_rate,pricing_formula,calculated_total,gross_profit,updated_by_user_id,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW())
            ON CONFLICT(case_id) DO UPDATE SET
              currency=EXCLUDED.currency,actual_supplier=EXCLUDED.actual_supplier,actual_logistics=EXCLUDED.actual_logistics,
              actual_customs=EXCLUDED.actual_customs,actual_bank=EXCLUDED.actual_bank,actual_local=EXCLUDED.actual_local,actual_other=EXCLUDED.actual_other,
              charged_supplier=EXCLUDED.charged_supplier,charged_logistics=EXCLUDED.charged_logistics,charged_customs=EXCLUDED.charged_customs,
              charged_bank=EXCLUDED.charged_bank,charged_local=EXCLUDED.charged_local,charged_other=EXCLUDED.charged_other,
              markup_percent=EXCLUDED.markup_percent,tax_amount=EXCLUDED.tax_amount,discount_amount=EXCLUDED.discount_amount,
              exchange_rate=EXCLUDED.exchange_rate,pricing_formula=EXCLUDED.pricing_formula,calculated_total=EXCLUDED.calculated_total,
              gross_profit=EXCLUDED.gross_profit,updated_by_user_id=EXCLUDED.updated_by_user_id,updated_at=NOW()
            """,
            caseId,currency,
            zero(in.actualSupplier()),zero(in.actualLogistics()),zero(in.actualCustoms()),zero(in.actualBank()),zero(in.actualLocal()),zero(in.actualOther()),
            zero(in.chargedSupplier()),zero(in.chargedLogistics()),zero(in.chargedCustoms()),zero(in.chargedBank()),zero(in.chargedLocal()),zero(in.chargedOther()),
            pct,zero(in.taxAmount()),zero(in.discountAmount()),in.exchangeRate(),formula,zero(in.calculatedTotal()),zero(in.grossProfit()),actor.id());
        return jdbc.queryForMap("SELECT * FROM case_pricing_workbooks WHERE case_id=?",caseId);
    }

    private void ensureCase(UUID id){Integer c=jdbc.queryForObject("SELECT COUNT(*) FROM workflow_cases WHERE id=?",Integer.class,id);if(c==null||c==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"پرونده پیدا نشد.");}
    private BigDecimal zero(BigDecimal v){return v==null?BigDecimal.ZERO:v;}
    private String defaultFormula(){return "chargedSupplier + chargedLogistics + chargedCustoms + chargedBank + chargedLocal + chargedOther + ((chargedSupplier + chargedLogistics + chargedCustoms + chargedBank + chargedLocal + chargedOther) * markupPercent / 100) + taxAmount - discountAmount";}
    private ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
}
