package com.paytm.wallet.service;

import com.paytm.wallet.entity.WalletEntity;
import com.paytm.wallet.repository.WalletRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {
    private static final Logger log = LoggerFactory.getLogger(WalletService.class);
    private final WalletRepository wallets;
    private final MeterRegistry metrics;
    public WalletService(WalletRepository wallets, MeterRegistry metrics) { 
        this.wallets = wallets; 
        this.metrics = metrics; 
    }
    @Transactional
    public Result getOrCreate(String userId) {
        boolean created = wallets.insertIfAbsent(userId) == 1;
        WalletEntity wallet = wallets.findByUserId(userId).orElseThrow();
        log.atInfo().addKeyValue("event", "wallet_created").addKeyValue("wallet_id", wallet.getId())
                .addKeyValue("created", created).log("Wallet get-or-create completed");
        if (created) metrics.counter("wallet.created").increment();
        return new Result(wallet, created);
    }
    public WalletEntity getOwned(String userId, java.util.UUID id) {
        WalletEntity wallet = wallets.findById(id).orElseThrow(() -> new NotFoundException("wallet_not_found", "Wallet was not found"));
        if (!wallet.getUserId().equals(userId)) throw new ForbiddenException("wallet_forbidden", "You do not own this wallet");
        return wallet;
    }
    public record Result(WalletEntity wallet, boolean created) { }
}
