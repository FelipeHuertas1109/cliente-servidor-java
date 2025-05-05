package com.mycompany.chatserverproject;

import com.mycompany.configloaderproject.ConfigLoader;
import com.mycompany.databaseconnectorproject.DatabaseConnector;
import com.mycompany.databaseconnectorproject.DatabaseConnection;
import com.mycompany.chatserverproject.distributed.HeartbeatSender;
import com.mycompany.chatserverproject.distributed.HeartbeatReceiver;
import com.mycompany.chatserverproject.distributed.UserFileRegistry;
import com.mycompany.chatserverproject.distributed.StateSyncService;


public class Main {
    public static void main(String[] args) {
        try {
            System.out.println("[DEBUG] Cargando configuración");
            ConfigLoader config = new ConfigLoader();
            int port           = Integer.parseInt(config.getProperty("port"));
            int maxConnections = Integer.parseInt(config.getProperty("max_conexiones"));
            String dbUrl       = config.getProperty("db_url");
            String dbUser      = config.getProperty("db_user");
            String dbPass      = config.getProperty("db_pass");

            if (port <= 0)            port = 12345;
            if (maxConnections <= 0)  maxConnections = 10;
            if (dbUrl == null || dbUrl.trim().isEmpty())
                throw new IllegalArgumentException("db_url es obligatorio");
            if (dbUser == null || dbUser.trim().isEmpty())
                throw new IllegalArgumentException("db_user es obligatorio");
            if (dbPass == null) dbPass = "";

            System.out.println("[DEBUG] Conectando a la BD y creando ChatServer");
            DatabaseConnection dbConn = new DatabaseConnector(dbUrl, dbUser, dbPass);
            ChatServer server = ServerFactory.createServer(port, maxConnections, dbConn, null);

            System.out.println("[DEBUG] Configurando IDs y puertos distribuidos");
            String serverId = config.getProperty("server_id");
            int    syncPort = Integer.parseInt(config.getProperty("sync_port"));

            System.out.println("[DEBUG] Arrancando Heartbeats");
            HeartbeatSender hbSender = new HeartbeatSender(serverId);
            hbSender.start();
            HeartbeatReceiver hbReceiver = new HeartbeatReceiver();
            hbReceiver.start();
            server.setHeartbeatReceiver(hbReceiver);
            server.setChatPort(port);

            System.out.println("[DEBUG] Arrancando StateSyncService");
            UserFileRegistry registry = new UserFileRegistry();
            StateSyncService syncService =
                new StateSyncService(syncPort, registry, hbReceiver);
            syncService.start();

            System.out.println("[DEBUG] Montando UI distribuida");
            ServerUI ui = new ServerGUI(server, hbReceiver, registry);
            server.setUI(ui);

            System.out.println("[DEBUG] Inyectando dependencias en ChatServer");
            server.setServerId(serverId);
            server.setUserFileRegistry(registry);
            server.setStateSyncService(syncService);

            // Esperar hasta descubrir al menos un peer
            System.out.println("[DEBUG] Esperando discovery de peers…");
            long deadline = System.currentTimeMillis() + 10_000;
            while (hbReceiver.getLiveServers().isEmpty() &&
                   System.currentTimeMillis() < deadline) {
                Thread.sleep(200);
            }

            if (hbReceiver.getLiveServers().isEmpty()) {
                System.err.println("[WARN] No se descubrió ningún peer vivo en 10s; salto full-dump inicial");
            } else {
                Thread.sleep(500);  // margen para que el peer arranque su listener

                System.out.println("[DEBUG] Lanzando full-dump inicial a peers: "
                                   + hbReceiver.getLiveServers().values());
                for (String peerHost : hbReceiver.getLiveServers().values()) {
                    try {
                        syncService.requestFullDump(peerHost, syncPort);
                    } catch (Exception ex) {
                        System.err.println("[ERROR] sync con " + peerHost + ": " + ex.getMessage());
                    }
                }
            }

            System.out.println("[DEBUG] Arrancando listener de clientes");
            server.start();

        } catch (Exception e) {
            System.err.println("[FATAL] Error al iniciar el servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
