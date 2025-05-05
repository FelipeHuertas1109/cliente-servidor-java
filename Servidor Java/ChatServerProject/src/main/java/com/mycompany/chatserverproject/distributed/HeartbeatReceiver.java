package com.mycompany.chatserverproject.distributed;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.InetSocketAddress;
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

    // Mapa de servidorId → última marca de tiempo recibida
    private final Map<String, Long> lastSeen = new ConcurrentHashMap<>();
    // Mapa de servidorId → dirección (host + puerto)
    private final Map<String, InetSocketAddress> liveServers = new ConcurrentHashMap<>();

    // Para limpiar dead peers periódicamente
    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor();

    public HeartbeatReceiver() {}

    /**
     * Arranca el listener de heartbeats y el scheduler de timeouts.
     */
    public void start() {
        Thread listener = new Thread(this::listenLoop, "HeartbeatReceiver");
        listener.setDaemon(true);
        listener.start();

        // Cada TIMEOUT_MS ms, comprobamos qué peers pasaron su timeout
        scheduler.scheduleAtFixedRate(
            this::checkTimeouts,
            TIMEOUT_MS,
            TIMEOUT_MS,
            TimeUnit.MILLISECONDS
        );
    }

    /**
     * Bucle principal: se une al grupo multicast y recibe los paquetes UDP.
     */
    private void listenLoop() {
        try (MulticastSocket socket = new MulticastSocket(MULTICAST_PORT)) {
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            socket.joinGroup(group);

            byte[] buf = new byte[512];
            while (!Thread.currentThread().isInterrupted()) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                String msg = new String(
                    packet.getData(),
                    0,
                    packet.getLength(),
                    StandardCharsets.UTF_8
                );
                handleHeartbeat(msg, packet.getAddress().getHostAddress(), packet.getPort());
            }

            socket.leaveGroup(group);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Parseo del mensaje y actualización de liveServers y lastSeen.
     *
     * Mensaje esperado: "HEARTBEAT;<serverId>;<timestamp>"
     */
    private void handleHeartbeat(String raw, String host, int port) {
    if (raw == null) return;

    // 1) Encuentra dónde empieza realmente el mensaje
    int idx = raw.indexOf("HEARTBEAT");
    if (idx < 0) return;  // no es un heartbeat válido

    String msg = raw.substring(idx);

    // 2) Separa tanto por ':' como por ';'
    String[] parts = msg.split("[:;]");
    if (parts.length < 3) return;

    String peerId = parts[1];
    long ts;
    try {
        ts = Long.parseLong(parts[2]);
    } catch (NumberFormatException e) {
        return;
    }

    // 3) Actualiza tu mapa de últimos vistos Y tu mapa de liveServers
    lastSeen.put(peerId, ts);
    liveServers.put(peerId, new InetSocketAddress(host, port));
}

    /**
     * Elimina de liveServers y lastSeen a los peers que no envían heartbeats
     * desde hace más de TIMEOUT_MS milisegundos.
     */
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

    /** @return Mapa de servidorId → InetSocketAddress de los peers vivos. */
    public Map<String, InetSocketAddress> getLiveServers() {
        return liveServers;
    }

    /**
     * Conveniencia para obtener solo el hostname (o IP) de un servidor.
     * @param serverId Identificador del servidor
     * @return Hostname o null si no está vivo
     */
    public String getHostForServer(String serverId) {
        InetSocketAddress addr = liveServers.get(serverId);
        return (addr != null) ? addr.getHostString() : null;
    }
}
