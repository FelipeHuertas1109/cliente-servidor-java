package com.mycompany.chatserverproject.discovery;

import com.mycompany.chatserverproject.ChatServer;
import com.mycompany.chatserverproject.model.PeerInfo;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.*;

public class ServerDiscoveryService {
    private final List<PeerInfo>           peers;
    private final ChatServer               server;
    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor();

    public ServerDiscoveryService(List<PeerInfo> peers, ChatServer server) {
        this.peers  = peers;
        this.server = server;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::checkAll, 0, 10, TimeUnit.SECONDS);
    }

    private void checkAll() {
        for (PeerInfo p : peers) {
            boolean up = ping(p);
            if (up != p.isAlive()) {
                p.setAlive(up);
                server.onPeerStatusChange(p);
            }
        }
    }

    private boolean ping(PeerInfo p) {
        try (Socket s = new Socket(p.getHost(), p.getPort())) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
