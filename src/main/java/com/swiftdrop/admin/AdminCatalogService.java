package com.swiftdrop.admin;

import com.swiftdrop.admin.dto.PricingRuleRequest;
import com.swiftdrop.admin.dto.ZoneRequest;
import com.swiftdrop.audit.AuditService;
import com.swiftdrop.common.exception.ApiException;
import com.swiftdrop.pricing.PricingRule;
import com.swiftdrop.pricing.PricingRuleRepository;
import com.swiftdrop.pricing.Zone;
import com.swiftdrop.pricing.ZoneRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Admin use case "Manage delivery zones and pricing". */
@Service
public class AdminCatalogService {

    private final ZoneRepository zones;
    private final PricingRuleRepository rules;
    private final AuditService audit;

    public AdminCatalogService(ZoneRepository zones, PricingRuleRepository rules, AuditService audit) {
        this.zones = zones;
        this.rules = rules;
        this.audit = audit;
    }

    // ---------- zones ----------

    @Transactional(readOnly = true)
    public List<ZoneView> listZones() {
        return zones.findAll(Sort.by("name")).stream().map(ZoneView::from).toList();
    }

    @Transactional
    public ZoneView createZone(ZoneRequest request, UUID adminId) {
        Zone zone = new Zone();
        apply(zone, request);
        zones.save(zone);
        audit.record(adminId, "ZONE_CREATED", "ZONE", zone.getId(), null, zone.getName());
        return ZoneView.from(zone);
    }

    @Transactional
    public ZoneView updateZone(Integer id, ZoneRequest request, UUID adminId) {
        Zone zone = zones.findById(id).orElseThrow(() -> ApiException.notFound("Zone not found"));
        String old = zone.getName() + (zone.isActive() ? "" : " (inactive)");
        apply(zone, request);
        audit.record(adminId, "ZONE_UPDATED", "ZONE", zone.getId(), old,
                zone.getName() + (zone.isActive() ? "" : " (inactive)"));
        return ZoneView.from(zone);
    }

    private void apply(Zone zone, ZoneRequest request) {
        String name = request.name().trim();
        zones.findAll().stream()
                .filter(z -> z.getName().equalsIgnoreCase(name) && !z.getId().equals(zone.getId()))
                .findAny()
                .ifPresent(z -> {
                    throw ApiException.conflict("A zone with this name already exists");
                });
        zone.setName(name);
        zone.setDescription(request.description());
        if (request.active() != null) {
            zone.setActive(request.active());
        }
    }

    // ---------- pricing rules ----------

    @Transactional(readOnly = true)
    public List<PricingRuleView> listRules(Integer zoneId) {
        return rules.findAll(Sort.by("zone.name", "minWeightKg")).stream()
                .filter(r -> zoneId == null || r.getZone().getId().equals(zoneId))
                .map(PricingRuleView::from)
                .toList();
    }

    @Transactional
    public PricingRuleView createRule(PricingRuleRequest request, UUID adminId) {
        PricingRule rule = new PricingRule();
        apply(rule, request);
        rules.save(rule);
        audit.record(adminId, "PRICING_RULE_CREATED", "PRICING_RULE", rule.getId(), null, describe(rule));
        return PricingRuleView.from(rule);
    }

    @Transactional
    public PricingRuleView updateRule(Integer id, PricingRuleRequest request, UUID adminId) {
        PricingRule rule = rules.findById(id).orElseThrow(() -> ApiException.notFound("Pricing rule not found"));
        String old = describe(rule);
        apply(rule, request);
        audit.record(adminId, "PRICING_RULE_UPDATED", "PRICING_RULE", rule.getId(), old, describe(rule));
        return PricingRuleView.from(rule);
    }

    private void apply(PricingRule rule, PricingRuleRequest request) {
        if (request.maxWeightKg().compareTo(request.minWeightKg()) <= 0) {
            throw ApiException.badRequest("Maximum weight must be greater than minimum weight");
        }
        Zone zone = zones.findById(request.zoneId()).orElseThrow(() -> ApiException.badRequest("Zone not found"));
        boolean active = request.active() == null || request.active();

        // Two active rules of the same zone must not overlap, or the fee would be ambiguous.
        if (active) {
            rules.findByZoneIdAndActiveTrueOrderByMinWeightKgAsc(zone.getId()).stream()
                    .filter(r -> !r.getId().equals(rule.getId()))
                    .filter(r -> overlaps(r.getMinWeightKg(), r.getMaxWeightKg(),
                            request.minWeightKg(), request.maxWeightKg()))
                    .findAny()
                    .ifPresent(r -> {
                        throw ApiException.conflict("This weight band overlaps an existing rule ("
                                + r.getMinWeightKg() + " - " + r.getMaxWeightKg() + " kg)");
                    });
        }
        rule.setZone(zone);
        rule.setMinWeightKg(request.minWeightKg());
        rule.setMaxWeightKg(request.maxWeightKg());
        rule.setBaseFee(request.baseFee());
        rule.setPerKgFee(request.perKgFee());
        rule.setActive(active);
    }

    private static boolean overlaps(BigDecimal aMin, BigDecimal aMax, BigDecimal bMin, BigDecimal bMax) {
        return aMin.compareTo(bMax) < 0 && bMin.compareTo(aMax) < 0;
    }

    private static String describe(PricingRule r) {
        return r.getZone().getName() + " " + r.getMinWeightKg() + "-" + r.getMaxWeightKg() + "kg: "
                + r.getBaseFee() + " + " + r.getPerKgFee() + "/kg" + (r.isActive() ? "" : " (inactive)");
    }

    public record ZoneView(Integer id, String name, String description, boolean active) {
        static ZoneView from(Zone z) {
            return new ZoneView(z.getId(), z.getName(), z.getDescription(), z.isActive());
        }
    }

    public record PricingRuleView(Integer id, Integer zoneId, String zone, BigDecimal minWeightKg,
                                  BigDecimal maxWeightKg, BigDecimal baseFee, BigDecimal perKgFee, boolean active) {
        static PricingRuleView from(PricingRule r) {
            return new PricingRuleView(r.getId(), r.getZone().getId(), r.getZone().getName(), r.getMinWeightKg(),
                    r.getMaxWeightKg(), r.getBaseFee(), r.getPerKgFee(), r.isActive());
        }
    }
}
