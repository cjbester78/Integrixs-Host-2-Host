package com.integrixs.shared.dto;

/**
 * Request DTO for bank operations
 */
public class BankOperationRequest {
    private String bankName;
    private String operationType; // payment, audit, pop
    private String direction; // upload, download
    private int delaySeconds; // optional delay for upload-download operations

    public BankOperationRequest() {}

    public BankOperationRequest(String bankName, String operationType, String direction) {
        this.bankName = bankName;
        this.operationType = operationType;
        this.direction = direction;
    }

    public BankOperationRequest(String bankName, String operationType, String direction, int delaySeconds) {
        this.bankName = bankName;
        this.operationType = operationType;
        this.direction = direction;
        this.delaySeconds = delaySeconds;
    }

    // Getters and setters
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public int getDelaySeconds() { return delaySeconds; }
    public void setDelaySeconds(int delaySeconds) { this.delaySeconds = delaySeconds; }

    @Override
    public String toString() {
        return String.format("BankOperationRequest{bank='%s', type='%s', direction='%s', delay=%ds}", 
                bankName, operationType, direction, delaySeconds);
    }
}