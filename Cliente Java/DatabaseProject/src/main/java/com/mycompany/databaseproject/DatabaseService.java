/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Estudiante_MCA
 */
package com.mycompany.databaseproject;

public interface DatabaseService {
    void saveMessage(String sender, String message, byte[] file);
    void close();
}