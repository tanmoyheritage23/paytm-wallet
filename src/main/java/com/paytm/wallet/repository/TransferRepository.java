package com.paytm.wallet.repository;

import com.paytm.wallet.dto.TransferStatus;
import com.paytm.wallet.entity.TransferEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TransferRepository extends JpaRepository<TransferEntity, UUID> {
    Optional<TransferEntity> findByInitiatedByUserIdAndIdempotencyKey(String initiatedByUserId, String idempotencyKey);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        INSERT INTO transfers (initiated_by_user_id, idempotency_key, from_wallet_id, to_wallet_id, amount_paise, status, request_fingerprint)
        VALUES (:userId, :key, :from, :to, :amount, 'PENDING', :fingerprint)
        ON CONFLICT (initiated_by_user_id, idempotency_key) DO NOTHING
        """, nativeQuery = true)
    int claim(@Param("userId") String userId, @Param("key") String key, @Param("from") UUID from,
              @Param("to") UUID to, @Param("amount") long amount, @Param("fingerprint") String fingerprint);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE TransferEntity t SET t.status = :status, t.declineReason = :reason, t.completedAt = CURRENT_TIMESTAMP WHERE t.id = :id")
    int finish(@Param("id") UUID id, @Param("status") TransferStatus status, @Param("reason") String reason);
}
