/*
 * Click nbfs://SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
/**
 *
 * @author Estudiante_MCA
 */
package com.mycompany.chatclientproject;

import com.mycompany.databaseproject.DatabaseService;
import java.io.*;
import java.net.Socket;
import java.util.*;

public class ChatClient implements MessageObserver {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private DatabaseService db;
    private volatile boolean running;
    private ChatGUI gui;
    private String username;
    private List<String> onlineUsers = new ArrayList<>();
    private List<String> registeredUsers = new ArrayList<>();
    private Set<String> joinedChannels = new HashSet<>();
    private Set<String> allChannels = new HashSet<>();
    private boolean isAuthenticated = false;
    private final CryptoService cryptoService = new CryptoService();

    public ChatClient(String host, int port, DatabaseService db) {
        try {
            this.socket = new Socket(host, port);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.db = db;
            this.running = true;
            this.gui = new ChatGUI(this);
            System.out.println("Conectado al servidor en " + host + ":" + port);
        } catch (IOException e) {
            System.err.println("No se pudo conectar al servidor en " + host + ":" + port);
            this.running = false;
        }
    }

    public void sendMessage(String message) {
        if (out != null && running) {
            out.println(cryptoService.encrypt(message));
        }
    }

    public void start() {
        if (!running) return;
        Thread receiveThread = new Thread(this::receiveMessages);
        receiveThread.start();
    }

    private void receiveMessages() {
        try {
            String message;
            while (running && (message = in.readLine()) != null) {
                String decryptedMessage = cryptoService.decrypt(message);
                onMessageReceived(decryptedMessage);
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Error en recepción de mensajes: " + e.getMessage());
                gui.displayMessage("Conexión perdida con el servidor");
            }
        }
    }

    public void stop() {
        running = false;
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
            db.close();
        } catch (IOException e) {
            System.err.println("Error al cerrar cliente: " + e.getMessage());
        }
    }

    public boolean isRunning() {
        return running;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public List<String> getOnlineUsers() {
        return onlineUsers;
    }

    public List<String> getRegisteredUsers() {
        return registeredUsers;
    }

    public Set<String> getJoinedChannels() {
        return joinedChannels;
    }

    public Set<String> getAllChannels() {
        return allChannels;
    }

    public boolean isAuthenticated() {
        return isAuthenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.isAuthenticated = authenticated;
        gui.toggleInterface(authenticated);
        if (authenticated) {
            sendMessage("GET_REGISTERED_USERS");
            sendMessage("GET_ONLINE_USERS");
            sendMessage("GET_JOINED_CHANNELS");
            sendMessage("GET_ALL_CHANNELS");
            sendMessage("GET_PROFILE_PHOTO:" + username);
        } else {
            onlineUsers.clear();
            registeredUsers.clear();
            joinedChannels.clear();
            allChannels.clear();
            gui.resetInterface();
        }
    }

    @Override
    public void onMessageReceived(String message) {
        if (message.startsWith("SUCCESS:")) {
            String successMsg = message.substring("SUCCESS:".length());
            if (successMsg.startsWith("Usuario registrado o conectado")) {
                setAuthenticated(true);
            } else if (successMsg.startsWith("Te has unido al canal")) {
                String channel = successMsg.split(":")[1].trim();
                joinedChannels.add(channel);
                gui.updateChannels();
            }
            gui.displayMessage(successMsg);
        } else if (message.startsWith("ERROR:")) {
            String errorMsg = message.substring("ERROR:".length());
            gui.displayMessage(errorMsg);
            if (errorMsg.contains("Contraseña incorrecta") || errorMsg.contains("Usuario no registrado")) {
                gui.showWelcomeDialog();
            }
        } else if (message.startsWith("MSG:")) {
            String[] parts = message.split(":", 4);
            if (parts.length < 4) {
                System.out.println("[" + username + "] Mensaje mal formado: " + message);
                return;
            }
            String destination = parts[1];
            String sender = parts[2];
            String msg = parts[3];
            String displayDestination = destination.startsWith("#") ? destination : sender;

            if (sender.equals(username)) {
                displayDestination = destination;
            }

            System.out.println("[" + username + "] Mensaje recibido - Destino: " + destination + ", Remitente: " + sender + ", Mensaje: " + msg);
            if (gui.isTabOpen(displayDestination)) {
                gui.displayMessage(sender + ": " + msg, displayDestination);
            }
            if (!gui.isChatOpen(displayDestination) && !sender.equals(username)) {
                gui.notifyNewMessage(displayDestination);
            }
            db.saveMessage(sender, msg, null);
        } else if (message.startsWith("FILE|")) {
            String[] parts = message.split("\\|", 5);
            if (parts.length < 5) return;
            String destination = parts[1];
            String sender = parts[2];
            String fileName = parts[3];
            byte[] file = Base64.getDecoder().decode(parts[4]);
            String displayDestination = destination.startsWith("#") ? destination : sender;
            if (sender.equals(username)) {
                displayDestination = destination;
            }
            saveFileLocally(file, fileName, displayDestination);
            if (gui.isTabOpen(displayDestination)) {
                gui.displayFileMessage(sender, displayDestination, fileName, file);
            }
            if (!gui.isChatOpen(displayDestination) && !sender.equals(username)) {
                gui.notifyNewMessage(displayDestination);
            }
            db.saveMessage(sender, "Archivo enviado: " + fileName, file);
        } else if (message.startsWith("ONLINE_USERS:")) {
            String users = message.substring("ONLINE_USERS:".length());
            onlineUsers.clear();
            if (!users.equals("none")) {
                onlineUsers.addAll(Arrays.asList(users.split(",")));
            }
            gui.updateOnlineUsers();
        } else if (message.startsWith("REGISTERED_USERS:")) {
            String users = message.substring("REGISTERED_USERS:".length());
            registeredUsers.clear();
            if (!users.equals("none")) {
                registeredUsers.addAll(Arrays.asList(users.split(",")));
            }
        } else if (message.startsWith("JOINED_CHANNELS:")) {
            String channels = message.substring("JOINED_CHANNELS:".length());
            joinedChannels.clear();
            if (!channels.equals("none")) {
                joinedChannels.addAll(Arrays.asList(channels.split(",")));
            }
            gui.updateChannels();
        } else if (message.startsWith("ALL_CHANNELS:")) {
            String channels = message.substring("ALL_CHANNELS:".length());
            allChannels.clear();
            if (!channels.equals("none")) {
                allChannels.addAll(Arrays.asList(channels.split(",")));
            }
            gui.updateChannels();
        } else if (message.startsWith("HISTORY:")) {
            String[] parts = message.split(":", 5);
            if (parts.length < 5) return;
            String channel = parts[1];
            String sender = parts[2];
            String msg = parts[3];
            String timestamp = parts[4];
            gui.displayMessage("[" + timestamp + "] " + sender + ": " + msg, "#" + channel);
        } else if (message.startsWith("CHAT_HISTORY:")) {
            String[] parts = message.split(":", 5);
            if (parts.length < 5) return;
            String user = parts[1];
            String sender = parts[2];
            String msg = parts[3];
            String timestamp = parts[4];
            gui.displayMessage("[" + timestamp + "] " + sender + ": " + msg, user);
        } else if (message.startsWith("HISTORY_FILE:")) {
            String[] parts = message.split(":", 4);
            if (parts.length < 4) return;
            String destination = parts[1];
            String sender = parts[2];
            String encodedFile = parts[3];
            byte[] file = Base64.getDecoder().decode(encodedFile);
            String fileName = "file_" + System.currentTimeMillis() + ".dat";
            saveFileLocally(file, fileName, "#" + destination);
            gui.displayFileMessage(sender, "#" + destination, fileName, file);
            db.saveMessage(sender, "Archivo recibido: " + fileName, file);
        } else if (message.startsWith("CHANNEL_REQUEST:")) {
            String[] parts = message.split(":", 3);
            if (parts.length < 3) return;
            String channel = parts[1];
            String requester = parts[2];
            gui.showChannelRequest(channel, requester);
        } else if (message.startsWith("PROFILE_PHOTO:")) {
            String photoData = message.substring("PROFILE_PHOTO:".length());
            if (!photoData.equals("none")) {
                gui.setUserPhoto(Base64.getDecoder().decode(photoData));
            } else {
                gui.setUserPhoto(null);
            }
        } else if (message.startsWith("NEW_MESSAGE_IN_CHANNEL:")) {
            String channel = message.substring("NEW_MESSAGE_IN_CHANNEL:".length());
            String channelKey = "#" + channel;
            if (!gui.isChatOpen(channelKey) && joinedChannels.contains(channel)) {
                gui.notifyNewMessage(channelKey);
            }
        }
    }

    private void saveFileLocally(byte[] file, String fileName, String destination) {
        File dir = new File("received_files" + File.separator + destination.replace("#", "channel_"));
        if (!dir.exists()) dir.mkdirs();
        try (FileOutputStream fos = new FileOutputStream(new File(dir, fileName))) {
            fos.write(file);
        } catch (IOException e) {
            gui.displayMessage("Error al guardar archivo localmente: " + e.getMessage());
        }
    }
}