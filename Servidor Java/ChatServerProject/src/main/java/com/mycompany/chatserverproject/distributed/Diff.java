// Diff.java
package com.mycompany.chatserverproject.distributed;

public class Diff {
    public enum Type { USER_ADDED, USER_REMOVED, FILE_ADDED, FILE_REMOVED }

    private Type type;
    private UserInfo userInfo;
    private FileInfo fileInfo;

    public Diff() {}

    // Constructor para usuario
    public Diff(Type type, UserInfo userInfo) {
        this.type = type;
        this.userInfo = userInfo;
    }

    // Constructor para archivo
    public Diff(Type type, FileInfo fileInfo) {
        this.type = type;
        this.fileInfo = fileInfo;
    }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public UserInfo getUserInfo() { return userInfo; }
    public void setUserInfo(UserInfo userInfo) { this.userInfo = userInfo; }

    public FileInfo getFileInfo() { return fileInfo; }
    public void setFileInfo(FileInfo fileInfo) { this.fileInfo = fileInfo; }
}
