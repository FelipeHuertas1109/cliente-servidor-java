package com.mycompany.chatserverproject.distributed;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HeartbeatSender {
    private static final String MULTICAST_ADDRESS = "230.0.0.0";
    private static final int MULTICAST_PORT = 4446;
    private final String serverId;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public HeartbeatSender(String serverId) {
        this.serverId = serverId;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(() -> sendHeartbeat(), 0, 5, TimeUnit.SECONDS);
    }

    private void sendHeartbeat() {
        try (DatagramSocket socket = new DatagramSocket()) {
            String msg = "HEARTBEAT:" + serverId + ";" + System.currentTimeMillis();
            byte[] buf = msg.getBytes();
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            DatagramPacket packet = new DatagramPacket(buf, buf.length, group, MULTICAST_PORT);
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
