package com.integrixs.adapters.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;

import java.time.LocalDateTime;

/**
 * Represents an SFTP connection with session management
 */
public class SftpConnection {
    
    private final Session session;
    private final ChannelSftp channel;
    private final String bankName;
    private final LocalDateTime createdTime;
    private LocalDateTime lastUsedTime;
    private boolean inUse;
    
    public SftpConnection(Session session, ChannelSftp channel, String bankName) {
        this.session = session;
        this.channel = channel;
        this.bankName = bankName;
        this.createdTime = LocalDateTime.now();
        this.lastUsedTime = LocalDateTime.now();
        this.inUse = false;
    }
    
    public Session getSession() {
        return session;
    }
    
    public ChannelSftp getChannel() {
        updateLastUsed();
        return channel;
    }
    
    public String getBankName() {
        return bankName;
    }
    
    public LocalDateTime getCreatedTime() {
        return createdTime;
    }
    
    public LocalDateTime getLastUsedTime() {
        return lastUsedTime;
    }
    
    public boolean isInUse() {
        return inUse;
    }
    
    public void setInUse(boolean inUse) {
        this.inUse = inUse;
        updateLastUsed();
    }
    
    public boolean isConnected() {
        return session != null && session.isConnected() && 
               channel != null && channel.isConnected();
    }
    
    public void disconnect() {
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }
    
    private void updateLastUsed() {
        this.lastUsedTime = LocalDateTime.now();
    }
    
    @Override
    public String toString() {
        return String.format("SftpConnection{bank='%s', connected=%s, inUse=%s, created=%s}", 
                bankName, isConnected(), inUse, createdTime);
    }
}