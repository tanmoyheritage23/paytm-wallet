package com.paytm.wallet.service;
public class BadRequestException extends RuntimeException { 
    public final String code; 
    public BadRequestException(String code, String message) { 
        super(message); 
        this.code = code; 
    } 
}
