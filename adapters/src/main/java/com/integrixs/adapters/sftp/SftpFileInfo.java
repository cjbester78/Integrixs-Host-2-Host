package com.integrixs.adapters.sftp;

import java.util.Date;

/**
 * Information about a file on the SFTP server
 */
public class SftpFileInfo {
    
    private String fileName;
    private String fullPath;
    private long size;
    private Date modifiedTime;
    private String permissions;
    private boolean isDirectory;
    private String owner;
    private String group;
    
    public SftpFileInfo() {}
    
    public SftpFileInfo(String fileName, String fullPath, long size, Date modifiedTime) {
        this.fileName = fileName;
        this.fullPath = fullPath;
        this.size = size;
        this.modifiedTime = modifiedTime;
    }
    
    // Getters and setters
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    
    public String getFullPath() { return fullPath; }
    public void setFullPath(String fullPath) { this.fullPath = fullPath; }
    
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    
    public Date getModifiedTime() { return modifiedTime; }
    public void setModifiedTime(Date modifiedTime) { this.modifiedTime = modifiedTime; }
    
    public String getPermissions() { return permissions; }
    public void setPermissions(String permissions) { this.permissions = permissions; }
    
    public boolean isDirectory() { return isDirectory; }
    public void setDirectory(boolean directory) { isDirectory = directory; }
    
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    
    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }
    
    // Helper methods
    public String getFormattedSize() {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024.0));
        return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
    }
    
    public String getFileExtension() {
        if (fileName == null) return null;
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1) : "";
    }
    
    @Override
    public String toString() {
        return String.format("SftpFileInfo{fileName='%s', size=%s, modified=%s, permissions='%s'}",
                fileName, getFormattedSize(), modifiedTime, permissions);
    }
}