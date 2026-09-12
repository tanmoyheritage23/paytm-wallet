package com.paytm.wallet.service;
public class ForbiddenException extends RuntimeException { 
    public final String code; 
    public ForbiddenException(String code, String message) { 
        super(message); 
        this.code = code; 
    } 
}
