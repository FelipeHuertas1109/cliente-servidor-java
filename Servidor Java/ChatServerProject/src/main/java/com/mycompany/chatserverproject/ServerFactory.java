/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author home
 */

package com.mycompany.chatserverproject;

import com.mycompany.databaseconnectorproject.DatabaseConnection;

public class ServerFactory {
    public static ChatServer createServer(int port, int maxConnections, DatabaseConnection db, ServerUI ui) {
        return new ChatServer(port, maxConnections, db, ui);
    }
}