/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Estudiante_MCA
 */

package com.mycompany.chatserverproject;

import java.io.PrintWriter;
import java.util.Map;
import java.util.Set;

public interface MessageService {
    void registerClient(String username, PrintWriter out);
    void broadcast(String message, String sender);
    void sendToChannel(String channel, String message, String sender, byte[] file);
    void joinChannel(String channel, PrintWriter out);
    void removeClient(PrintWriter out);
    Map<String, PrintWriter> getClients();
}