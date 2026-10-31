package com.securepay.security;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TokenRevocationServiceTest {
    private final RevokedTokenRepository repository = mock(RevokedTokenRepository.class);
    private final TokenRevocationService service = new TokenRevocationService(repository);

    @Test
    void storesOnlyTheSha256TokenIdAndItsExpiry() {
        Instant expiry = Instant.parse("2030-01-01T00:00:00Z");
        when(repository.existsById(anyString())).thenReturn(false);
        service.revoke("opaque-random-jti", expiry);

        ArgumentCaptor<RevokedTokenEntity> captor = ArgumentCaptor.forClass(RevokedTokenEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().tokenIdHash()).hasSize(64).isNotEqualTo("opaque-random-jti");
        assertThat(captor.getValue().expiresAt()).isEqualTo(expiry);
    }

    @Test
    void repeatedRevocationDoesNotCreateDuplicateRows() {
        when(repository.existsById(anyString())).thenReturn(true);
        service.revoke("already-revoked", Instant.now().plusSeconds(60));
        verify(repository, never()).save(any());
    }

    @Test
    void expiredRowsCanBeRemovedByTheScheduledCleanup() {
        service.removeExpiredRevocations();
        verify(repository).deleteByExpiresAtLessThanEqual(any(Instant.class));
    }
}
