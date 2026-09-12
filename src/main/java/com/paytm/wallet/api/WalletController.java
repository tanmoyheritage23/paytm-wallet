package com.paytm.wallet.api;

import com.paytm.wallet.dto.ApiModels.WalletResponse;
import com.paytm.wallet.entity.WalletEntity;
import com.paytm.wallet.security.AuthFilter;
import com.paytm.wallet.service.WalletService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/wallets")
public class WalletController {
    private final WalletService service;
    public WalletController(WalletService service) { 
        this.service = service; 
    }
    @PostMapping 
    public ResponseEntity<WalletResponse> create(@RequestAttribute(AuthFilter.USER_ID_ATTRIBUTE) String userId) {
        WalletService.Result result = service.getOrCreate(userId);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(toResponse(result.wallet(), result.created()));
    }
    @GetMapping("/{id}") 
    public WalletResponse get(@RequestAttribute(AuthFilter.USER_ID_ATTRIBUTE) String userId, @PathVariable UUID id) {
        return toResponse(service.getOwned(userId, id), null);
    }
    static WalletResponse toResponse(WalletEntity wallet, Boolean created) { 
        return new WalletResponse(wallet.getId(), wallet.getUserId(), wallet.getBalancePaise(), created); 
    }
}
