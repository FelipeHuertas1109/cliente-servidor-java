package com.mycompany.chatserverproject.distributed;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StateSyncService {
    private static final boolean DEBUG = true;

    private final int port;
    private final UserFileRegistry registry;
    private final HeartbeatReceiver hbReceiver;
    private final ObjectMapper mapper;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    public StateSyncService(int port,
                            UserFileRegistry registry,
                            HeartbeatReceiver hbReceiver) {
        this.port       = port;
        this.registry   = registry;
        this.hbReceiver = hbReceiver;

        // Construye y configura el mapper para que NO cierre los streams
        this.mapper = new ObjectMapper();
        mapper.getFactory().configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
        mapper.getFactory().configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false);
    }

    public void start() throws Exception {
        ServerSocket serverSocket = new ServerSocket(port);
        debug("start() → escuchando en puerto " + port);
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
        debug("handleConnection(): conexión de " + sock.getRemoteSocketAddress());
        try (InputStream in = sock.getInputStream();
             OutputStream out = sock.getOutputStream()) {

            debug("handleConnection(): leyendo SyncRequest…");
            SyncRequest req = mapper.readValue(in, SyncRequest.class);
            debug("handleConnection(): tipo=" + req.getType());

            if (req.getType() == SyncRequest.Type.FULL_DUMP) {
                debug("→ FULL_DUMP: enviando dump a " + sock.getRemoteSocketAddress());
                FullDump dump = new FullDump(registry.getAllUsers(), registry.getAllFiles());
                mapper.writeValue(out, dump);
                out.flush();
                debug("→ FULL_DUMP enviado. Usuarios=" + dump.getUsers().size()
                      + ", Archivos=" + dump.getFiles().size());

            } else if (req.getType() == SyncRequest.Type.DIFF) {
                debug("→ DIFF: leyendo diff…");
                Diff diff = mapper.readValue(in, Diff.class);
                debug("→ DIFF tipo=" + diff.getType()
                      + (diff.getUserInfo()!=null ? " usuario=" + diff.getUserInfo().getUsername() : ""));
                registry.applyDiff(diff);
                debug("→ DIFF aplicado");
            }

        } catch (Exception e) {
            System.err.println("[ERROR] handleConnection para "
                               + sock.getRemoteSocketAddress() + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            debug("handleConnection(): cerrando socket " + sock.getRemoteSocketAddress());
            try { sock.close(); } catch (Exception ignore) {}
        }
    }

    public void requestFullDump(String host, int port) throws Exception {
        debug("requestFullDump() → conectando a " + host + ":" + port);
        try (Socket sock = new Socket(host, port);
             OutputStream out = sock.getOutputStream();
             InputStream in  = sock.getInputStream()) {

            debug("→ enviando FULL_DUMP request");
            SyncRequest req = new SyncRequest(SyncRequest.Type.FULL_DUMP);
            mapper.writeValue(out, req);
            out.flush();

            debug("→ esperando FullDump…");
            FullDump dump = mapper.readValue(in, FullDump.class);
            debug("→ recibido FullDump Usuarios=" + dump.getUsers().size()
                  + ", Archivos=" + dump.getFiles().size());
            registry.integrateFullDump(dump);

        } catch (Exception e) {
            System.err.println("[ERROR] requestFullDump con " + host + ":" + port
                               + " → " + e.getMessage());
            throw e;
        }
    }

    public void broadcastDiff(Diff diff) {
        debug("broadcastDiff() → peers vivos: " + hbReceiver.getLiveServers());
        for (Map.Entry<String,String> e : hbReceiver.getLiveServers().entrySet()) {
            String host = e.getValue();
            debug("→ enviando DIFF a " + host);
            sendDiffToPeer(host, port, diff);
        }
    }

    private void sendDiffToPeer(String host, int port, Diff diff) {
        debug("sendDiffToPeer() → conectando a " + host + ":" + port);
        try (Socket sock = new Socket(host, port);
             OutputStream out = sock.getOutputStream()) {

            debug("→ enviando SyncRequest.DIFF");
            SyncRequest req = new SyncRequest(SyncRequest.Type.DIFF);
            mapper.writeValue(out, req);
            out.flush();

            debug("→ enviando Diff tipo=" + diff.getType());
            mapper.writeValue(out, diff);
            out.flush();

            debug("→ DIFF enviado correctamente");

        } catch (Exception ex) {
            System.err.println("[ERROR] sendDiffToPeer a " + host + ":" + port
                               + " → " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void debug(String msg) {
        if (DEBUG) {
            System.out.println("[DEBUG][" + Thread.currentThread().getName() + "] " + msg);
        }
    }
}
