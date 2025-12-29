package com.integrixs.core.logging;

import org.slf4j.MDC;
import java.util.UUID;

/**
 * Thread-local correlation context for tracking operations across logs
 */
public class CorrelationContext {
    
    private static final ThreadLocal<String> correlationId = new ThreadLocal<>();
    private static final ThreadLocal<String> operationId = new ThreadLocal<>();
    private static final ThreadLocal<String> bankName = new ThreadLocal<>();
    private static final ThreadLocal<String> sessionId = new ThreadLocal<>();
    private static final ThreadLocal<String> executionId = new ThreadLocal<>();
    private static final ThreadLocal<String> flowId = new ThreadLocal<>();
    private static final ThreadLocal<String> flowName = new ThreadLocal<>();
    private static final ThreadLocal<String> adapterId = new ThreadLocal<>();
    private static final ThreadLocal<String> adapterName = new ThreadLocal<>();
    
    public static void setCorrelationId(String id) {
        correlationId.set(id);
        MDC.put("correlationId", id);
    }
    
    public static String getCorrelationId() {
        String id = correlationId.get();
        if (id == null) {
            id = generateCorrelationId();
            correlationId.set(id);
        }
        return id;
    }
    
    public static void setOperationId(String id) {
        operationId.set(id);
    }
    
    public static String getOperationId() {
        return operationId.get();
    }
    
    public static void setBankName(String name) {
        bankName.set(name);
    }
    
    public static String getBankName() {
        return bankName.get();
    }
    
    public static void setSessionId(String id) {
        sessionId.set(id);
    }
    
    public static String getSessionId() {
        return sessionId.get();
    }
    
    public static void setExecutionId(String id) {
        executionId.set(id);
        MDC.put("executionId", id);
    }
    
    public static String getExecutionId() {
        return executionId.get();
    }
    
    public static void setFlowId(String id) {
        flowId.set(id);
        MDC.put("flowId", id);
    }
    
    public static String getFlowId() {
        return flowId.get();
    }
    
    public static void setFlowName(String name) {
        flowName.set(name);
        MDC.put("flowName", name);
    }
    
    public static String getFlowName() {
        return flowName.get();
    }
    
    public static void setAdapterId(String id) {
        adapterId.set(id);
        MDC.put("adapterId", id);
    }
    
    public static String getAdapterId() {
        return adapterId.get();
    }
    
    public static void setAdapterName(String name) {
        adapterName.set(name);
        MDC.put("adapterName", name);
    }
    
    public static String getAdapterName() {
        return adapterName.get();
    }
    
    public static void clear() {
        correlationId.remove();
        operationId.remove();
        bankName.remove();
        sessionId.remove();
        messageId.remove();
        executionId.remove();
        flowId.remove();
        flowName.remove();
        adapterId.remove();
        adapterName.remove();
        
        // Clear MDC as well
        MDC.clear();
    }
    
    public static String generateCorrelationId() {
        return "CORR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    public static String generateOperationId() {
        return "OP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    public static String generateSessionId() {
        return "SES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    /**
     * Generate enterprise-style message ID for flow execution traceability
     */
    public static String generateMessageId() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String timestamp = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String sequence = String.format("%03d", (int)(Math.random() * 1000));
        return "MSG-" + timestamp + "-" + sequence;
    }
    
    private static final ThreadLocal<String> messageId = new ThreadLocal<>();
    
    public static void setMessageId(String id) {
        messageId.set(id);
    }
    
    public static String getMessageId() {
        String id = messageId.get();
        if (id == null) {
            id = generateMessageId();
            messageId.set(id);
        }
        return id;
    }
    
    public static String getContextInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        
        String corrId = getCorrelationId();
        if (corrId != null) {
            sb.append("corr=").append(corrId);
        }
        
        String opId = getOperationId();
        if (opId != null) {
            if (sb.length() > 1) sb.append(", ");
            sb.append("op=").append(opId);
        }
        
        String bank = getBankName();
        if (bank != null) {
            if (sb.length() > 1) sb.append(", ");
            sb.append("bank=").append(bank);
        }
        
        String sesId = getSessionId();
        if (sesId != null) {
            if (sb.length() > 1) sb.append(", ");
            sb.append("ses=").append(sesId);
        }
        
        String msgId = getMessageId();
        if (msgId != null) {
            if (sb.length() > 1) sb.append(", ");
            sb.append("msg=").append(msgId);
        }
        
        String execId = getExecutionId();
        if (execId != null) {
            if (sb.length() > 1) sb.append(", ");
            sb.append("exec=").append(execId);
        }
        
        String flwId = getFlowId();
        if (flwId != null) {
            if (sb.length() > 1) sb.append(", ");
            sb.append("flow=").append(flwId);
        }
        
        String flwName = getFlowName();
        if (flwName != null) {
            if (sb.length() > 1) sb.append(", ");
            sb.append("flowName=").append(flwName);
        }
        
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * Set up context for a new operation
     */
    public static void initializeOperation(String bankName, String operationType) {
        setCorrelationId(generateCorrelationId());
        setOperationId(generateOperationId());
        setBankName(bankName);
    }
    
    /**
     * Copy context from current thread to new thread
     */
    public static ContextSnapshot captureContext() {
        return new ContextSnapshot(
            getCorrelationId(),
            getOperationId(),
            getBankName(),
            getSessionId(),
            getMessageId(),
            getExecutionId(),
            getFlowId(),
            getFlowName()
        );
    }
    
    /**
     * Restore context in current thread
     */
    public static void restoreContext(ContextSnapshot snapshot) {
        if (snapshot != null) {
            setCorrelationId(snapshot.correlationId);
            setOperationId(snapshot.operationId);
            setBankName(snapshot.bankName);
            setSessionId(snapshot.sessionId);
            setMessageId(snapshot.messageId);
            setExecutionId(snapshot.executionId);
            setFlowId(snapshot.flowId);
            setFlowName(snapshot.flowName);
        }
    }
    
    /**
     * Immutable snapshot of correlation context
     */
    public static class ContextSnapshot {
        public final String correlationId;
        public final String operationId;
        public final String bankName;
        public final String sessionId;
        public final String messageId;
        public final String executionId;
        public final String flowId;
        public final String flowName;
        
        public ContextSnapshot(String correlationId, String operationId, String bankName, String sessionId, 
                             String messageId, String executionId, String flowId, String flowName) {
            this.correlationId = correlationId;
            this.operationId = operationId;
            this.bankName = bankName;
            this.sessionId = sessionId;
            this.messageId = messageId;
            this.executionId = executionId;
            this.flowId = flowId;
            this.flowName = flowName;
        }
    }
}