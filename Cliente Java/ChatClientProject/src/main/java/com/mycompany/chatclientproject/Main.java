/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Estudiante_MCA
 */

package com.mycompany.chatclientproject;

import com.mycompany.configloaderproject.ConfigLoader;
import com.mycompany.databaseproject.Database;
import com.mycompany.databaseproject.DatabaseService;

public class Main {
    public static void main(String[] args) {
        try {
            ConfigLoader config = new ConfigLoader();
            String host = config.getProperty("server.host");
            int port = Integer.parseInt(config.getProperty("server.port"));
            String dbUrl = expandUser(config.getProperty("db_url"));
            String dbUser = config.getProperty("db_user");
            String dbPassword = config.getProperty("db_pass");

            if (host == null || host.trim().isEmpty()) {
                System.err.println("Error: La propiedad 'server.host' no está definida en config.properties. Usando valor por defecto: localhost");
                host = "localhost";
            }
            if (port == 0) {
                System.err.println("Error: La propiedad 'server.port' no está definida o es inválida en config.properties. Usando valor por defecto: 12345");
                port = 12345;
            }
            if (dbUrl == null || dbUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("La propiedad 'db_url' es obligatoria en config.properties");
            }
            if (dbUser == null || dbUser.trim().isEmpty()) {
                throw new IllegalArgumentException("La propiedad 'db_user' es obligatoria en config.properties");
            }
            if (dbPassword == null) {
                System.err.println("Advertencia: La propiedad 'db_pass' no está definida. Usando cadena vacía.");
                dbPassword = "";
            }

            DatabaseService db = new Database(dbUrl, dbUser, dbPassword);
            ChatClient client = ClientFactory.createClient(host, port, db);

            if (client.isRunning()) {
                client.start();
            } else {
                System.out.println("No se pudo iniciar el cliente. Verifica que el servidor esté ejecutándose.");
                db.close();
            }
        } catch (Exception e) {
            System.err.println("Error al iniciar el cliente: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String expandUser(String path) {
        return path.replace("~", System.getProperty("user.home"));
    }
}