package com.goldpricemonitor.api;
import org.springframework.http.HttpStatus;
public class ApiFault extends RuntimeException {
    public final HttpStatus status;
    public final String code;
    public ApiFault(HttpStatus status,String code,String message){super(message);this.status=status;this.code=code;}
}
