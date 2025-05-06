package com.dirac.spaceinvaders.game;

import java.awt.Color; // Para el color
import java.awt.Graphics; // Para dibujar
// import java.io.Serializable; // Necesario para enviar por red

/**
 * Clase Bullet: Representa un proyectil disparado por un jugador o un alien.
 * Hereda de GameObject y añade información sobre quién lo disparó y su velocidad/dirección.
 * Es Serializable para ser incluida en GameState.
 */
public class Bullet extends GameObject {
    private static final long serialVersionUID = 1L; // Versión para serialización

    // --- Constantes ---
    public static final int BULLET_WIDTH = 4;    // Ancho del proyectil
    public static final int BULLET_HEIGHT = 10;   // Alto del proyectil
    public static final int PLAYER_BULLET_SPEED = -8; // Velocidad hacia arriba (negativa en Y)
    public static final int ALIEN_BULLET_SPEED = 5;  // Velocidad hacia abajo (positiva en Y)

    // --- Atributos Específicos de la Bala ---
    private final int ownerId; // ID del jugador que disparó (-1 si es de un alien)
    private final int speedY;  // Velocidad vertical (negativa para jugador, positiva para alien)
    private final Color color; // Color de la bala

    // --- Constructor ---
    /**
     * Constructor para una nueva bala.
     * @param x Posición inicial X (generalmente centro del disparador).
     * @param y Posición inicial Y (generalmente borde del disparador).
     * @param ownerId ID del jugador que dispara, o -1 si es un alien.
     */
    public Bullet(int x, int y, int ownerId) {
        // Llama al constructor de GameObject.
        super(x, y, BULLET_WIDTH, BULLET_HEIGHT);
        this.ownerId = ownerId;

        // Determina la velocidad y el color según quién disparó.
        if (ownerId == -1) { // Bala de Alien
            this.speedY = ALIEN_BULLET_SPEED;
            this.color = Color.RED; // Balas alienígenas rojas
        } else { // Bala de Jugador
            this.speedY = PLAYER_BULLET_SPEED;
            this.color = Color.YELLOW; // Balas de jugador amarillas
        }
    }

    // --- Movimiento (Controlado por el Servidor) ---
    /**
     * Actualiza la posición de la bala moviéndola verticalmente según su velocidad.
     */
    public void move() {
        this.y += this.speedY;
    }

    // --- Getters ---
    /**
     * Obtiene el ID del dueño de la bala.
     * @return El ID del jugador, o -1 si es de un alien.
     */
    public int getOwnerId() { return ownerId; }

    /**
     * Comprueba si la bala fue disparada por un jugador.
     * @return true si fue disparada por un jugador, false si fue por un alien.
     */
    public boolean isPlayerBullet() { return ownerId != -1; }

    // --- Dibujo ---
    /**
     * Dibuja la bala como un pequeño rectángulo vertical.
     * @param g Objeto Graphics donde se dibuja.
     */
    @Override
    public void draw(Graphics g) {
        // Establece el color de la bala.
        g.setColor(this.color);
        // Dibuja un rectángulo relleno.
        g.fillRect(x, y, width, height);
    }
}