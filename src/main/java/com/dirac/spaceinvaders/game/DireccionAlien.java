package com.dirac.spaceinvaders.game;

/**
 * Enumeración DireccionAlien: Define las posibles direcciones de movimiento
 * horizontal de los aliens (izquierda o derecha).
 * No necesita ser Serializable directamente si no se envía sola, pero los
 * objetos que la usen (Alien) sí deben serlo.
 */
public enum DireccionAlien {
    IZQUIERDA, // Moviéndose hacia la izquierda
    DERECHA    // Moviéndose hacia la derecha
}