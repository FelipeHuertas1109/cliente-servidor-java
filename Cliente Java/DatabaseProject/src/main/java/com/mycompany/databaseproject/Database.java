/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */


/**
 *
 * @author Estudiante_MCA
 */
package com.mycompany.databaseproject;

import java.sql.*;

public class Database implements DatabaseService {
    private Connection conn;

    public Database(String dbUrl, String dbUser, String dbPassword) {
        try {
            detectAndRegisterDriver(dbUrl);
            conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
            createTableIfNotExists();
        } catch (SQLException e) {
            throw new RuntimeException("Error al conectar con la base de datos del cliente", e);
        }
    }

    private void detectAndRegisterDriver(String dbUrl) {
        try {
            if (dbUrl.contains("mysql")) {
                Class.forName("com.mysql.cj.jdbc.Driver");
                System.out.println("Controlador MySQL cargado.");
            } else if (dbUrl.contains("h2")) {
                Class.forName("org.h2.Driver");
                System.out.println("Controlador H2 cargado.");
            } else if (dbUrl.contains("postgresql")) {
                Class.forName("org.postgresql.Driver");
                System.out.println("Controlador PostgreSQL cargado.");
            } else if (dbUrl.contains("oracle")) {
                Class.forName("oracle.jdbc.OracleDriver");
                System.out.println("Controlador Oracle cargado.");
            } else {
                throw new IllegalArgumentException("Tipo de base de datos no soportado o URL no válida.");
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Error al cargar el controlador JDBC", e);
        }
    }

    private void createTableIfNotExists() {
        String sql = "CREATE TABLE IF NOT EXISTS messages (id INT AUTO_INCREMENT PRIMARY KEY, sender VARCHAR(50), message TEXT, file BLOB, timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("Tabla 'messages' verificada/creada en la base de datos del cliente.");
        } catch (SQLException e) {
            throw new RuntimeException("Error al crear la tabla 'messages' en el cliente", e);
        }
    }

    @Override
    public void saveMessage(String sender, String message, byte[] file) {
        String sql = "INSERT INTO messages (sender, message, file) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sender);
            stmt.setString(2, message);
            stmt.setBytes(3, file);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}