package com.paytm.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.UUID;

public final class ApiModels {
    private ApiModels() { }
    public record WalletResponse(UUID wallet_id, String user_id, long balance_paise, Boolean created) { }
    public record TransferRequest(@NotNull UUID from, @NotNull UUID to, @Positive long amount_paise,
                                  @NotBlank String idempotency_key) { }
    public record TransferResponse(UUID transfer_id, TransferStatus status, UUID from, UUID to, long amount_paise,
                                   String decline_reason, Instant created_at, Boolean idempotent_replay) { }
    public record ErrorResponse(String error, String message) { }
}
