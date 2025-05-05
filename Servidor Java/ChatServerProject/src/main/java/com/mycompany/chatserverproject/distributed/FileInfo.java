// FileInfo.java
package com.mycompany.chatserverproject.distributed;

import java.util.Objects;

public class FileInfo {
    private String filename;
    private String serverId;
    private String checksum;

    public FileInfo() {}

    public FileInfo(String filename, String serverId, String checksum) {
        this.filename = filename;
        this.serverId = serverId;
        this.checksum = checksum;
    }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }

    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileInfo)) return false;
        FileInfo that = (FileInfo) o;
        return Objects.equals(filename, that.filename) &&
               Objects.equals(serverId, that.serverId) &&
               Objects.equals(checksum, that.checksum);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filename, serverId, checksum);
    }
}
