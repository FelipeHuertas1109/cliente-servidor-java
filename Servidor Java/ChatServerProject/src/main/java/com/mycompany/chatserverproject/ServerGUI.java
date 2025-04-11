/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
/**
 *
 * @author Estudiante_MCA
 */
package com.mycompany.chatserverproject;

import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class ServerGUI implements ServerUI {
    private JTextArea logArea;
    private JFrame frame;
    private final ChatServer server;

    public ServerGUI(ChatServer server) {
        this.server = server;
        frame = new JFrame("Chat Server");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);

        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        frame.add(scrollPane, BorderLayout.CENTER);

        JButton reportButton = new JButton("Generar Informes");
        reportButton.addActionListener(e -> generateReports());
        frame.add(reportButton, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    @Override
    public void displayMessage(String message) {
        if (logArea != null) {
            SwingUtilities.invokeLater(() -> {
                logArea.append(message + "\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
            });
        } else {
            System.err.println("Error: logArea es null. Mensaje: " + message);
        }
    }

    @Override
    public void initUI(Runnable reportAction) {
        frame = new JFrame("Chat Server");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);

        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        frame.add(scrollPane, BorderLayout.CENTER);

        JButton reportButton = new JButton("Generar Informes");
        reportButton.addActionListener(e -> reportAction.run());
        frame.add(reportButton, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    private void generateReports() {
        StringBuilder report = new StringBuilder();
        report.append("=== Informes del Servidor ===\n\n");

        report.append("Usuarios Registrados:\n");
        try (Connection conn = server.getDb().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT username, email, ip_address FROM users")) {
            while (rs.next()) {
                report.append(String.format("Usuario: %s, Email: %s, IP: %s\n",
                        rs.getString("username"), rs.getString("email"), rs.getString("ip_address")));
            }
        } catch (SQLException e) {
            report.append("Error al obtener usuarios registrados: " + e.getMessage() + "\n");
        }

        report.append("\nCanales con Usuarios Vinculados:\n");
        try (Connection conn = server.getDb().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT c.name, GROUP_CONCAT(u.username) AS members FROM channels c LEFT JOIN channel_members cm ON c.id = cm.channel_id LEFT JOIN users u ON cm.user_id = u.id GROUP BY c.name")) {
            while (rs.next()) {
                report.append(String.format("Canal: %s, Miembros: %s\n",
                        rs.getString("name"), rs.getString("members") != null ? rs.getString("members") : "Ninguno"));
            }
        } catch (SQLException e) {
            report.append("Error al obtener canales: " + e.getMessage() + "\n");
        }

        report.append("\nUsuarios Conectados:\n");
        for (String username : server.getClients().keySet()) {
            report.append(username + "\n");
        }
        if (server.getClients().isEmpty()) {
            report.append("Ninguno\n");
        }

        report.append("\nLogs de Mensajes:\n");
        try (Connection conn = server.getDb().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT u.username AS sender, m.destination, m.message, m.timestamp FROM messages m JOIN users u ON m.sender_id = u.id ORDER BY m.timestamp")) {
            while (rs.next()) {
                report.append(String.format("[%s] %s -> %s: %s\n",
                        rs.getTimestamp("timestamp"), rs.getString("sender"), rs.getString("destination"), rs.getString("message")));
            }
        } catch (SQLException e) {
            report.append("Error al obtener logs: " + e.getMessage() + "\n");
        }

        JTextArea reportArea = new JTextArea(report.toString());
        reportArea.setEditable(false);
        JOptionPane.showMessageDialog(frame, new JScrollPane(reportArea), "Informes", JOptionPane.INFORMATION_MESSAGE);
    }
}