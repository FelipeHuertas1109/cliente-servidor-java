package com.mycompany.chatserverproject.connector;

import com.mycompany.chatserverproject.model.PeerInfo;
import com.mycompany.chatserverproject.protocol.MessageType;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ServerConnector {
    private final Map<PeerInfo, Socket> connections = new ConcurrentHashMap<>();

    public synchronized Socket getOrCreate(PeerInfo p) throws IOException {
        if (!connections.containsKey(p) || connections.get(p).isClosed()) {
            Socket s = new Socket(p.getHost(), p.getPort());
            connections.put(p, s);
        }
        return connections.get(p);
    }

    public void sendMessage(PeerInfo p, MessageType type, String payload) {
        try {
            Socket s = getOrCreate(p);
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            out.writeUTF(type.name() + "|" + payload);
        } catch (IOException e) {
            // log o reintento
        }
    }

    public List<PeerInfo> getAlivePeers(List<PeerInfo> all) {
        List<PeerInfo> alive = new ArrayList<>();
        for (PeerInfo p : all) if (p.isAlive()) alive.add(p);
        return alive;
    }
}
