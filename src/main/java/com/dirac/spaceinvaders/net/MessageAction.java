package com.dirac.spaceinvaders.net;

import java.io.Serializable; // Necesario para enviar por red

/**
 * Enumeración MessageAction: Define las acciones que un cliente puede enviar al servidor.
 * Es Serializable para poder ser enviada a través de ObjectOutputStream.
 */
public enum MessageAction implements Serializable {
    // Acciones del jugador
    MOVE_LEFT,    // Mover nave a la izquierda
    MOVE_RIGHT,   // Mover nave a la derecha
    MOVE_UP,          // ← nuevo
    MOVE_DOWN,        // ← nuevo
    SHOOT,        // Disparar un proyectil

    // Acciones de conexión/desconexión (podrían manejarse implícitamente,
    // pero es bueno tenerlas si se necesita lógica específica)
    CONNECT,      // Mensaje inicial al conectar (opcional)
    DISCONNECT    // Notificación de desconexión (opcional, el servidor suele detectarlo por IOException)
}