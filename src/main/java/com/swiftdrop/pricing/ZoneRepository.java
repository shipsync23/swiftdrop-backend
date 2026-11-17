package com.swiftdrop.pricing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ZoneRepository extends JpaRepository<Zone, Integer> {

    List<Zone> findByActiveTrueOrderByNameAsc();
}
