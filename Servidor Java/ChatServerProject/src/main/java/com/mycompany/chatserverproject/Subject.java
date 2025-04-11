/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Estudiante_MCA
 */

package com.mycompany.chatserverproject;

public interface Subject {
    void addObserver(MessageObserver observer);
    void removeObserver(MessageObserver observer);
    void notifyObservers(String message);
}