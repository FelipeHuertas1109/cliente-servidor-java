package com.mycompany.chatserverproject;

import com.mycompany.databaseconnectorproject.DatabaseConnection;
import com.mycompany.chatserverproject.distributed.UserFileRegistry;
import com.mycompany.chatserverproject.distributed.StateSyncService;
import com.mycompany.chatserverproject.distributed.Diff;
import com.mycompany.chatserverproject.distributed.UserInfo;
import com.mycompany.chatserverproject.distributed.HeartbeatReceiver;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.*;
import java.util.Base64;

public class ChatServer {
    private final int port;
    private final int maxConnections;
    private final DatabaseConnection db;
    private ServerUI ui;
    private final Map<String, PrintWriter> clients = new ConcurrentHashMap<>();
    private final Map<String, Set<PrintWriter>> channels = new ConcurrentHashMap<>();
    private final CryptoService cryptoService = new CryptoService();
    private final Logger logger;

    // ► Inyectados desde Main
    private String serverId;
    private UserFileRegistry registry;
    private StateSyncService syncService;
    private HeartbeatReceiver hbReceiver;
    private int chatPort;

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
            FileHandler fh = new FileHandler("server.log", true);
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
            logger.setLevel(Level.ALL);
            logger.setUseParentHandlers(false);
        } catch (IOException e) {
            System.err.println("Error logger: " + e.getMessage());
        }
    }

    // ► Setters para inyección
    public void setServerId(String serverId) {
        this.serverId = serverId;
    }
    public void setUserFileRegistry(UserFileRegistry registry) {
        this.registry = registry;
    }
    public void setStateSyncService(StateSyncService syncService) {
        this.syncService = syncService;
    }
    public void setHeartbeatReceiver(HeartbeatReceiver hbReceiver) {
        this.hbReceiver = hbReceiver;
    }
    public void setChatPort(int chatPort) {
        this.chatPort = chatPort;
    }
    public void setUI(ServerUI ui) {
        this.ui = ui;
    }

    private void log(String msg) {
        logger.info(msg);
        if (ui != null) ui.displayMessage(msg);
    }

    public void start() {
        try (ServerSocket ss = new ServerSocket(port)) {
            log("Servidor iniciado en puerto " + port);
            while (true) {
                if (clients.size() < maxConnections) {
                    Socket sock = ss.accept();
                    new Thread(new ClientHandler(sock, this)).start();
                    log("Nuevo cliente conectado: " + sock.getInetAddress());
                }
            }
        } catch (IOException e) {
            log("Error servidor: " + e.getMessage());
        }
    }

    // ─── Alta / Baja de clientes ────────────────────
    public synchronized void addClient(String username, PrintWriter out) {
        clients.put(username, out);

        if (registry != null && syncService != null && serverId != null) {
            Diff diff = new Diff(Diff.Type.USER_ADDED, new UserInfo(username, serverId));
            registry.applyDiff(diff);
            syncService.broadcastDiff(diff);
        }

        sendOnlineUsersToAll();
        sendAllChannelsToAll();
        log("Cliente " + username + " conectado.");
    }

    public synchronized void removeClient(PrintWriter out) {
        String user = null;
        for (var e : clients.entrySet()) {
            if (e.getValue().equals(out)) { user = e.getKey(); break; }
        }
        if (user != null) {
            clients.remove(user);
            channels.values().forEach(set -> set.remove(out));
            if (registry != null && syncService != null && serverId != null) {
                Diff diff = new Diff(Diff.Type.USER_REMOVED, new UserInfo(user, serverId));
                registry.applyDiff(diff);
                syncService.broadcastDiff(diff);
            }
            sendOnlineUsersToAll();
            log("Cliente " + user + " desconectado.");
        }
    }

    // ─── Envío mensajes ─────────────────────────────
    public synchronized void sendToUser(String username, String message, byte[] file) {
        // 1) Intento local
        PrintWriter out = clients.get(username);
        if (out != null) {
            String[] parts = message.split(":", 2);
            String sender = parts[0], body = parts.length>1?parts[1]:parts[0];
            String fmt = "MSG:" + username + ":" + sender + ":" + body;
            out.println(cryptoService.encrypt(fmt));
            if (file != null) {
                String fn = "file_"+System.currentTimeMillis()+".dat";
                saveFileOnServer(file, fn, username);
                String fmsg = "FILE|" + username+"|"+sender+"|"+fn+"|"+Base64.getEncoder().encodeToString(file);
                out.println(cryptoService.encrypt(fmsg));
            }
            log("Sent to "+username+" from "+sender+": "+body);
            return;
        }

        // 2) Reenvío remoto
        if (registry!=null && hbReceiver!=null) {
            UserInfo ui = registry.getAllUsers().stream()
                               .filter(u->u.getUsername().equals(username))
                               .findFirst().orElse(null);
            if (ui!=null) {
                String peerIp = hbReceiver.getLiveServers().get(ui.getServerId());
                if (peerIp!=null) {
                    try (Socket sock = new Socket(peerIp, chatPort);
                         PrintWriter pw = new PrintWriter(sock.getOutputStream(),true)) {
                        String[] parts = message.split(":",2);
                        String sender = parts[0], body = parts.length>1?parts[1]:parts[0];
                        String fwd = "FORWARD:"+username+":"+sender+":"+body;
                        pw.println(cryptoService.encrypt(fwd));
                        log("Forward "+username+"->"+ui.getServerId());
                    } catch(IOException ex) {
                        log("Error forward a "+username+"@"+ui.getServerId()+": "+ex);
                    }
                }
            }
        }
    }

    public synchronized void sendToChannel(String channel, String message, String sender, byte[] file) {
        Set<PrintWriter> channelClients = channels.getOrDefault(channel, new HashSet<>());
        String formattedMessage = "MSG:#" + channel + ":" + sender + ":" + message;
        System.out.println("Enviando mensaje al canal #" + channel + ": " + formattedMessage);
        
        // Enviar mensaje a todos los clientes del canal
        for (PrintWriter client : channelClients) {
            client.println(cryptoService.encrypt(formattedMessage));
            if (file != null) {
                String fileName = "file_" + System.currentTimeMillis() + ".dat";
                saveFileOnServer(file, fileName, "#" + channel);
                String fileMessage = "FILE|#" + channel + "|" + sender + "|" + fileName + "|" + Base64.getEncoder().encodeToString(file);
                client.println(cryptoService.encrypt(fileMessage));
            }
        }
        
        // Notificar a todos los clientes que están en el canal sobre el nuevo mensaje
        String notification = "NEW_MESSAGE_IN_CHANNEL:" + channel;
        for (PrintWriter client : channelClients) {
            client.println(cryptoService.encrypt(notification));
        }
        
        logMessage(sender, "#" + channel, message, file);
        log("Mensaje enviado al canal #" + channel + " desde " + sender + ": " + message);
    }

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
                "INSERT INTO channel_members (channel_id, user_id) SELECT c.id, u.id FROM channels c, users u WHERE c.name = ? AND u.username = ?")) {
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
                "INSERT INTO channel_requests (channel_id, user_id) SELECT c.id, u.id FROM channels c, users u WHERE c.name = ? AND u.username = ? ON DUPLICATE KEY UPDATE status = 'PENDING'")) {
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
                    log("Solicitud de unión al canal " + channel + " por " + username + " enviada al creador " + creator);
                }
            }
        } catch (SQLException e) {
            log("Error al enviar solicitud de unión al canal: " + e.getMessage());
        }
    }

    public synchronized void approveJoin(String channel, String username) {
        try (Connection conn = db.getConnection(); 
             PreparedStatement stmt = conn.prepareStatement(
                "UPDATE channel_requests SET status = 'APPROVED' WHERE channel_id = (SELECT id FROM channels WHERE name = ?) AND user_id = (SELECT id FROM users WHERE username = ?)")) {
            stmt.setString(1, channel);
            stmt.setString(2, username);
            stmt.executeUpdate();
            PrintWriter userOut = clients.get(username);
            if (userOut != null && channels.containsKey(channel)) {
                channels.get(channel).add(userOut);
                userOut.println(cryptoService.encrypt("SUCCESS:Te has unido al canal: " + channel));
                try (PreparedStatement memberStmt = conn.prepareStatement(
                    "INSERT INTO channel_members (channel_id, user_id) SELECT c.id, u.id FROM channels c, users u WHERE c.name = ? AND u.username = ?")) {
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
                "UPDATE channel_requests SET status = 'REJECTED' WHERE channel_id = (SELECT id FROM channels WHERE name = ?) AND user_id = (SELECT id FROM users WHERE username = ?)")) {
            stmt.setString(1, channel);
            stmt.setString(2, username);
            stmt.executeUpdate();
            PrintWriter userOut = clients.get(username);
            if (userOut != null) {
                userOut.println(cryptoService.encrypt("ERROR:Tu solicitud para unirte al canal " + channel + " fue rechazada"));
            }
            log("Solicitud de unión al canal " + channel + " por " + username + " rechazada");
        } catch (SQLException e) {
            log("Error al rechazar unión al canal: " + e.getMessage());
        }
    }

    public synchronized void sendRegisteredUsers(PrintWriter out) {
        try (Connection conn = db.getConnection(); 
             Statement stmt = conn.createStatement(); 
             ResultSet rs = stmt.executeQuery("SELECT username FROM users")) {
            StringBuilder users = new StringBuilder("REGISTERED_USERS:");
            while (rs.next()) {
                users.append(rs.getString("username")).append(",");
            }
            String usersStr = users.length() > "REGISTERED_USERS:".length() ? users.substring(0, users.length() - 1) : "REGISTERED_USERS:none";
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
                "SELECT c.name FROM channels c JOIN channel_members cm ON c.id = cm.channel_id JOIN users u ON cm.user_id = u.id WHERE u.username = ?")) {
            stmt.setString(1, getUsername(out));
            ResultSet rs = stmt.executeQuery();
            StringBuilder channelsList = new StringBuilder("JOINED_CHANNELS:");
            while (rs.next()) {
                channelsList.append(rs.getString("name")).append(",");
            }
            String channelsStr = channelsList.length() > "JOINED_CHANNELS:".length() ? channelsList.substring(0, channelsList.length() - 1) : "JOINED_CHANNELS:none";
            out.println(cryptoService.encrypt(channelsStr));
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
            StringBuilder channels = new StringBuilder("ALL_CHANNELS:");
            while (rs.next()) {
                channels.append(rs.getString("name")).append(",");
            }
            String channelsStr = channels.length() > "ALL_CHANNELS:".length() ? channels.substring(0, channels.length() - 1) : "ALL_CHANNELS:none";
            out.println(cryptoService.encrypt(channelsStr));
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
            StringBuilder channels = new StringBuilder("ALL_CHANNELS:");
            while (rs.next()) {
                channels.append(rs.getString("name")).append(",");
            }
            String channelsStr = channels.length() > "ALL_CHANNELS:".length() ? channels.substring(0, channels.length() - 1) : "ALL_CHANNELS:none";
            String encrypted = cryptoService.encrypt(channelsStr);
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
                "SELECT u.username AS sender, m.message, m.file, m.timestamp FROM messages m JOIN users u ON m.sender_id = u.id WHERE m.destination = ? ORDER BY m.timestamp")) {
            stmt.setString(1, "#" + channel);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String sender = rs.getString("sender");
                String msg = rs.getString("message");
                byte[] file = rs.getBytes("file");
                String timestamp = rs.getTimestamp("timestamp").toString();
                out.println(cryptoService.encrypt("HISTORY:" + channel + ":" + sender + ":" + msg + ":" + timestamp));
                if (file != null) {
                    out.println(cryptoService.encrypt("HISTORY_FILE:" + channel + ":" + sender + ":" + Base64.getEncoder().encodeToString(file)));
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
                "SELECT u.username AS sender, m.message, m.file, m.timestamp FROM messages m JOIN users u ON m.sender_id = u.id WHERE (m.destination = ? AND u.username = ?) OR (m.destination = ? AND u.username = ?) ORDER BY m.timestamp")) {
            String currentUser = getUsername(out);
            stmt.setString(1, user);
            stmt.setString(2, currentUser);
            stmt.setString(3, currentUser);
            stmt.setString(4, user);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String sender = rs.getString("sender");
                String msg = rs.getString("message");
                byte[] file = rs.getBytes("file");
                String timestamp = rs.getTimestamp("timestamp").toString();
                out.println(cryptoService.encrypt("CHAT_HISTORY:" + user + ":" + sender + ":" + msg + ":" + timestamp));
                if (file != null) {
                    out.println(cryptoService.encrypt("HISTORY_FILE:" + user + ":" + sender + ":" + Base64.getEncoder().encodeToString(file)));
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
             PreparedStatement stmt = conn.prepareStatement("SELECT photo FROM users WHERE username = ?")) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String photo = rs.getString("photo");
                String response = photo != null && !photo.isEmpty() ? "PROFILE_PHOTO:" + photo : "PROFILE_PHOTO:none";
                out.println(cryptoService.encrypt(response));
                log("Foto de perfil enviada a " + username);
            } else {
                out.println(cryptoService.encrypt("PROFILE_PHOTO:none"));
                log("No se encontró foto de perfil para " + username);
            }
        } catch (SQLException e) {
            out.println(cryptoService.encrypt("PROFILE_PHOTO:none"));
            log("Error al obtener foto de perfil: " + e.getMessage());
        }
    }

    public boolean authenticateUser(String username, String password) {
        try (Connection conn = db.getConnection(); 
             PreparedStatement stmt = conn.prepareStatement("SELECT password FROM users WHERE username = ?")) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                boolean authenticated = rs.getString("password").equals(password);
                log("Intento de autenticación para " + username + ": " + (authenticated ? "Éxito" : "Fallido"));
                return authenticated;
            }
        } catch (SQLException e) {
            log("Error al autenticar usuario: " + e.getMessage());
        }
        log("Intento de autenticación para " + username + ": Fallido (usuario no encontrado)");
        return false;
    }

    public boolean registerUser(String username, String email, String password, String photo, String ipAddress) {
        try (Connection conn = db.getConnection(); 
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO users (username, email, password, photo, ip_address) VALUES (?, ?, ?, ?, ?)")) {
            stmt.setString(1, username);
            stmt.setString(2, email);
            stmt.setString(3, password);
            stmt.setString(4, photo);
            stmt.setString(5, ipAddress);
            boolean success = stmt.executeUpdate() > 0;
            log("Registro de usuario " + username + ": " + (success ? "Éxito" : "Fallido"));
            return success;
        } catch (SQLException e) {
            log("Error al registrar usuario: " + e.getMessage());
            return false;
        }
    }

    private void logMessage(String sender, String destination, String message, byte[] file) {
        try (Connection conn = db.getConnection(); 
             PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO messages (sender_id, destination, message, file) SELECT id, ?, ?, ? FROM users WHERE username = ?")) {
            stmt.setString(1, destination);
            stmt.setString(2, message);
            stmt.setBytes(3, file);
            stmt.setString(4, sender);
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected == 0) {
                log("Error: No se pudo registrar el mensaje, usuario " + sender + " no encontrado.");
            }
        } catch (SQLException e) {
            log("Error al guardar mensaje: " + e.getMessage());
        }
    }
    // Método público que llama a saveFileOnServer
    public void storeFile(byte[] file, String fileName, String destination) {
    saveFileOnServer(file, fileName, destination);
    }
    
    public synchronized void sendFileToUser(String username, String sender, String originalFileName, byte[] file) {
    PrintWriter out = clients.get(username);
    if (out != null) {
        // No generamos un nuevo nombre, usamos el original
        saveFileOnServer(file, originalFileName, username);
        String fileMessage = "FILE|" + username + "|" + sender + "|" + originalFileName + "|" 
                             + Base64.getEncoder().encodeToString(file);
        out.println(cryptoService.encrypt(fileMessage));
        logMessage(sender, username, "Archivo enviado: " + originalFileName, file);
        log("Archivo " + originalFileName + " enviado a " + username + " desde " + sender);
    }
    }

    public synchronized void sendFileToChannel(String channel, String sender, String originalFileName, byte[] file) {
        Set<PrintWriter> channelClients = channels.getOrDefault(channel, new HashSet<>());
        String fileMessage = "FILE|#" + channel + "|" + sender + "|" + originalFileName + "|" 
                             + Base64.getEncoder().encodeToString(file);
        // Guardamos el archivo usando el nombre original
        saveFileOnServer(file, originalFileName, "#" + channel);
        for (PrintWriter client : channelClients) {
            client.println(cryptoService.encrypt(fileMessage));
        }
        logMessage(sender, "#" + channel, "Archivo enviado: " + originalFileName, file);
        log("Archivo " + originalFileName + " enviado al canal #" + channel + " desde " + sender);
    }



    private void saveFileOnServer(byte[] file, String fileName, String destination) {
        File dir = new File("server_files" + File.separator + destination.replace("#", "channel_"));
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

    public String encrypt(String data) {
        return cryptoService.encrypt(data);
    }

    public String decrypt(String encryptedData) {
        return cryptoService.decrypt(encryptedData);
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
    private String computeChecksum(byte[] data) {
    try {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    } catch (NoSuchAlgorithmException e) {
        return "";
    }
}
}