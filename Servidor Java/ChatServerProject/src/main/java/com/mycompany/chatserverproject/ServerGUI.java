package com.mycompany.chatserverproject;

import com.mycompany.databaseconnectorproject.DatabaseConnection;
import com.mycompany.chatserverproject.distributed.FileInfo;
import com.mycompany.chatserverproject.distributed.HeartbeatReceiver;
import com.mycompany.chatserverproject.distributed.UserFileRegistry;
import com.mycompany.chatserverproject.distributed.UserInfo;

import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/**
 * Interfaz gráfica del servidor: muestra logs, servidores vivos,
 * usuarios y archivos distribuidos, y permite generar informes.
 */
public class ServerGUI implements ServerUI {
    private final ChatServer server;
    private final HeartbeatReceiver hbReceiver;
    private final UserFileRegistry registry;

    private JFrame frame;
    private JTextArea logArea;
    private DefaultListModel<String> serverModel;
    private DefaultListModel<String> userModel;
    private DefaultListModel<String> fileModel;

    public ServerGUI(ChatServer server,
                     HeartbeatReceiver hbReceiver,
                     UserFileRegistry registry) {
        this.server      = server;
        this.hbReceiver  = hbReceiver;
        this.registry    = registry;
        SwingUtilities.invokeLater(this::initComponents);
        startRefreshTimer();
    }

    private void initComponents() {
        frame = new JFrame("Chat Server Distribuido");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(5,5));

        // Panel superior: listas de servidores, usuarios y archivos
        serverModel = new DefaultListModel<>();
        JList<String> serverList = new JList<>(serverModel);
        userModel   = new DefaultListModel<>();
        JList<String> userList   = new JList<>(userModel);
        fileModel   = new DefaultListModel<>();
        JList<String> fileList   = new JList<>(fileModel);

        JPanel listsPanel = new JPanel(new GridLayout(1,3,5,5));
        listsPanel.add(wrapInTitledPanel(serverList, "Servidores Vivos"));
        listsPanel.add(wrapInTitledPanel(userList,   "Usuarios Distribuidos"));
        listsPanel.add(wrapInTitledPanel(fileList,   "Archivos Distribuidos"));

        frame.add(listsPanel, BorderLayout.NORTH);

        // Centro: área de logs
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);
        frame.add(wrapInTitledPanel(logScroll, "Logs de Mensajes"), BorderLayout.CENTER);

        // Sur: botón de informes
        JButton reportButton = new JButton("Generar Informes");
        reportButton.addActionListener(e -> generateReports());
        frame.add(reportButton, BorderLayout.SOUTH);

        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel wrapInTitledPanel(Component comp, String title) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(comp, BorderLayout.CENTER);
        return p;
    }

    /** Refresca las tres listas desde hbReceiver y registry. */
    private void refreshRegistry() {
        SwingUtilities.invokeLater(() -> {
            // Servidores vivos
            serverModel.clear();
            for (Map.Entry<String, ?> entry : hbReceiver.getLiveServers().entrySet()) {
                serverModel.addElement(entry.getKey());
            }
            // Usuarios distribuidos
            userModel.clear();
            for (UserInfo u : registry.getAllUsers()) {
                userModel.addElement(u.getUsername() + "@" + u.getServerId());
            }
            // Archivos distribuidos
            fileModel.clear();
            for (FileInfo f : registry.getAllFiles()) {
                fileModel.addElement(f.getFilename() + " (" + f.getServerId() + ")");
            }
        });
    }

    /** Timer Swing que refresca el estado cada 2 segundos. */
    private void startRefreshTimer() {
        new Timer(2000, e -> refreshRegistry()).start();
    }

    @Override
    public void displayMessage(String message) {
        if (logArea != null) {
            SwingUtilities.invokeLater(() -> {
                logArea.append(message + "\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
            });
        } else {
            System.err.println(message);
        }
    }

    @Override
    public void initUI(Runnable reportAction) {
        // No usado en esta implementación; displayMessage() y constructor manejan todo.
    }

    private void generateReports() {
        StringBuilder report = new StringBuilder();
        report.append("=== Informes del Servidor ===\n\n");

        // 1) Usuarios registrados en BD
        report.append("Usuarios Registrados:\n");
        try (Connection conn = server.getDb().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT username, email, ip_address FROM users"
             )) {
            while (rs.next()) {
                report.append(String.format(
                    "Usuario: %s, Email: %s, IP: %s\n",
                    rs.getString("username"),
                    rs.getString("email"),
                    rs.getString("ip_address")
                ));
            }
        } catch (SQLException e) {
            report.append("Error al obtener usuarios: ").append(e.getMessage()).append("\n");
        }

        // 2) Canales y miembros
        report.append("\nCanales y Miembros:\n");
        try (Connection conn = server.getDb().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT c.name, GROUP_CONCAT(u.username) AS members " +
                 "FROM channels c " +
                 "LEFT JOIN channel_members cm ON c.id = cm.channel_id " +
                 "LEFT JOIN users u ON cm.user_id = u.id " +
                 "GROUP BY c.name"
             )) {
            while (rs.next()) {
                String members = rs.getString("members");
                report.append(String.format(
                    "Canal: %s, Miembros: %s\n",
                    rs.getString("name"),
                    (members != null ? members : "Ninguno")
                ));
            }
        } catch (SQLException e) {
            report.append("Error al obtener canales: ").append(e.getMessage()).append("\n");
        }

        // 3) Clientes conectados
        report.append("\nUsuarios Conectados:\n");
        if (server.getClients().isEmpty()) {
            report.append("Ninguno\n");
        } else {
            for (String user : server.getClients().keySet()) {
                report.append(user).append("\n");
            }
        }

        // 4) Logs guardados en BD
        report.append("\nLogs de Mensajes:\n");
        try (Connection conn = server.getDb().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT u.username AS sender, m.destination, m.message, m.timestamp " +
                 "FROM messages m " +
                 "JOIN users u ON m.sender_id = u.id " +
                 "ORDER BY m.timestamp"
             )) {
            while (rs.next()) {
                report.append(String.format(
                    "[%s] %s -> %s: %s\n",
                    rs.getTimestamp("timestamp"),
                    rs.getString("sender"),
                    rs.getString("destination"),
                    rs.getString("message")
                ));
            }
        } catch (SQLException e) {
            report.append("Error al obtener logs: ").append(e.getMessage()).append("\n");
        }

        // Mostrar en diálogo
        JTextArea ta = new JTextArea(report.toString());
        ta.setEditable(false);
        JOptionPane.showMessageDialog(
            frame,
            new JScrollPane(ta),
            "Informes del Servidor",
            JOptionPane.INFORMATION_MESSAGE
        );
    }
}
