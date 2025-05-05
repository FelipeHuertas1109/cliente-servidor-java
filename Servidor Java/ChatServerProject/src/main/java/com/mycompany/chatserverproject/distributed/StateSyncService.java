// src/main/java/com/mycompany/chatserverproject/distributed/StateSyncService.java
package com.mycompany.chatserverproject.distributed;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servicio que sincroniza estado (FULL_DUMP y DIFF) entre servidores.
 */
public class StateSyncService {
    private final int port;
    private final UserFileRegistry registry;
    private final HeartbeatReceiver hbReceiver;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService pool = Executors.newCachedThreadPool();

    /**
     * @param port       Puerto TCP para FULL_DUMP y DIFF.
     * @param registry   Registro local de usuarios y archivos.
     * @param hbReceiver Para conocer la lista de peers vivos.
     */
    public StateSyncService(int port,
                            UserFileRegistry registry,
                            HeartbeatReceiver hbReceiver) {
        this.port       = port;
        this.registry   = registry;
        this.hbReceiver = hbReceiver;
    }

    /**
     * Arranca el listener de FULL_DUMP y DIFF entrantes.
     */
    public void start() throws Exception {
        ServerSocket serverSocket = new ServerSocket(port);
        pool.submit(() -> {
            try {
                while (true) {
                    Socket sock = serverSocket.accept();
                    pool.submit(() -> handleConnection(sock));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void handleConnection(Socket sock) {
        try (InputStream in = sock.getInputStream();
             OutputStream out = sock.getOutputStream()) {

            // 1) Leemos el SyncRequest (FULL_DUMP o DIFF)
            SyncRequest req = mapper.readValue(in, SyncRequest.class);

            if (req.getType() == SyncRequest.Type.FULL_DUMP) {
                // Envío del volcado completo
                FullDump dump = new FullDump(registry.getAllUsers(),
                                             registry.getAllFiles());
                mapper.writeValue(out, dump);

            } else if (req.getType() == SyncRequest.Type.DIFF) {
                // 2) Leemos el Diff y lo aplicamos
                Diff diff = mapper.readValue(in, Diff.class);
                registry.applyDiff(diff);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Solicita un volcado completo a otro servidor.
     */
    public void requestFullDump(String host, int port) throws Exception {
        try (Socket sock = new Socket(host, port);
             OutputStream out = sock.getOutputStream();
             InputStream  in  = sock.getInputStream()) {

            // Pido FULL_DUMP
            SyncRequest req = new SyncRequest(SyncRequest.Type.FULL_DUMP);
            mapper.writeValue(out, req);

            // Recibo el dump y lo integro
            FullDump dump = mapper.readValue(in, FullDump.class);
            registry.integrateFullDump(dump);
        }
    }

    /**
     * Envía un Diff a todos los peers vivos.
     */
    public void broadcastDiff(Diff diff) {
        Set<String> peers = hbReceiver.getLiveServers().keySet();
        for (String peerId : peers) {
            String host = hbReceiver.getHostForServer(peerId);
            if (host != null) {
                sendDiffToPeer(host, port, diff);
            }
        }
    }

    /**
     * Envía un SyncRequest.TYPE.DIFF + el objeto Diff a un solo peer.
     */
    public void sendDiff(Diff diff) {
        // para compatibilidad con ChatServer que llama a sendDiff(...)
        broadcastDiff(diff);
    }

    private void sendDiffToPeer(String host, int port, Diff diff) {
        try (Socket sock = new Socket(host, port);
             OutputStream out = sock.getOutputStream()) {

            // 1) Indico que es un DIFF
            SyncRequest req = new SyncRequest(SyncRequest.Type.DIFF);
            mapper.writeValue(out, req);

            // 2) Envío el objeto Diff
            mapper.writeValue(out, diff);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
