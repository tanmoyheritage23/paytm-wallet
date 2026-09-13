package com.paytm.wallet.service;

import com.paytm.wallet.dto.ApiModels.TransferRequest;
import com.paytm.wallet.dto.TransferStatus;
import com.paytm.wallet.entity.TransferEntity;
import com.paytm.wallet.entity.WalletEntity;
import com.paytm.wallet.repository.TransferRepository;
import com.paytm.wallet.repository.WalletRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {
    private static final Logger log = LoggerFactory.getLogger(TransferService.class);
    private final WalletRepository wallets;
    private final TransferRepository transfers;
    private final MeterRegistry metrics;
    public TransferService(WalletRepository wallets, TransferRepository transfers, MeterRegistry metrics) {
        this.wallets = wallets; 
        this.transfers = transfers; 
        this.metrics = metrics;
    }

    @Transactional
    public Result create(String userId, TransferRequest request) {
        if (request.from().equals(request.to())) { 
            throw new BadRequestException("self_transfer", "Source and destination wallets must differ");
        }
        WalletEntity source = wallets.findById(request.from()).orElseThrow(() -> new BadRequestException("unknown_wallet", "Source wallet does not exist"));
        if (!source.getUserId().equals(userId)) throw new ForbiddenException("source_wallet_forbidden", "You do not own the source wallet");
        wallets.findById(request.to()).orElseThrow(() -> new BadRequestException("unknown_wallet", "Destination wallet does not exist"));
        String fingerprint = sha256(request.from() + "|" + request.to() + "|" + request.amount_paise());
        boolean claimed = transfers.claim(userId, request.idempotency_key(), request.from(), request.to(), request.amount_paise(), fingerprint) == 1;
        if (!claimed) {
            TransferEntity existing = transfers.findByInitiatedByUserIdAndIdempotencyKey(userId, request.idempotency_key()).orElseThrow();
            if (!existing.getRequestFingerprint().equals(fingerprint)) throw new ConflictException("idempotency_key_conflict", "This idempotency key was already used with a different request");
            metrics.counter("wallet.transfers.idempotent_replay").increment();
            log.atInfo().addKeyValue("event", "idempotent_replay_hit").addKeyValue("transfer_id", existing.getId()).log("Idempotent replay");
            return new Result(existing, true);
        }
        TransferEntity claimedTransfer = transfers.findByInitiatedByUserIdAndIdempotencyKey(userId, request.idempotency_key()).orElseThrow();
        UUID id = claimedTransfer.getId();
        boolean sourceFirst = request.from().compareTo(request.to()) < 0;
        boolean debited;
        if (sourceFirst) {
            debited = wallets.debit(request.from(), request.amount_paise()) == 1;
            if (debited) creditOrFail(request.to(), request.amount_paise());
        } else {
            // Touch the lower UUID first. If debit subsequently fails, compensate before recording a decline.
            creditOrFail(request.to(), request.amount_paise());
            debited = wallets.debit(request.from(), request.amount_paise()) == 1;
            if (!debited) {
                int restored = wallets.debit(request.to(), request.amount_paise());
                if (restored != 1) throw new IllegalStateException("Could not compensate destination credit");
            }
        }
        if (debited) {
            transfers.finish(id, TransferStatus.COMPLETED, null);
            metrics.counter("wallet.transfers").increment();
            log.atInfo().addKeyValue("event", "wallet_debited").addKeyValue("transfer_id", id)
                    .addKeyValue("wallet_id", request.from()).addKeyValue("amount_paise", request.amount_paise()).log("Wallet debited");
            log.atInfo().addKeyValue("event", "wallet_credited").addKeyValue("transfer_id", id)
                    .addKeyValue("wallet_id", request.to()).addKeyValue("amount_paise", request.amount_paise()).log("Wallet credited");
            log.atInfo().addKeyValue("event", "transfer_completed").addKeyValue("transfer_id", id)
                    .addKeyValue("amount_paise", request.amount_paise()).log("Transfer completed");
        } else {
            transfers.finish(id, TransferStatus.DECLINED, "INSUFFICIENT_FUNDS");
            metrics.counter("wallet.transfers.declined", "reason", "INSUFFICIENT_FUNDS").increment();
            log.atInfo().addKeyValue("event", "transfer_declined").addKeyValue("transfer_id", id)
                    .addKeyValue("reason", "INSUFFICIENT_FUNDS").log("Transfer declined");
        }
        TransferEntity transfer = transfers.findById(id).orElseThrow();
        return new Result(transfer, false);
    }
    public TransferEntity getOwned(String userId, UUID id) {
        TransferEntity transfer = transfers.findById(id).orElseThrow(() -> new NotFoundException("transfer_not_found", "Transfer was not found"));
        if (!transfer.getInitiatedByUserId().equals(userId)) throw new ForbiddenException("transfer_forbidden", "You did not initiate this transfer");
        return transfer;
    }
    private void creditOrFail(UUID id, long amount) { 
        if (wallets.credit(id, amount) != 1) throw new IllegalStateException("Destination wallet disappeared"); 
    }
    private static String sha256(String input) {
        try { 
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); 
        }
        catch (NoSuchAlgorithmException e) { 
            throw new IllegalStateException(e); 
        }
    }

    public record Result(TransferEntity transfer, boolean replay) { }
}
