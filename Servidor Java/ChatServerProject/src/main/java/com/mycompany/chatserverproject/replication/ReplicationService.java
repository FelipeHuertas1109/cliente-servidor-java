package com.mycompany.chatserverproject.replication;

import com.mycompany.chatserverproject.connector.ServerConnector;
import com.mycompany.chatserverproject.model.PeerInfo;
import com.mycompany.chatserverproject.protocol.MessageType;
import com.mycompany.chatserverproject.persistence.DatabaseService;
import com.mycompany.chatserverproject.model.User;
import java.util.List;

public class ReplicationService {
    private final ServerConnector connector;
    private final DatabaseService db;

    public ReplicationService(ServerConnector connector, DatabaseService db) {
        this.connector = connector;
        this.db        = db;
    }

    public void syncNewPeer(PeerInfo peer) {
        connector.sendMessage(peer, MessageType.SYNC_USERS, "");
        connector.sendMessage(peer, MessageType.SYNC_FILES, "");
    }

    public void broadcastUserRegistration(User u, List<PeerInfo> peers) {
        for (PeerInfo p : connector.getAlivePeers(peers)) {
            connector.sendMessage(p, MessageType.USER_REGISTER, u.toJson());
        }
    }
}
