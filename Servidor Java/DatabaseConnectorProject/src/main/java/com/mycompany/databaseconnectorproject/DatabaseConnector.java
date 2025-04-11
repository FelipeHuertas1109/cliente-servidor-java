/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */
/**
 *
 * @author Estudiante_MCA
 */
package com.mycompany.databaseconnectorproject;

import java.sql.*;
import org.apache.commons.dbcp2.BasicDataSource;

public class DatabaseConnector implements DatabaseConnection {
    private BasicDataSource dataSource;

    public DatabaseConnector(String dbUrl, String dbUser, String dbPass) {
        dataSource = new BasicDataSource();
        dataSource.setUrl(dbUrl);
        dataSource.setUsername(dbUser);
        dataSource.setPassword(dbPass);
        dataSource.setMinIdle(5);
        dataSource.setMaxIdle(10);
        dataSource.setMaxOpenPreparedStatements(100);

        detectAndRegisterDriver(dbUrl);
        createTablesIfNotExist();
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
                throw new IllegalArgumentException("Base de datos no soportada o URL no válida.");
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Error al cargar el controlador JDBC", e);
        }
    }

    private void createTablesIfNotExist() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS users (id INT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(50) UNIQUE NOT NULL, email VARCHAR(100) UNIQUE NOT NULL, password VARCHAR(100) NOT NULL, photo BLOB, ip_address VARCHAR(15))");
            stmt.execute("CREATE TABLE IF NOT EXISTS channels (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(50) UNIQUE NOT NULL, creator_id INT, FOREIGN KEY (creator_id) REFERENCES users(id))");
            stmt.execute("CREATE TABLE IF NOT EXISTS channel_members (channel_id INT, user_id INT, PRIMARY KEY (channel_id, user_id), FOREIGN KEY (channel_id) REFERENCES channels(id), FOREIGN KEY (user_id) REFERENCES users(id))");
            stmt.execute("CREATE TABLE IF NOT EXISTS channel_requests (channel_id INT, user_id INT, status ENUM('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'PENDING', PRIMARY KEY (channel_id, user_id), FOREIGN KEY (channel_id) REFERENCES channels(id), FOREIGN KEY (user_id) REFERENCES users(id))");
            stmt.execute("CREATE TABLE IF NOT EXISTS messages (id INT AUTO_INCREMENT PRIMARY KEY, sender_id INT, destination VARCHAR(255), message TEXT, file VARCHAR(255), timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (sender_id) REFERENCES users(id))");
            System.out.println("Tablas verificadas/creadas en la base de datos del servidor.");
        } catch (SQLException e) {
            throw new RuntimeException("Error al crear las tablas en el servidor", e);
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        try {
            dataSource.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}