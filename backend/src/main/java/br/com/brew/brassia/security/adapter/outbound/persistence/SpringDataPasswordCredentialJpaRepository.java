package br.com.brew.brassia.security.adapter.outbound.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPasswordCredentialJpaRepository extends JpaRepository<PasswordCredentialJpaEntity, UUID> {
}
