package br.com.brew.brassia.brewery.adapter.outbound.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataBreweryJpaRepository extends JpaRepository<BreweryJpaEntity, UUID> {
    boolean existsByCode(String code);
}
