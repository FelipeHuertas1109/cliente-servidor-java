/*
 * Click nbfs://SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
/**
 *
 * @author Estudiante_MCA
 */
package com.mycompany.chatclientproject;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import javax.swing.*;

public class ChatGUI {

    private ChatClient client;
    private JFrame frame;
    private JTabbedPane tabbedPane;
    private JTextField messageField;
    private JList<String> onlineUsersList;
    private JList<String> channelsList;
    private DefaultListModel<String> onlineUsersModel;
    private DefaultListModel<String> channelsModel;
    private Map<String, JPanel> chatPanels;
    private Map<String, Set<String>> displayedMessages;
    private Map<String, Integer> unreadMessages;
    private Map<String, JLabel> tabTitles;
    private JPanel inputPanel;
    private JPanel sidePanel;
    private byte[] userPhoto;
    private Map<String, String> channelAdmins;

    public ChatGUI(ChatClient client) {
        this.client = client;
        this.chatPanels = new HashMap<>();
        this.displayedMessages = new HashMap<>();
        this.unreadMessages = new HashMap<>();
        this.tabTitles = new HashMap<>();
        this.channelAdmins = new HashMap<>();
        initGUI();
    }

    private void initGUI() {
        frame = new JFrame("Chat Client - " + System.currentTimeMillis());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 600);

        sidePanel = new JPanel(new BorderLayout());
        JTabbedPane sideTabs = new JTabbedPane();

        onlineUsersModel = new DefaultListModel<>();
        onlineUsersList = new JList<>(onlineUsersModel);
        onlineUsersList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selected = onlineUsersList.getSelectedValue();
                    if (selected != null && !selected.equals("No hay usuarios en línea")) {
                        selected = selected.replace(" \u25CF", "");
                        openChatTab(selected);
                    }
                }
            }
        });
        sideTabs.addTab("Usuarios", new JScrollPane(onlineUsersList));

        channelsModel = new DefaultListModel<>();
        channelsList = new JList<>(channelsModel);
        channelsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selected = channelsList.getSelectedValue();
                    if (selected != null) {
                        selected = selected.replace(" \u25CF", "").replace(" (No unido)", "");
                        if (!client.getJoinedChannels().contains(selected)) {
                            client.sendMessage("REQUEST_JOIN:" + selected + ":" + client.getUsername());
                        } else {
                            openChatTab("#" + selected);
                        }
                    }
                }
            }
        });
        sideTabs.addTab("Canales", new JScrollPane(channelsList));
        sidePanel.add(sideTabs, BorderLayout.CENTER);
        sidePanel.setPreferredSize(new Dimension(200, 0));

        tabbedPane = new JTabbedPane();
        tabbedPane.addChangeListener(e -> {
            if (tabbedPane.getSelectedIndex() != -1) {
                String selectedTab = tabbedPane.getTitleAt(tabbedPane.getSelectedIndex());
                System.out.println("[" + client.getUsername() + "] Pestaña seleccionada: " + selectedTab);
                unreadMessages.put(selectedTab, 0); // Resetear mensajes no leídos al abrir la pestaña
                updateTabTitle(selectedTab);
                updateChannels();
                updateOnlineUsers();
            }
        });

        inputPanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        JButton sendButton = new JButton("Enviar");
        JButton fileButton = new JButton("Archivo");
        JButton createChannelButton = new JButton("Crear Canal");
        inputPanel.add(messageField, BorderLayout.CENTER);
        JPanel buttonPanel = new JPanel(new GridLayout(1, 3));
        buttonPanel.add(sendButton);
        buttonPanel.add(fileButton);
        buttonPanel.add(createChannelButton);
        inputPanel.add(buttonPanel, BorderLayout.EAST);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidePanel, tabbedPane);
        splitPane.setDividerLocation(200);
        frame.add(splitPane, BorderLayout.CENTER);
        frame.add(inputPanel, BorderLayout.SOUTH);

        JMenuBar menuBar = new JMenuBar();
        JMenu authMenu = new JMenu("Autenticación");
        JMenuItem registerItem = new JMenuItem("Registrar");
        JMenuItem loginItem = new JMenuItem("Iniciar Sesión");
        JMenuItem profileItem = new JMenuItem("Perfil");
        authMenu.add(registerItem);
        authMenu.add(loginItem);
        authMenu.add(profileItem);
        menuBar.add(authMenu);
        frame.setJMenuBar(menuBar);

        sendButton.addActionListener(e -> sendMessage());
        fileButton.addActionListener(e -> sendFile());
        createChannelButton.addActionListener(e -> createChannel());
        registerItem.addActionListener(e -> register());
        loginItem.addActionListener(e -> login());
        profileItem.addActionListener(e -> showProfile());

        toggleInterface(false);
        frame.setVisible(true);
        showWelcomeDialog();
    }

    public void toggleInterface(boolean enabled) {
        messageField.setEnabled(enabled);
        inputPanel.setEnabled(enabled);
        for (Component comp : inputPanel.getComponents()) {
            comp.setEnabled(enabled);
            if (comp instanceof JPanel) {
                for (Component subComp : ((JPanel) comp).getComponents()) {
                    subComp.setEnabled(enabled);
                }
            }
        }
        sidePanel.setEnabled(enabled);
        tabbedPane.setEnabled(enabled);
        frame.revalidate();
        frame.repaint();
    }

    public void showWelcomeDialog() {
        JPanel panel = new JPanel(new GridLayout(2, 1));
        JButton loginButton = new JButton("Iniciar Sesión");
        JButton registerButton = new JButton("Registrarse");
        panel.add(loginButton);
        panel.add(registerButton);

        JDialog welcomeDialog = new JDialog(frame, "Bienvenido", true);
        welcomeDialog.setLayout(new BorderLayout());
        welcomeDialog.add(new JLabel("Por favor, elige una opción:"), BorderLayout.NORTH);
        welcomeDialog.add(panel, BorderLayout.CENTER);
        welcomeDialog.setSize(300, 150);
        welcomeDialog.setLocationRelativeTo(frame);

        loginButton.addActionListener(e -> {
            welcomeDialog.dispose();
            login();
        });
        registerButton.addActionListener(e -> {
            welcomeDialog.dispose();
            register();
        });

        welcomeDialog.setVisible(true);
    }

    private void login() {
        JTextField usernameField = new JTextField();
        JPasswordField passwordField = new JPasswordField();
        JPanel panel = new JPanel(new GridLayout(2, 2));
        panel.add(new JLabel("Usuario:"));
        panel.add(usernameField);
        panel.add(new JLabel("Contraseña:"));
        panel.add(passwordField);

        int result = JOptionPane.showConfirmDialog(frame, panel, "Iniciar Sesión", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword()).trim();
            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Usuario y contraseña son obligatorios", "Error", JOptionPane.ERROR_MESSAGE);
                login();
                return;
            }
            client.setUsername(username);
            client.sendMessage("LOGIN:" + username + ":" + password);
        }
    }

    private void register() {
        JTextField usernameField = new JTextField();
        JTextField emailField = new JTextField();
        JPasswordField passwordField = new JPasswordField();
        JButton photoButton = new JButton("Seleccionar Foto");
        JLabel photoLabel = new JLabel("No hay foto seleccionada");

        JPanel panel = new JPanel(new GridLayout(5, 2));
        panel.add(new JLabel("Usuario:"));
        panel.add(usernameField);
        panel.add(new JLabel("Email:"));
        panel.add(emailField);
        panel.add(new JLabel("Contraseña:"));
        panel.add(passwordField);
        panel.add(new JLabel("Foto de perfil:"));
        panel.add(photoButton);
        panel.add(new JLabel());
        panel.add(photoLabel);

        userPhoto = null;

        photoButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                try {
                    File file = fileChooser.getSelectedFile();
                    userPhoto = Files.readAllBytes(file.toPath());
                    photoLabel.setText("Foto seleccionada: " + file.getName());
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(frame, "Error al cargar la foto: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        int result = JOptionPane.showConfirmDialog(frame, panel, "Registrarse", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            String username = usernameField.getText().trim();
            String email = emailField.getText().trim();
            String password = new String(passwordField.getPassword()).trim();
            String photo = userPhoto != null ? Base64.getEncoder().encodeToString(userPhoto) : "";
            if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Todos los campos son obligatorios", "Error", JOptionPane.ERROR_MESSAGE);
                register();
                return;
            }
            client.setUsername(username);
            client.sendMessage("REGISTER:" + username + ":" + email + ":" + password + ":" + photo);
        }
    }

    private void sendMessage() {
        if (!client.isAuthenticated() || tabbedPane.getSelectedIndex() == -1) return;
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            String destination = tabbedPane.getTitleAt(tabbedPane.getSelectedIndex());
            System.out.println("[" + client.getUsername() + "] Enviando mensaje a " + destination + ": " + message);
            displayMessage(client.getUsername() + ": " + message, destination);
            client.sendMessage("MSG:" + destination + ":" + message);
            messageField.setText("");
        }
    }

    private void sendFile() {
        if (!client.isAuthenticated() || tabbedPane.getSelectedIndex() == -1) return;
        String destination = tabbedPane.getTitleAt(tabbedPane.getSelectedIndex());
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                String encodedFile = Base64.getEncoder().encodeToString(fileBytes);
                client.sendMessage("FILE|" + destination + "|" + client.getUsername() + "|" + file.getName() + "|" + encodedFile);
                displayFileMessage(client.getUsername(), destination, file.getName(), fileBytes);
            } catch (IOException e) {
                displayMessage("Error al enviar archivo: " + e.getMessage());
            }
        }
    }

    private void createChannel() {
        if (!client.isAuthenticated()) return;
        JTextField channelNameField = new JTextField();
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel("Nombre del canal:"), BorderLayout.NORTH);
        panel.add(channelNameField, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(frame, panel, "Crear Canal", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            String channelName = channelNameField.getText().trim();
            if (channelName.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "El nombre del canal es obligatorio", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            client.sendMessage("CREATE_CHANNEL:" + channelName);
            channelAdmins.put(channelName, client.getUsername());
            client.getJoinedChannels().add(channelName);
            openChatTab("#" + channelName);
            client.sendMessage("GET_ALL_CHANNELS");
            client.sendMessage("GET_CHANNEL_HISTORY:" + channelName);
            updateChannels();

            JList<String> usersList = new JList<>(new DefaultListModel<>());
            DefaultListModel<String> usersModel = (DefaultListModel<String>) usersList.getModel();
            for (String user : client.getRegisteredUsers()) {
                if (!user.equals(client.getUsername())) {
                    usersModel.addElement(user);
                }
            }
            usersList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            JPanel userPanel = new JPanel(new BorderLayout());
            userPanel.add(new JLabel("Seleccionar usuarios para el canal " + channelName + ":"), BorderLayout.NORTH);
            userPanel.add(new JScrollPane(usersList), BorderLayout.CENTER);

            int userResult = JOptionPane.showConfirmDialog(frame, userPanel, "Agregar Usuarios", JOptionPane.OK_CANCEL_OPTION);
            if (userResult == JOptionPane.OK_OPTION) {
                java.util.List<String> selectedUsers = usersList.getSelectedValuesList();
                for (String user : selectedUsers) {
                    client.sendMessage("ADD_TO_CHANNEL:" + channelName + ":" + user);
                }
            }
        }
    }

    private void showProfile() {
        if (!client.isAuthenticated()) {
            JOptionPane.showMessageDialog(frame, "Debes iniciar sesión primero", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JPanel profilePanel = new JPanel(new BorderLayout());
        profilePanel.add(new JLabel("Usuario: " + client.getUsername()), BorderLayout.NORTH);
        if (userPhoto != null && userPhoto.length > 0) {
            try {
                ImageIcon photoIcon = new ImageIcon(userPhoto);
                if (photoIcon.getIconWidth() == -1) {
                    profilePanel.add(new JLabel("Error: No se pudo cargar la imagen"), BorderLayout.CENTER);
                    System.err.println("Error: ImageIcon failed to load the image data.");
                } else {
                    Image scaledImage = photoIcon.getImage().getScaledInstance(100, 100, Image.SCALE_SMOOTH);
                    profilePanel.add(new JLabel(new ImageIcon(scaledImage)), BorderLayout.CENTER);
                }
            } catch (Exception e) {
                profilePanel.add(new JLabel("Error al cargar la foto de perfil: " + e.getMessage()), BorderLayout.CENTER);
                System.err.println("Error loading profile photo: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            profilePanel.add(new JLabel("No hay foto de perfil"), BorderLayout.CENTER);
        }
        JButton logoutButton = new JButton("Cerrar Sesión");
        logoutButton.addActionListener(e -> logout());
        profilePanel.add(logoutButton, BorderLayout.SOUTH);
        JOptionPane.showMessageDialog(frame, profilePanel, "Perfil", JOptionPane.INFORMATION_MESSAGE);
    }

    private void logout() {
        SwingUtilities.invokeLater(() -> {
            client.sendMessage("LOGOUT");
            client.setAuthenticated(false);
            client.stop();
            resetInterface();
            showWelcomeDialog();
        });
    }

    public void resetInterface() {
        tabbedPane.removeAll();
        chatPanels.clear();
        displayedMessages.clear();
        unreadMessages.clear();
        tabTitles.clear();
        onlineUsersModel.clear();
        channelsModel.clear();
        toggleInterface(false);
        frame.revalidate();
        frame.repaint();
    }

    public void displayMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(frame, message);
        });
    }

    public void displayMessage(String message, String destination) {
        SwingUtilities.invokeLater(() -> {
            System.out.println("[" + client.getUsername() + "] displayMessage called for " + destination + ": " + message);
            if (!chatPanels.containsKey(destination)) {
                System.out.println("[" + client.getUsername() + "] Creating new chat panel for " + destination);
                JPanel chatPanel = new JPanel();
                chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
                chatPanels.put(destination, chatPanel);
                displayedMessages.put(destination, new HashSet<>());
            }
            JPanel chatPanel = chatPanels.get(destination);
            Set<String> messages = displayedMessages.get(destination);

            String messageKey = message;
            if (message.startsWith("[")) {
                int endOfTimestamp = message.indexOf("] ");
                if (endOfTimestamp != -1) {
                    messageKey = message.substring(0, endOfTimestamp + 2) + message.substring(endOfTimestamp + 2).split(": ", 2)[1];
                }
            }

            if (!messages.contains(messageKey)) {
                System.out.println("[" + client.getUsername() + "] Adding message to chat panel: " + message);
                messages.add(messageKey);
                JLabel messageLabel = new JLabel(message);
                chatPanel.add(messageLabel);
                chatPanel.add(Box.createVerticalStrut(5));
                if (isTabOpen(destination)) {
                    System.out.println("[" + client.getUsername() + "] Tab is open, updating UI for " + destination);
                    chatPanel.revalidate();
                    chatPanel.repaint();
                    Component parent = chatPanel.getParent();
                    if (parent instanceof JViewport) {
                        JViewport viewport = (JViewport) parent;
                        JScrollPane scrollPane = (JScrollPane) viewport.getParent();
                        JScrollBar vertical = scrollPane.getVerticalScrollBar();
                        vertical.setValue(vertical.getMaximum());
                        scrollPane.revalidate();
                        scrollPane.repaint();
                        tabbedPane.revalidate();
                        tabbedPane.repaint();
                    } else {
                        System.out.println("[" + client.getUsername() + "] Parent is not a JViewport: " + parent);
                    }
                } else {
                    System.out.println("[" + client.getUsername() + "] Tab is not open for " + destination);
                }
            } else {
                System.out.println("[" + client.getUsername() + "] Message already displayed: " + message);
            }
        });
    }

    public void displayFileMessage(String sender, String destination, String fileName, byte[] file) {
        SwingUtilities.invokeLater(() -> {
            if (!chatPanels.containsKey(destination)) {
                JPanel chatPanel = new JPanel();
                chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
                chatPanels.put(destination, chatPanel);
                displayedMessages.put(destination, new HashSet<>());
            }
            JPanel chatPanel = chatPanels.get(destination);
            Set<String> messages = displayedMessages.get(destination);

            String messageKey = sender + " envió un archivo: " + fileName;
            if (!messages.contains(messageKey)) {
                messages.add(messageKey);
                JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
                JLabel fileLabel = new JLabel(sender + " envió un archivo: " + fileName);
                JButton downloadButton = new JButton("Descargar");
                downloadButton.addActionListener(e -> {
                    try {
                        Files.write(new File(System.getProperty("user.home") + "/Downloads/" + fileName).toPath(), file);
                        JOptionPane.showMessageDialog(frame, "Archivo descargado en Descargas");
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(frame, "Error al descargar: " + ex.getMessage());
                    }
                });
                filePanel.add(fileLabel);
                filePanel.add(downloadButton);
                chatPanel.add(filePanel);
                chatPanel.add(Box.createVerticalStrut(5));
                if (isTabOpen(destination)) {
                    chatPanel.revalidate();
                    chatPanel.repaint();
                    Component parent = chatPanel.getParent();
                    if (parent instanceof JViewport) {
                        JViewport viewport = (JViewport) parent;
                        JScrollPane scrollPane = (JScrollPane) viewport.getParent();
                        JScrollBar vertical = scrollPane.getVerticalScrollBar();
                        vertical.setValue(vertical.getMaximum());
                        scrollPane.revalidate();
                        scrollPane.repaint();
                        tabbedPane.revalidate();
                        tabbedPane.repaint();
                    }
                }
            }
        });
    }

    public void notifyNewMessage(String destination) {
        SwingUtilities.invokeLater(() -> {
            System.out.println("[" + client.getUsername() + "] notifyNewMessage called for " + destination);
            unreadMessages.put(destination, unreadMessages.getOrDefault(destination, 0) + 1);
            System.out.println("[" + client.getUsername() + "] Updated unread count for " + destination + ": " + unreadMessages.get(destination));
            updateTabTitle(destination);
            if (destination.startsWith("#")) {
                updateChannels();
            } else {
                updateOnlineUsers();
            }
        });
    }

    public boolean isTabOpen(String destination) {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (tabbedPane.getTitleAt(i).equals(destination)) {
                return true;
            }
        }
        return false;
    }

    public boolean isChatOpen(String destination) {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (tabbedPane.getTitleAt(i).equals(destination)) {
                return tabbedPane.getSelectedIndex() == i;
            }
        }
        return false;
    }

    public void updateOnlineUsers() {
        SwingUtilities.invokeLater(() -> {
            onlineUsersModel.clear();
            if (client.getOnlineUsers().isEmpty()) {
                onlineUsersModel.addElement("No hay usuarios en línea");
            } else {
                for (String user : client.getOnlineUsers()) {
                    if (!user.equals(client.getUsername())) {
                        int unread = unreadMessages.getOrDefault(user, 0);
                        String display = user + (unread > 0 ? " \u25CF" : "");
                        onlineUsersModel.addElement(display);
                    }
                }
            }
            onlineUsersList.revalidate();
            onlineUsersList.repaint();
            sidePanel.revalidate();
            sidePanel.repaint();
        });
    }

    public void updateChannels() {
        SwingUtilities.invokeLater(() -> {
            System.out.println("[" + client.getUsername() + "] updateChannels called");
            channelsModel.clear();
            for (String channel : client.getAllChannels()) {
                String channelKey = "#" + channel;
                boolean isJoined = client.getJoinedChannels().contains(channel);
                int unread = unreadMessages.getOrDefault(channelKey, 0);
                String display = channel + (isJoined ? "" : " (No unido)") + (unread > 0 ? " \u25CF" : "");
                System.out.println("[" + client.getUsername() + "] Adding channel to list: " + display);
                channelsModel.addElement(display);
            }
            channelsList.revalidate();
            channelsList.repaint();
            sidePanel.revalidate();
            sidePanel.repaint();
        });
    }

    private void openChatTab(String destination) {
        if (!isTabOpen(destination)) {
            if (chatPanels.containsKey(destination)) {
                chatPanels.get(destination).removeAll();
                displayedMessages.get(destination).clear();
            } else {
                JPanel chatPanel = new JPanel();
                chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
                chatPanels.put(destination, chatPanel);
                displayedMessages.put(destination, new HashSet<>());
            }

            JPanel chatPanel = chatPanels.get(destination);
            JPanel tabPanel = new JPanel(new BorderLayout());
            JLabel titleLabel = new JLabel(destination);
            tabPanel.add(titleLabel, BorderLayout.NORTH);
            tabPanel.add(new JScrollPane(chatPanel), BorderLayout.CENTER);

            if (destination.startsWith("#") && channelAdmins.getOrDefault(destination.substring(1), "").equals(client.getUsername())) {
                JButton manageButton = new JButton("Agregar Usuarios");
                manageButton.addActionListener(e -> manageChannel(destination.substring(1)));
                tabPanel.add(manageButton, BorderLayout.SOUTH);
            }

            JButton closeButton = new JButton("X");
            closeButton.addActionListener(e -> {
                int index = tabbedPane.indexOfTab(destination);
                if (index != -1) {
                    tabbedPane.remove(index);
                    tabTitles.remove(destination);
                    updateOnlineUsers();
                    updateChannels();
                }
            });
            JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            titlePanel.add(titleLabel);
            titlePanel.add(closeButton);
            tabbedPane.addTab(destination, tabPanel);
            tabTitles.put(destination, titleLabel);
            unreadMessages.putIfAbsent(destination, 0);
            tabbedPane.setTabComponentAt(tabbedPane.getTabCount() - 1, titlePanel);

            if (destination.startsWith("#")) {
                client.sendMessage("GET_CHANNEL_HISTORY:" + destination.substring(1));
            } else {
                client.sendMessage("GET_CHAT_HISTORY:" + destination);
            }
        }
        tabbedPane.setSelectedIndex(tabbedPane.indexOfTab(destination));
        unreadMessages.put(destination, 0); // Resetear mensajes no leídos al abrir
        updateTabTitle(destination);
        updateOnlineUsers();
        updateChannels();
    }

    private void updateTabTitle(String destination) {
        SwingUtilities.invokeLater(() -> {
            int index = tabbedPane.indexOfTab(destination);
            if (index != -1) {
                int unread = unreadMessages.getOrDefault(destination, 0);
                JLabel titleLabel = tabTitles.get(destination);
                if (titleLabel != null) {
                    titleLabel.setText(destination + (unread > 0 ? " (" + unread + ")" : ""));
                    titleLabel.setForeground(unread > 0 ? Color.RED : Color.BLACK);
                }
            }
        });
    }

    private void manageChannel(String channelName) {
        JList<String> usersList = new JList<>(new DefaultListModel<String>());
        DefaultListModel<String> usersModel = (DefaultListModel<String>) usersList.getModel();
        for (String user : client.getRegisteredUsers()) {
            if (!user.equals(client.getUsername()) && !isUserInChannel(user, channelName)) {
                usersModel.addElement(user);
            }
        }
        usersList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel("Agregar usuarios al canal " + channelName + ":"), BorderLayout.NORTH);
        panel.add(new JScrollPane(usersList), BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(frame, panel, "Gestionar Canal", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            java.util.List<String> selectedUsers = usersList.getSelectedValuesList();
            for (String user : selectedUsers) {
                client.sendMessage("ADD_TO_CHANNEL:" + channelName + ":" + user);
            }
        }
    }

    private boolean isUserInChannel(String username, String channelName) {
        return false; // Placeholder: Replace with actual logic if needed
    }

    public void showChannelRequest(String channel, String requester) {
        SwingUtilities.invokeLater(() -> {
            int response = JOptionPane.showConfirmDialog(frame, requester + " solicita unirse al canal " + channel + ". ¿Aceptar?", "Solicitud de Canal", JOptionPane.YES_NO_OPTION);
            if (response == JOptionPane.YES_OPTION) {
                client.sendMessage("APPROVE_JOIN:" + channel + ":" + requester);
            } else {
                client.sendMessage("REJECT_JOIN:" + channel + ":" + requester);
            }
        });
    }

    public void setUserPhoto(byte[] photo) {
        this.userPhoto = photo;
    }
}