package com.paytm.wallet.repository;

import com.paytm.wallet.entity.WalletEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<WalletEntity, UUID> {
    Optional<WalletEntity> findByUserId(String userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO wallets (user_id, balance_paise) VALUES (:userId, 0) ON CONFLICT (user_id) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("userId") String userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE WalletEntity w SET w.balancePaise = w.balancePaise - :amount, w.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE w.id = :id AND w.balancePaise >= :amount")
    int debit(@Param("id") UUID id, @Param("amount") long amount);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE WalletEntity w SET w.balancePaise = w.balancePaise + :amount, w.updatedAt = CURRENT_TIMESTAMP WHERE w.id = :id")
    int credit(@Param("id") UUID id, @Param("amount") long amount);
}
