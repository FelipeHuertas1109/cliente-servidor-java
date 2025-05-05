// UserInfo.java
package com.mycompany.chatserverproject.distributed;

import java.util.Objects;

public class UserInfo {
    private String username;
    private String serverId;

    public UserInfo() {}

    public UserInfo(String username, String serverId) {
        this.username = username;
        this.serverId = serverId;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserInfo)) return false;
        UserInfo that = (UserInfo) o;
        return Objects.equals(username, that.username) &&
               Objects.equals(serverId, that.serverId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, serverId);
    }
}
