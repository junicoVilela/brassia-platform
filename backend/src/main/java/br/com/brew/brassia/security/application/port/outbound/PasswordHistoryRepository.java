package br.com.brew.brassia.security.application.port.outbound;

import br.com.brew.brassia.security.domain.PasswordCredential;
import br.com.brew.brassia.security.domain.UserId;
import java.util.List;

/** Histórico de credenciais de senha, para barrar reutilização recente. */
public interface PasswordHistoryRepository {
    void save(PasswordCredential replaced);
    /** Hashes das últimas {@code limit} senhas do usuário (mais recentes primeiro). */
    List<String> recentHashes(UserId userId, int limit);
}
