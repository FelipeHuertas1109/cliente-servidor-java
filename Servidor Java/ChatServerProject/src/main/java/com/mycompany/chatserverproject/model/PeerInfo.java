package com.mycompany.chatserverproject.model;

public class PeerInfo {
    private String host;
    private int    port;
    private boolean alive;

    public PeerInfo() {}

    public PeerInfo(String host, int port) {
        this.host  = host;
        this.port  = port;
        this.alive = false;
    }

    public String getHost()     { return host; }
    public int    getPort()     { return port; }
    public boolean isAlive()    { return alive; }
    public void   setAlive(boolean alive) { this.alive = alive; }

    @Override
    public String toString() {
        return host + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PeerInfo)) return false;
        PeerInfo p = (PeerInfo) o;
        return port == p.port && host.equals(p.host);
    }

    @Override
    public int hashCode() {
        return host.hashCode() * 31 + port;
    }
}
