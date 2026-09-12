package com.paytm.wallet.api;

import com.paytm.wallet.dto.ApiModels.TransferRequest;
import com.paytm.wallet.dto.ApiModels.TransferResponse;
import com.paytm.wallet.entity.TransferEntity;
import com.paytm.wallet.security.AuthFilter;
import com.paytm.wallet.service.TransferService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transfers")
public class TransferController {
    private final TransferService service;
    public TransferController(TransferService service) { 
        this.service = service; 
    }

    @PostMapping 
    public ResponseEntity<TransferResponse> create(@RequestAttribute(AuthFilter.USER_ID_ATTRIBUTE) String userId, @Valid @RequestBody TransferRequest request) {
        TransferService.Result result = service.create(userId, request);
        return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED).body(toResponse(result.transfer(), result.replay()));
    }

    @GetMapping("/{id}") 
    public TransferResponse get(@RequestAttribute(AuthFilter.USER_ID_ATTRIBUTE) String userId, @PathVariable UUID id) {
        return toResponse(service.getOwned(userId, id), null);
    }
    
    static TransferResponse toResponse(TransferEntity t, Boolean replay) {
        return new TransferResponse(t.getId(), t.getStatus(), t.getFromWalletId(), t.getToWalletId(), t.getAmountPaise(), t.getDeclineReason(), t.getCreatedAt(), replay);
    }
}
