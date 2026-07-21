package br.com.brew.brassia.security.adapter.outbound.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAccountTokenJpaRepository extends JpaRepository<AccountTokenJpaEntity, UUID> {
    Optional<AccountTokenJpaEntity> findByTokenHashAndTokenType(String tokenHash, String tokenType);
}
