// src/main/java/com/mycompany/chatserverproject/Main.java
package com.mycompany.chatserverproject;

import com.mycompany.configloaderproject.ConfigLoader;
import com.mycompany.databaseconnectorproject.DatabaseConnector;
import com.mycompany.databaseconnectorproject.DatabaseConnection;
import com.mycompany.chatserverproject.distributed.HeartbeatSender;
import com.mycompany.chatserverproject.distributed.HeartbeatReceiver;
import com.mycompany.chatserverproject.distributed.UserFileRegistry;
import com.mycompany.chatserverproject.distributed.StateSyncService;

import java.net.InetSocketAddress;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        try {
            // 1) Carga configuración
            ConfigLoader config = new ConfigLoader();
            int port           = Integer.parseInt(config.getProperty("port"));
            int maxConnections = Integer.parseInt(config.getProperty("max_conexiones"));
            String dbUrl  = config.getProperty("db_url");
            String dbUser = config.getProperty("db_user");
            String dbPass = config.getProperty("db_pass");

            if (port <= 0) {
                System.err.println("Propiedad 'port' inválida. Usando 12345");
                port = 12345;
            }
            if (maxConnections <= 0) {
                System.err.println("Propiedad 'max_conexiones' inválida. Usando 10");
                maxConnections = 10;
            }
            if (dbUrl == null || dbUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("db_url es obligatorio");
            }
            if (dbUser == null || dbUser.trim().isEmpty()) {
                throw new IllegalArgumentException("db_user es obligatorio");
            }
            if (dbPass == null) {
                System.err.println("Advertencia: db_pass no definida. Usando vacío.");
                dbPass = "";
            }

            // 2) Conexión a BD y creación del ChatServer
            DatabaseConnection db = new DatabaseConnector(dbUrl, dbUser, dbPass);
            ChatServer server = ServerFactory.createServer(port, maxConnections, db, null);

            // 3) Parámetros distribuidos
            String serverId = config.getProperty("server_id");
            int    syncPort = Integer.parseInt(config.getProperty("sync_port"));

            // 4) Descubrimiento con heartbeats
            HeartbeatSender hbSender   = new HeartbeatSender(serverId);
            hbSender.start();
            HeartbeatReceiver hbReceiver = new HeartbeatReceiver();
            hbReceiver.start();

            // 5) Registry y sincronización de estado
            UserFileRegistry registry = new UserFileRegistry();
            StateSyncService  syncService =
                new StateSyncService(syncPort, registry, hbReceiver);
            syncService.start();

            // 6) Montar la UI (recibe servidor, receptor de heartbeats y registry)
            ServerUI ui = new ServerGUI(server, hbReceiver, registry);
            server.setUI(ui);

            // 7) Inyectar dependencias en ChatServer
            server.setServerId(serverId);
            server.setUserFileRegistry(registry);
            server.setStateSyncService(syncService);

            // 8) Dump inicial desde cada par vivo (usar host, no peerId)
            for (Map.Entry<String, InetSocketAddress> entry
                    : hbReceiver.getLiveServers().entrySet()) {
                String host = entry.getValue().getHostString();
                syncService.requestFullDump(host, syncPort);
            }

            // 9) Arrancar el listener de clientes
            server.start();

        } catch (Exception e) {
            System.err.println("Error al iniciar el servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
