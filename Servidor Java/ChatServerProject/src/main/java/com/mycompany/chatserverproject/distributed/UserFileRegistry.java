// UserFileRegistry.java
package com.mycompany.chatserverproject.distributed;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class UserFileRegistry {
    private final CopyOnWriteArrayList<UserInfo> users = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<FileInfo> files = new CopyOnWriteArrayList<>();

    public List<UserInfo> getAllUsers() {
        return users;
    }

    public List<FileInfo> getAllFiles() {
        return files;
    }

    public void integrateFullDump(FullDump dump) {
        users.clear();
        users.addAll(dump.getUsers());
        files.clear();
        files.addAll(dump.getFiles());
    }

    public void applyDiff(Diff d) {
        switch (d.getType()) {
            case USER_ADDED:
                if (d.getUserInfo() != null && !users.contains(d.getUserInfo())) {
                    users.add(d.getUserInfo());
                }
                break;
            case USER_REMOVED:
                if (d.getUserInfo() != null) {
                    users.remove(d.getUserInfo());
                }
                break;
            case FILE_ADDED:
                if (d.getFileInfo() != null && !files.contains(d.getFileInfo())) {
                    files.add(d.getFileInfo());
                }
                break;
            case FILE_REMOVED:
                if (d.getFileInfo() != null) {
                    files.remove(d.getFileInfo());
                }
                break;
            default:
                // No-op
        }
    }
}
