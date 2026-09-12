package com.paytm.wallet.service;
public class NotFoundException extends RuntimeException { 
    public final String code; 
    public NotFoundException(String code, String message) { 
        super(message); 
        this.code = code; 
    } 
}
