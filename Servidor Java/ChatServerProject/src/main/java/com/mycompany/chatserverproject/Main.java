/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Estudiante_MCA
 */

package com.mycompany.chatserverproject;

import com.mycompany.configloaderproject.ConfigLoader;
import com.mycompany.databaseconnectorproject.DatabaseConnector;
import com.mycompany.databaseconnectorproject.DatabaseConnection;

public class Main {
    public static void main(String[] args) {
        try {
            ConfigLoader config = new ConfigLoader();
            int port = Integer.parseInt(config.getProperty("port"));
            int maxConnections = Integer.parseInt(config.getProperty("max_conexiones"));
            String dbUrl = config.getProperty("db_url");
            String dbUser = config.getProperty("db_user");
            String dbPass = config.getProperty("db_pass");

            if (port == 0) {
                System.err.println("Error: La propiedad 'port' no está definida o es inválida en config.properties. Usando valor por defecto: 12345");
                port = 12345;
            }
            if (maxConnections == 0) {
                System.err.println("Error: La propiedad 'max_conexiones' no está definida o es inválida en config.properties. Usando valor por defecto: 10");
                maxConnections = 10;
            }
            if (dbUrl == null || dbUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("La propiedad 'db_url' es obligatoria en config.properties");
            }
            if (dbUser == null || dbUser.trim().isEmpty()) {
                throw new IllegalArgumentException("La propiedad 'db_user' es obligatoria en config.properties");
            }
            if (dbPass == null) {
                System.err.println("Advertencia: La propiedad 'db_pass' no está definida. Usando cadena vacía.");
                dbPass = "";
            }

            DatabaseConnection db = new DatabaseConnector(dbUrl, dbUser, dbPass);
            
            // Crear una instancia de ChatServer primero
            ChatServer server = ServerFactory.createServer(port, maxConnections, db, null);
            
            // Pasar la instancia de ChatServer al constructor de ServerGUI
            ServerUI ui = new ServerGUI(server);
            
            // Asignar el UI a ChatServer y luego iniciar el servidor
            server.setUI(ui);
            server.start();
        } catch (Exception e) {
            System.err.println("Error al iniciar el servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }
}