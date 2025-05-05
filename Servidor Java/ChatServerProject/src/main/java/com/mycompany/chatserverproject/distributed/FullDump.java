// FullDump.java
package com.mycompany.chatserverproject.distributed;

import java.util.List;

public class FullDump {
    private List<UserInfo> users;
    private List<FileInfo> files;

    public FullDump() {}

    public FullDump(List<UserInfo> users, List<FileInfo> files) {
        this.users = users;
        this.files  = files;
    }

    public List<UserInfo> getUsers() { return users; }
    public void setUsers(List<UserInfo> users) { this.users = users; }

    public List<FileInfo> getFiles() { return files; }
    public void setFiles(List<FileInfo> files) { this.files = files; }
}
