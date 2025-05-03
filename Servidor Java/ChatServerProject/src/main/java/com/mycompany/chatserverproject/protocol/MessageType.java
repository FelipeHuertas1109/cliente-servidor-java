package com.mycompany.chatserverproject.protocol;

public enum MessageType {
    PING,
    PONG,
    SRV_JOIN,       // un servidor se une
    SYNC_USERS,     // pedir/enviar lista de usuarios
    SYNC_FILES,     // pedir/enviar metadatos de archivos
    USER_REGISTER,  // broadcast de nuevo usuario
    MSG_REMOTE      // reenvío de mensaje entre servidores
}
