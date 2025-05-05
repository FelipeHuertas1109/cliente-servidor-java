package com.mycompany.chatserverproject.distributed;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HeartbeatReceiver {
    private static final String MULTICAST_ADDRESS = "230.0.0.0";
    private static final int MULTICAST_PORT = 4446;
    private static final long TIMEOUT_MS = 15_000;

    // serverId -> última marca de tiempo
    private final Map<String, Long> lastSeen = new ConcurrentHashMap<>();
    // serverId -> host IP
    private final Map<String, String> liveServers = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor();

    public void start() {
        listen();
        scheduler.scheduleAtFixedRate(this::checkTimeouts, TIMEOUT_MS, TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private void listen() {
        new Thread(() -> {
            try (MulticastSocket socket = new MulticastSocket(MULTICAST_PORT)) {
                InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
                socket.joinGroup(group);

                byte[] buf = new byte[256];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);

                    String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    String host = packet.getAddress().getHostAddress();
                    handleHeartbeat(msg, host);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "HeartbeatReceiver").start();
    }

    private void handleHeartbeat(String raw, String host) {
        if (raw == null) return;

        // Buscamos el prefijo "HEARTBEAT"
        int idx = raw.indexOf("HEARTBEAT");
        if (idx < 0) return;
        String msg = raw.substring(idx);

        // Separar por ':' o ';'
        String[] parts = msg.split("[:;]");
        if (parts.length < 3) return;

        String peerId = parts[1];
        long ts;
        try {
            ts = Long.parseLong(parts[2]);
        } catch (NumberFormatException ex) {
            return;
        }

        lastSeen.put(peerId, ts);
        liveServers.put(peerId, host);
    }

    private void checkTimeouts() {
        long now = System.currentTimeMillis();
        for (String peerId : lastSeen.keySet()) {
            Long ts = lastSeen.get(peerId);
            if (ts == null || now - ts > TIMEOUT_MS) {
                lastSeen.remove(peerId);
                liveServers.remove(peerId);
            }
        }
    }

    /** @return Mapa serverId -> host IP de servidores vivos. */
    public Map<String, String> getLiveServers() {
        return liveServers;
    }
}
