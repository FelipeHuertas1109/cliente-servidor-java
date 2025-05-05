package com.mycompany.chatserverproject.distributed;

public class SyncRequest {
    public enum Type { FULL_DUMP, DIFF }
    private Type type;
    public SyncRequest() {}
    public SyncRequest(Type type) { this.type = type; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
}
