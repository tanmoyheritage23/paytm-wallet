package com.paytm.wallet.entity;

import com.paytm.wallet.dto.TransferStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transfers")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransferEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "initiated_by_user_id", nullable = false) private String initiatedByUserId;
    @Column(name = "idempotency_key", nullable = false) private String idempotencyKey;
    @Column(name = "from_wallet_id", nullable = false) private UUID fromWalletId;
    @Column(name = "to_wallet_id", nullable = false) private UUID toWalletId;
    @Column(name = "amount_paise", nullable = false) private long amountPaise;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TransferStatus status;
    @Column(name = "decline_reason") private String declineReason;
    @Column(name = "request_fingerprint", nullable = false) private String requestFingerprint;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "completed_at") private Instant completedAt;
}
