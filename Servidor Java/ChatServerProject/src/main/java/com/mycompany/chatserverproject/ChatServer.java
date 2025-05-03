package com.mycompany.chatserverproject;

import com.mycompany.databaseconnectorproject.DatabaseConnection;
import com.mycompany.configloaderproject.ConfigLoader;
import com.mycompany.chatserverproject.model.PeerInfo;
import com.mycompany.chatserverproject.discovery.ServerDiscoveryService;
import com.mycompany.chatserverproject.connector.ServerConnector;
import com.mycompany.chatserverproject.replication.ReplicationService;
import com.mycompany.chatserverproject.protocol.MessageType;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.*;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.logging.*;

public class ChatServer {

    // ————— EXISTENTES —————
    private final int port;
    private final int maxConnections;
    private final DatabaseConnection db;
    private ServerUI ui;
    private final Map<String, PrintWriter> clients = new HashMap<>();
    private final Map<String, Set<PrintWriter>> channels = new HashMap<>();
    private final CryptoService cryptoService = new CryptoService();
    private final Logger logger;

    // ————— NUEVOS CAMPOS P2P —————
    private List<PeerInfo> peers;
    private ServerDiscoveryService discovery;
    private ServerConnector connector;
    private ReplicationService replication;

    public ChatServer(int port, int maxConnections, DatabaseConnection db, ServerUI ui) {
        this.port = port;
        this.maxConnections = maxConnections;
        this.db = db;
        this.ui = ui;
        this.logger = Logger.getLogger(ChatServer.class.getName());
        setupLogger();
    }

    private void setupLogger() {
        try {
            FileHandler fileHandler = new FileHandler("server.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            logger.setLevel(Level.ALL);
            logger.setUseParentHandlers(false);
        } catch (IOException e) {
            System.err.println("Error al configurar el logger: " + e.getMessage());
        }
    }

    private void log(String message) {
        logger.info(message);
        if (ui != null) {
            ui.displayMessage(message);
        } else {
            System.out.println(message);
        }
    }

    public void setUI(ServerUI ui) {
        this.ui = ui;
    }

    public void start() {
        // —— INICIALIZACIÓN P2P ——
        try {
            // 1) cargar lista de servidores amigos
            peers       = ConfigLoader.loadPeers("servers-config.json");
            // 2) inicializar componentes P2P
            connector   = new ServerConnector();
            discovery   = new ServerDiscoveryService(peers, this);
            replication = new ReplicationService(connector, db);
            discovery.start();
            log("P2P: servicio de descubrimiento arrancado con peers: " + peers);
        } catch (Exception e) {
            log("Error al cargar peers P2P: " + e.getMessage());
        }

        // —— LÓGICA ORIGINAL DE SERVIDOR ——        
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log("Servidor iniciado en puerto " + port);
            while (true) {
                if (clients.size() < maxConnections) {
                    Socket clientSocket = serverSocket.accept();
                    Thread clientThread = new Thread(new ClientHandler(clientSocket, this));
                    clientThread.start();
                    log("Nuevo cliente conectado: " + clientSocket.getInetAddress());
                }
            }
        } catch (IOException e) {
            log("Error al iniciar el servidor: " + e.getMessage());
        }
    }

    // ——— CALLBACK PARA EVENTOS DE DESCUBRIMIENTO ———
    public void onPeerStatusChange(PeerInfo peer) {
        if (peer.isAlive()) {
            ui.showInfo("Servidor UP: " + peer);
            replication.syncNewPeer(peer);
            connector.sendMessage(peer, MessageType.SRV_JOIN, peer.toString());
        } else {
            ui.showWarning("Servidor DOWN: " + peer);
        }
    }

    // ——— RECEPCIÓN DE MENSAJES ENTRE SERVIDORES ———
    public void handleServerMessage(PeerInfo from, String raw) {
        String[] parts = raw.split("\\|", 2);
        MessageType type = MessageType.valueOf(parts[0]);
        String payload = parts.length > 1 ? parts[1] : "";
        switch (type) {
            case USER_REGISTER:
                // Guardar usuario remoto en BD
                // db.saveRemoteUser(User.fromJson(payload), from);
                log("P2P: usuario registrado en peer " + from + " -> " + payload);
                break;
            case MSG_REMOTE:
                // Reenviar a cliente local
                // deliverToLocalClient(payload);
                log("P2P: mensaje remoto de " + from + " -> " + payload);
                break;
            default:
                break;
        }
    }

    // ——— GESTIÓN DE CLIENTES ———
    public synchronized void addClient(String username, PrintWriter out) {
        clients.put(username, out);
        sendOnlineUsersToAll();
        sendAllChannelsToAll();
        log("Cliente " + username + " se ha conectado.");
    }

    public synchronized void removeClient(PrintWriter out) {
        String usernameToRemove = null;
        for (Map.Entry<String, PrintWriter> entry : clients.entrySet()) {
            if (entry.getValue().equals(out)) {
                usernameToRemove = entry.getKey();
                break;
            }
        }
        if (usernameToRemove != null) {
            clients.remove(usernameToRemove);
            for (Set<PrintWriter> channelClients : channels.values()) {
                channelClients.remove(out);
            }
            sendOnlineUsersToAll();
            log("Cliente desconectado: " + usernameToRemove);
        }
    }

    // ——— ENVÍO DE MENSAJES ———
    public synchronized void sendToUser(String username, String message, byte[] file) {
        PrintWriter out = clients.get(username);
        if (out != null) {
            String[] messageParts = message.split(":", 2);
            String sender     = messageParts[0];
            String msgContent = messageParts.length > 1 ? messageParts[1] : messageParts[0];
            String formatted  = "MSG:" + username + ":" + sender + ":" + msgContent;
            out.println(cryptoService.encrypt(formatted));

            if (file != null) {
                String fileName    = "file_" + System.currentTimeMillis() + ".dat";
                saveFileOnServer(file, fileName, username);
                String fileMessage = "FILE|" + username + "|" + sender + "|" + fileName + "|" +
                                     Base64.getEncoder().encodeToString(file);
                out.println(cryptoService.encrypt(fileMessage));
            }

            logMessage(sender, username, msgContent, file);
            log("Mensaje enviado a " + username + " desde " + sender + ": " + msgContent);
        }
    }

    public synchronized void sendToChannel(String channel, String message, String sender, byte[] file) {
        Set<PrintWriter> channelClients = channels.getOrDefault(channel, new HashSet<>());
        String formatted = "MSG:#" + channel + ":" + sender + ":" + message;

        for (PrintWriter client : channelClients) {
            client.println(cryptoService.encrypt(formatted));
            if (file != null) {
                String fileName    = "file_" + System.currentTimeMillis() + ".dat";
                saveFileOnServer(file, fileName, "#" + channel);
                String fileMessage = "FILE|#" + channel + "|" + sender + "|" + fileName + "|" +
                                     Base64.getEncoder().encodeToString(file);
                client.println(cryptoService.encrypt(fileMessage));
            }
            client.println(cryptoService.encrypt("NEW_MESSAGE_IN_CHANNEL:" + channel));
        }

        logMessage(sender, "#" + channel, message, file);
        log("Mensaje enviado al canal #" + channel + " desde " + sender + ": " + message);
    }

    // ——— CANALES ———
    public synchronized void createChannel(String channelName, String creator) {
        channels.putIfAbsent(channelName, new HashSet<>());
        PrintWriter creatorOut = clients.get(creator);
        if (creatorOut != null) {
            channels.get(channelName).add(creatorOut);
            creatorOut.println(cryptoService.encrypt("SUCCESS:Te has unido al canal: " + channelName));
        }
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO channels (name, creator_id) SELECT ?, id FROM users WHERE username = ?",
                 Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, channelName);
            stmt.setString(2, creator);
            stmt.executeUpdate();
            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                int channelId = rs.getInt(1);
                try (PreparedStatement memberStmt = conn.prepareStatement(
                    "INSERT INTO channel_members (channel_id, user_id) SELECT ?, id FROM users WHERE username = ?")) {
                    memberStmt.setInt(1, channelId);
                    memberStmt.setString(2, creator);
                    memberStmt.executeUpdate();
                }
            }
            sendAllChannelsToAll();
            log("Canal " + channelName + " creado por " + creator);
        } catch (SQLException e) {
            log("Error al crear canal: " + e.getMessage());
        }
    }

    public synchronized void addToChannel(String channel, String username) {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO channel_members (channel_id, user_id) " +
                 "SELECT c.id, u.id FROM channels c, users u WHERE c.name = ? AND u.username = ?")) {
            stmt.setString(1, channel);
            stmt.setString(2, username);
            stmt.executeUpdate();
            PrintWriter userOut = clients.get(username);
            if (userOut != null && channels.containsKey(channel)) {
                channels.get(channel).add(userOut);
                userOut.println(cryptoService.encrypt("SUCCESS:Te has unido al canal: " + channel));
            }
            log("Usuario " + username + " agregado al canal " + channel);
        } catch (SQLException e) {
            log("Error al agregar usuario al canal: " + e.getMessage());
        }
    }

    public synchronized void requestJoin(String channel, String username) {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO channel_requests (channel_id, user_id) " +
                 "SELECT c.id, u.id FROM channels c, users u WHERE c.name = ? AND u.username = ? " +
                 "ON DUPLICATE KEY UPDATE status = 'PENDING'")) {
            stmt.setString(1, channel);
            stmt.setString(2, username);
            stmt.executeUpdate();

            try (PreparedStatement creatorStmt = conn.prepareStatement(
                     "SELECT u.username FROM channels c JOIN users u ON c.creator_id = u.id WHERE c.name = ?")) {
                creatorStmt.setString(1, channel);
                ResultSet rs = creatorStmt.executeQuery();
                if (rs.next()) {
                    String creator = rs.getString("username");
                    PrintWriter creatorOut = clients.get(creator);
                    if (creatorOut != null) {
                        creatorOut.println(cryptoService.encrypt("CHANNEL_REQUEST:" + channel + ":" + username));
                    }
                    log("Solicitud de unión al canal " + channel + " por " + username +
                        " enviada al creador " + creator);
                }
            }

        } catch (SQLException e) {
            log("Error al enviar solicitud de unión al canal: " + e.getMessage());
        }
    }

    public synchronized void approveJoin(String channel, String username) {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE channel_requests SET status = 'APPROVED' " +
                 "WHERE channel_id = (SELECT id FROM channels WHERE name = ?) " +
                 "AND user_id = (SELECT id FROM users WHERE username = ?)")) {
            stmt.setString(1, channel);
            stmt.setString(2, username);
            stmt.executeUpdate();

            PrintWriter userOut = clients.get(username);
            if (userOut != null && channels.containsKey(channel)) {
                channels.get(channel).add(userOut);
                userOut.println(cryptoService.encrypt("SUCCESS:Te has unido al canal: " + channel));
                try (PreparedStatement memberStmt = conn.prepareStatement(
                         "INSERT INTO channel_members (channel_id, user_id) " +
                         "SELECT c.id, u.id FROM channels c, users u WHERE c.name = ? AND u.username = ?")) {
                    memberStmt.setString(1, channel);
                    memberStmt.setString(2, username);
                    memberStmt.executeUpdate();
                }
            }
            log("Solicitud de unión al canal " + channel + " por " + username + " aprobada");

        } catch (SQLException e) {
            log("Error al aprobar unión al canal: " + e.getMessage());
        }
    }

    public synchronized void rejectJoin(String channel, String username) {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE channel_requests SET status = 'REJECTED' " +
                 "WHERE channel_id = (SELECT id FROM channels WHERE name = ?) " +
                 "AND user_id = (SELECT id FROM users WHERE username = ?)")) {
            stmt.setString(1, channel);
            stmt.setString(2, username);
            stmt.executeUpdate();

            PrintWriter userOut = clients.get(username);
            if (userOut != null) {
                userOut.println(cryptoService.encrypt(
                    "ERROR:Tu solicitud para unirte al canal " + channel + " fue rechazada"));
            }
            log("Solicitud de unión al canal " + channel + " por " + username + " rechazada");

        } catch (SQLException e) {
            log("Error al rechazar unión al canal: " + e.getMessage());
        }
    }

    // ——— ENVÍO DE LISTAS E HISTORIA ———
    public synchronized void sendRegisteredUsers(PrintWriter out) {
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT username FROM users")) {
            StringBuilder users = new StringBuilder("REGISTERED_USERS:");
            while (rs.next()) {
                users.append(rs.getString("username")).append(",");
            }
            String usersStr = users.length() > "REGISTERED_USERS:".length()
                ? users.substring(0, users.length() - 1)
                : "REGISTERED_USERS:none";
            out.println(cryptoService.encrypt(usersStr));
            log("Lista de usuarios registrados enviada a " + getUsername(out));
        } catch (SQLException e) {
            out.println(cryptoService.encrypt("ERROR:No se pudo obtener usuarios registrados"));
            log("Error al obtener usuarios registrados: " + e.getMessage());
        }
    }

    public synchronized void sendOnlineUsers(PrintWriter out) {
        String users = clients.isEmpty() ? "none" : String.join(",", clients.keySet());
        out.println(cryptoService.encrypt("ONLINE_USERS:" + users));
        log("Lista de usuarios en línea enviada a " + getUsername(out));
    }

    public synchronized void sendOnlineUsersToAll() {
        String users = clients.isEmpty() ? "none" : String.join(",", clients.keySet());
        String encrypted = cryptoService.encrypt("ONLINE_USERS:" + users);
        for (PrintWriter client : clients.values()) {
            client.println(encrypted);
        }
        log("Lista de usuarios en línea actualizada para todos los clientes");
    }

    public synchronized void sendJoinedChannels(PrintWriter out) {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT c.name FROM channels c JOIN channel_members cm ON c.id = cm.channel_id " +
                 "JOIN users u ON cm.user_id = u.id WHERE u.username = ?")) {
            stmt.setString(1, getUsername(out));
            ResultSet rs = stmt.executeQuery();
            StringBuilder list = new StringBuilder("JOINED_CHANNELS:");
            while (rs.next()) {
                list.append(rs.getString("name")).append(",");
            }
            String str = list.length() > "JOINED_CHANNELS:".length()
                ? list.substring(0, list.length() - 1)
                : "JOINED_CHANNELS:none";
            out.println(cryptoService.encrypt(str));
            log("Lista de canales unidos enviada a " + getUsername(out));
        } catch (SQLException e) {
            out.println(cryptoService.encrypt("ERROR:No se pudo obtener los canales unidos"));
            log("Error al obtener canales unidos: " + e.getMessage());
        }
    }

    public synchronized void sendAllChannels(PrintWriter out) {
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM channels")) {
            StringBuilder channelsList = new StringBuilder("ALL_CHANNELS:");
            while (rs.next()) {
                channelsList.append(rs.getString("name")).append(",");
            }
            String str = channelsList.length() > "ALL_CHANNELS:".length()
                ? channelsList.substring(0, channelsList.length() - 1)
                : "ALL_CHANNELS:none";
            out.println(cryptoService.encrypt(str));
            log("Lista de todos los canales enviada a " + getUsername(out));
        } catch (SQLException e) {
            out.println(cryptoService.encrypt("ERROR:No se pudo obtener la lista de canales"));
            log("Error al obtener la lista de canales: " + e.getMessage());
        }
    }

    public synchronized void sendAllChannelsToAll() {
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM channels")) {
            StringBuilder channelsList = new StringBuilder("ALL_CHANNELS:");
            while (rs.next()) {
                channelsList.append(rs.getString("name")).append(",");
            }
            String encrypted = cryptoService.encrypt(
                channelsList.length() > "ALL_CHANNELS:".length()
                ? channelsList.substring(0, channelsList.length() - 1)
                : "ALL_CHANNELS:none");
            for (PrintWriter client : clients.values()) {
                client.println(encrypted);
            }
            log("Lista de todos los canales actualizada para todos los clientes");
        } catch (SQLException e) {
            log("Error al enviar lista de canales: " + e.getMessage());
        }
    }

    public synchronized void sendChannelHistory(String channel, PrintWriter out) {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT u.username AS sender, m.message, m.file, m.timestamp " +
                 "FROM messages m JOIN users u ON m.sender_id = u.id " +
                 "WHERE m.destination = ? ORDER BY m.timestamp")) {
            stmt.setString(1, "#" + channel);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String sender = rs.getString("sender");
                String msg    = rs.getString("message");
                byte[] file   = rs.getBytes("file");
                String time   = rs.getTimestamp("timestamp").toString();
                out.println(cryptoService.encrypt(
                    "HISTORY:" + channel + ":" + sender + ":" + msg + ":" + time));
                if (file != null) {
                    out.println(cryptoService.encrypt(
                        "HISTORY_FILE:" + channel + ":" + sender + ":" +
                        Base64.getEncoder().encodeToString(file)));
                }
            }
            log("Historial del canal #" + channel + " enviado a " + getUsername(out));
        } catch (SQLException e) {
            out.println(cryptoService.encrypt("ERROR:No se pudo obtener el historial del canal"));
            log("Error al obtener historial del canal: " + e.getMessage());
        }
    }

    public synchronized void sendChatHistory(String user, PrintWriter out) {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT u.username AS sender, m.message, m.file, m.timestamp " +
                 "FROM messages m JOIN users u ON m.sender_id = u.id " +
                 "WHERE (m.destination = ? AND u.username = ?) " +
                 "OR (m.destination = ? AND u.username = ?) " +
                 "ORDER BY m.timestamp")) {
            String me = getUsername(out);
            stmt.setString(1, user);
            stmt.setString(2, me);
            stmt.setString(3, me);
            stmt.setString(4, user);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String sender = rs.getString("sender");
                String msg    = rs.getString("message");
                byte[] file   = rs.getBytes("file");
                String time   = rs.getTimestamp("timestamp").toString();
                out.println(cryptoService.encrypt(
                    "CHAT_HISTORY:" + user + ":" + sender + ":" + msg + ":" + time));
                if (file != null) {
                    out.println(cryptoService.encrypt(
                        "HISTORY_FILE:" + user + ":" + sender + ":" +
                        Base64.getEncoder().encodeToString(file)));
                }
            }
            log("Historial del chat con " + user + " enviado a " + getUsername(out));
        } catch (SQLException e) {
            out.println(cryptoService.encrypt("ERROR:No se pudo obtener el historial del chat"));
            log("Error al obtener historial del chat: " + e.getMessage());
        }
    }

    public synchronized void sendProfilePhoto(String username, PrintWriter out) {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT photo FROM users WHERE username = ?")) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String photo   = rs.getString("photo");
                String response = (photo != null && !photo.isEmpty())
                    ? "PROFILE_PHOTO:" + photo
                    : "PROFILE_PHOTO:none";
                out.println(cryptoService.encrypt(response));
            } else {
                out.println(cryptoService.encrypt("PROFILE_PHOTO:none"));
            }
            log("Foto de perfil enviada a " + username);
        } catch (SQLException e) {
            out.println(cryptoService.encrypt("PROFILE_PHOTO:none"));
            log("Error al obtener foto de perfil: " + e.getMessage());
        }
    }

    // ——— AUTENTICACIÓN Y REGISTRO ———
    public boolean authenticateUser(String username, String password) {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT password FROM users WHERE username = ?")) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                boolean ok = rs.getString("password").equals(password);
                log("Intento de autenticación para " + username + ": " +
                    (ok ? "Éxito" : "Fallido"));
                return ok;
            }
        } catch (SQLException e) {
            log("Error al autenticar usuario: " + e.getMessage());
        }
        log("Intento de autenticación para " + username + ": Fallido (usuario no encontrado)");
        return false;
    }

    public boolean registerUser(String username, String email, String password, String photo, String ipAddress) {
        boolean success = false;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO users (username, email, password, photo, ip_address) VALUES (?, ?, ?, ?, ?)")) {
            stmt.setString(1, username);
            stmt.setString(2, email);
            stmt.setString(3, password);
            stmt.setString(4, photo);
            stmt.setString(5, ipAddress);
            success = stmt.executeUpdate() > 0;
            log("Registro de usuario " + username + ": " + (success ? "Éxito" : "Fallido"));
        } catch (SQLException e) {
            log("Error al registrar usuario: " + e.getMessage());
        }

        if (success) {
            // —— P2P: notificar a otros servidores ——
            String payload = username + "|" + email;
            for (PeerInfo p : peers) {
                if (p.isAlive()) {
                    connector.sendMessage(p, MessageType.USER_REGISTER, payload);
                }
            }
        }

        return success;
    }

    // ——— LOG DE MENSAJES ———
    private void logMessage(String sender, String destination, String message, byte[] file) {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO messages (sender_id, destination, message, file) " +
                 "SELECT id, ?, ?, ? FROM users WHERE username = ?")) {
            stmt.setString(1, destination);
            stmt.setString(2, message);
            stmt.setBytes(3, file);
            stmt.setString(4, sender);
            int rows = stmt.executeUpdate();
            if (rows == 0) {
                log("Error: No se pudo registrar el mensaje, usuario " + sender + " no encontrado.");
            }
        } catch (SQLException e) {
            log("Error al guardar mensaje: " + e.getMessage());
        }
    }

    // ——— ALMACENAMIENTO DE ARCHIVOS ———
    public void storeFile(byte[] file, String fileName, String destination) {
        saveFileOnServer(file, fileName, destination);
    }

    public synchronized void sendFileToUser(String username, String sender, String originalFileName, byte[] file) {
        PrintWriter out = clients.get(username);
        if (out != null) {
            saveFileOnServer(file, originalFileName, username);
            String fileMessage = "FILE|" + username + "|" + sender + "|" +
                                  originalFileName + "|" +
                                  Base64.getEncoder().encodeToString(file);
            out.println(cryptoService.encrypt(fileMessage));
            logMessage(sender, username, "Archivo enviado: " + originalFileName, file);
            log("Archivo " + originalFileName + " enviado a " + username + " desde " + sender);
        }
    }

    public synchronized void sendFileToChannel(String channel, String sender, String originalFileName, byte[] file) {
        Set<PrintWriter> channelClients = channels.getOrDefault(channel, new HashSet<>());
        saveFileOnServer(file, originalFileName, "#" + channel);
        String fileMessage = "FILE|#" + channel + "|" + sender + "|" +
                              originalFileName + "|" +
                              Base64.getEncoder().encodeToString(file);
        for (PrintWriter client : channelClients) {
            client.println(cryptoService.encrypt(fileMessage));
        }
        logMessage(sender, "#" + channel, "Archivo enviado: " + originalFileName, file);
        log("Archivo " + originalFileName + " enviado al canal #" + channel + " desde " + sender);
    }

    private void saveFileOnServer(byte[] file, String fileName, String destination) {
        File dir = new File("server_files" + File.separator +
                            destination.replace("#", "channel_"));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(new File(dir, fileName))) {
            fos.write(file);
            log("Archivo " + fileName + " guardado en el servidor para " + destination);
        } catch (IOException e) {
            log("Error al guardar archivo en el servidor: " + e.getMessage());
        }
    }

    // ——— ÚTILES ———
    public String encrypt(String data) {
        return cryptoService.encrypt(data);
    }

    public String decrypt(String data) {
        return cryptoService.decrypt(data);
    }

    private String getUsername(PrintWriter out) {
        for (Map.Entry<String, PrintWriter> entry : clients.entrySet()) {
            if (entry.getValue().equals(out)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public DatabaseConnection getDb() {
        return db;
    }

    public Map<String, PrintWriter> getClients() {
        return clients;
    }
}
