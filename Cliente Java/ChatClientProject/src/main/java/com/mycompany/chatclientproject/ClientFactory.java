/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Estudiante_MCA
 */
package com.mycompany.chatclientproject;

import com.mycompany.databaseproject.DatabaseService;

public class ClientFactory {
    public static ChatClient createClient(String host, int port, DatabaseService db) {
        return new ChatClient(host, port, db);
    }
}