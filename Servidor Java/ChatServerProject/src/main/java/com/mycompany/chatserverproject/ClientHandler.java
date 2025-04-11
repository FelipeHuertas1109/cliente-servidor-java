/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
/**
 *
 * @author Estudiante_MCA
 */
package com.mycompany.chatserverproject;

import java.io.*;
import java.net.Socket;
import java.sql.*;
import java.util.Base64;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final ChatServer server;
    private BufferedReader in;
    private PrintWriter out;
    private String username;
    private volatile boolean running = true;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
        try {
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            System.err.println("Error al inicializar ClientHandler: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            String message;
            while (running && (message = in.readLine()) != null) {
                String decryptedMessage = server.decrypt(message);
                System.out.println("Mensaje recibido de " + (username != null ? username : "cliente") + ": " + decryptedMessage);
                handleMessage(decryptedMessage);
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Error en ClientHandler: " + e.getMessage());
            }
        } finally {
            stop();
        }
    }

    public void sendMessage(String message) {
        if (out != null) {
            out.println(server.encrypt(message));
        }
    }

    public void stop() {
        running = false;
        server.removeClient(out);
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Error al cerrar ClientHandler: " + e.getMessage());
        }
    }

    private void handleMessage(String message) {
        if (message.startsWith("REGISTER:")) {
            String[] parts = message.split(":", 5);
            if (parts.length < 5) return;
            String username = parts[1];
            String email = parts[2];
            String password = parts[3];
            String photo = parts[4];
            String ipAddress = socket.getInetAddress().getHostAddress();
            if (server.registerUser(username, email, password, photo, ipAddress)) {
                this.username = username;
                server.addClient(username, out);
                sendMessage("SUCCESS:Usuario registrado o conectado");
            } else {
                sendMessage("ERROR:Usuario ya registrado");
            }
        } else if (message.startsWith("LOGIN:")) {
            String[] parts = message.split(":", 3);
            if (parts.length < 3) return;
            String username = parts[1];
            String password = parts[2];
            if (server.authenticateUser(username, password)) {
                this.username = username;
                server.addClient(username, out);
                sendMessage("SUCCESS:Usuario registrado o conectado");
            } else {
                sendMessage("ERROR:Usuario no registrado o contraseña incorrecta");
            }
        } else if (message.startsWith("LOGOUT")) {
            stop();
        } else if (message.startsWith("MSG:")) {
            String[] parts = message.split(":", 3);
            if (parts.length < 3) return;
            String destination = parts[1];
            String msg = parts[2];
            if (destination.startsWith("#")) {
                String channel = destination.substring(1);
                server.sendToChannel(channel, msg, username, null);
            } else {
                server.sendToUser(destination, username + ":" + msg, null);
            }
        } else if (message.startsWith("FILE|")) {
            String[] parts = message.split("\\|", 5);
            if (parts.length < 5) return;

            String destination = parts[1];
            String sender = parts[2];
            String fileName = parts[3]; // Aquí se conserva el nombre original
            String encodedFile = parts[4];
            byte[] file = Base64.getDecoder().decode(encodedFile);

            if (destination.startsWith("#")) {
                String channel = destination.substring(1);
                server.sendFileToChannel(channel, sender, fileName, file);
            } else {
                server.sendFileToUser(destination, sender, fileName, file);
            }
        } else if (message.startsWith("GET_ONLINE_USERS")) {
            server.sendOnlineUsers(out);
        } else if (message.startsWith("GET_REGISTERED_USERS")) {
            server.sendRegisteredUsers(out);
        } else if (message.startsWith("GET_JOINED_CHANNELS")) {
            server.sendJoinedChannels(out);
        } else if (message.startsWith("GET_ALL_CHANNELS")) {
            server.sendAllChannels(out);
        } else if (message.startsWith("CREATE_CHANNEL:")) {
            String[] parts = message.split(":", 2);
            if (parts.length < 2) return;
            String channelName = parts[1];
            server.createChannel(channelName, username);
        } else if (message.startsWith("ADD_TO_CHANNEL:")) {
            String[] parts = message.split(":", 3);
            if (parts.length < 3) return;
            String channelName = parts[1];
            String targetUser = parts[2];
            try (Connection conn = server.getDb().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                    "SELECT u.username FROM channels c JOIN users u ON c.creator_id = u.id WHERE c.name = ?")) {
                stmt.setString(1, channelName);
                ResultSet rs = stmt.executeQuery();
                if (rs.next() && rs.getString("username").equals(username)) {
                    server.addToChannel(channelName, targetUser);
                } else {
                    sendMessage("ERROR:No eres administrador del canal");
                }
            } catch (SQLException e) {
                sendMessage("ERROR:Error al agregar usuario al canal");
            }
        } else if (message.startsWith("REQUEST_JOIN:")) {
            String[] parts = message.split(":", 3);
            if (parts.length < 3) return;
            String channelName = parts[1];
            String requester = parts[2];
            server.requestJoin(channelName, requester);
        } else if (message.startsWith("APPROVE_JOIN:")) {
            String[] parts = message.split(":", 3);
            if (parts.length < 3) return;
            String channelName = parts[1];
            String requester = parts[2];
            server.approveJoin(channelName, requester);
        } else if (message.startsWith("REJECT_JOIN:")) {
            String[] parts = message.split(":", 3);
            if (parts.length < 3) return;
            String channelName = parts[1];
            String requester = parts[2];
            server.rejectJoin(channelName, requester);
        } else if (message.startsWith("GET_CHANNEL_HISTORY:")) {
            String[] parts = message.split(":", 2);
            if (parts.length < 2) return;
            String channelName = parts[1];
            server.sendChannelHistory(channelName, out);
        } else if (message.startsWith("GET_CHAT_HISTORY:")) {
            String[] parts = message.split(":", 2);
            if (parts.length < 2) return;
            String user = parts[1];
            server.sendChatHistory(user, out);
        } else if (message.startsWith("GET_PROFILE_PHOTO:")) {
            String[] parts = message.split(":", 2);
            if (parts.length < 2) return;
            String targetUser = parts[1];
            server.sendProfilePhoto(targetUser, out);
        }
    }
}