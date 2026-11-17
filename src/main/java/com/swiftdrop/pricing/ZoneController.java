package com.swiftdrop.pricing;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** GET /api/v1/zones - active delivery zones for the booking form dropdown (public). */
@RestController
@RequestMapping("/api/v1/zones")
public class ZoneController {

    private final ZoneRepository zones;

    public ZoneController(ZoneRepository zones) {
        this.zones = zones;
    }

    @GetMapping
    public List<ZoneResponse> list() {
        return zones.findByActiveTrueOrderByNameAsc().stream()
                .map(z -> new ZoneResponse(z.getId(), z.getName(), z.getDescription()))
                .toList();
    }

    public record ZoneResponse(Integer id, String name, String description) {
    }
}
