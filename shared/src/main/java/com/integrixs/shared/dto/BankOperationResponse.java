package com.integrixs.shared.dto;

import java.time.LocalDateTime;

/**
 * Response DTO for bank operations
 */
public class BankOperationResponse {
    private String bankName;
    private boolean success;
    private String message;
    private LocalDateTime timestamp;
    private Object data;

    public BankOperationResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public BankOperationResponse(String bankName, boolean success, String message) {
        this.bankName = bankName;
        this.success = success;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    public BankOperationResponse(String bankName, boolean success, String message, Object data) {
        this.bankName = bankName;
        this.success = success;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }

    // Static factory methods
    public static BankOperationResponse success(String bankName, String message, Object data) {
        return new BankOperationResponse(bankName, true, message, data);
    }

    public static BankOperationResponse error(String bankName, String message) {
        return new BankOperationResponse(bankName, false, message);
    }

    // Getters and setters
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }


    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }

    @Override
    public String toString() {
        return String.format("BankOperationResponse{bank='%s', success=%s, message='%s', timestamp=%s}", 
                bankName, success, message, timestamp);
    }
}