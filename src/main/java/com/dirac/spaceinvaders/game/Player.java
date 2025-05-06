package com.dirac.spaceinvaders.game;

import java.awt.Color; // Para el color de la nave
import java.awt.Graphics; // Para dibujar
import java.io.Serializable; // Necesario para enviar por red

/**
 * Clase Player: Representa la nave controlada por un jugador.
 * Hereda de GameObject y añade un ID único y una puntuación.
 * Es Serializable para ser incluida en GameState.
 */
public class Player extends GameObject implements Serializable {
    private static final long serialVersionUID = 1L; // Versión para serialización

    // --- Constantes ---
    public static final int PLAYER_WIDTH = 30; // Ancho estándar del jugador
    public static final int PLAYER_HEIGHT = 20; // Alto estándar del jugador
    public static final int PLAYER_SPEED = 5; // Píxeles que se mueve por acción
    public static final int INITIAL_LIVES = 3; // ← número de vidas iniciales

    // --- Atributos Específicos del Jugador ---
    private int playerId; // Identificador único para este jugador
    private int score;    // Puntuación actual del jugador
    private int lives;    // ← nueva variable de vidas
    private long respawnTimestamp = 0L;              // instante en que reapareció
    private static final long INVULNERABILITY_MS = 2000; // 2 segundos
    private Color color;  // Color para distinguir naves (opcional)

    // --- Constructor ---
    /**
     * Constructor para un nuevo jugador.
     * @param x Posición inicial X.
     * @param y Posición inicial Y.
     * @param playerId ID único asignado por el servidor.
     * @param color Color para representar a este jugador.
     */
    public Player(int x, int y, int playerId, Color color) {
        super(x, y, PLAYER_WIDTH, PLAYER_HEIGHT);
        this.playerId = playerId;
        this.score = 0;
        this.color = color;
        this.lives = INITIAL_LIVES;
        this.respawnTimestamp = System.currentTimeMillis(); // al crear, darle invulnerabilidad breve
    }

    // --- Métodos de Movimiento (Ejecutados por el Servidor) ---
    /**
     * Mueve el jugador hacia la izquierda, respetando los límites del área de juego.
     * @param minX Límite izquierdo del área de juego.
     */
    public void moveLeft(int minX) {
        // Calcula la nueva posición.
        int nextX = this.x - PLAYER_SPEED;
        // Aplica la nueva posición solo si no se sale del límite izquierdo.
        if (nextX >= minX) {
            this.x = nextX;
        } else {
            this.x = minX; // Si se pasaría, lo deja en el borde.
        }
    }

    public void loseLife() {
        if (lives > 0) {
            lives--;
            if (lives > 0) {
                // vuelve invulnerable durante INVULNERABILITY_MS
                respawnTimestamp = System.currentTimeMillis();
            } else {
                // vida 0: se considerará inactivo en el Servidor
                setActive(false);
            }
        }
    }


    /**
     * Devuelve las vidas restantes.
     */
    public int getLives() {
        return lives;
    }
    /**
     * Mueve el jugador hacia la derecha, respetando los límites del área de juego.
     * @param maxX Límite derecho del área de juego (borde izquierdo de la pantalla + ancho pantalla - ancho jugador).
     */
    public void moveRight(int maxX) {
        // Calcula la nueva posición.
        int nextX = this.x + PLAYER_SPEED;
        // El límite derecho se calcula como ancho_pantalla - ancho_jugador
        if (nextX <= maxX) {
            this.x = nextX;
        } else {
            this.x = maxX; // Si se pasaría, lo deja en el borde.
        }
    }

    public boolean isInvulnerable() {
        return System.currentTimeMillis() - respawnTimestamp < INVULNERABILITY_MS;
    }

    public void moveUp(int minY) {
        int nextY = this.y - PLAYER_SPEED;
        this.y = Math.max(minY, nextY);
    }

    // Nuevo: bajar
    public void moveDown(int maxY) {
        int nextY = this.y + PLAYER_SPEED;
        this.y = Math.min(maxY, nextY);
    }

    // --- Métodos de Puntuación ---
    /**
     * Añade puntos a la puntuación del jugador.
     * @param points Puntos a añadir.
     */
    public void addScore(int points) {
        this.score += points;
    }

    // --- Getters ---
    /**
     * Obtiene el ID del jugador.
     * @return El ID único del jugador.
     */
    public int getPlayerId() { return playerId; }

    /**
     * Obtiene la puntuación actual del jugador.
     * @return La puntuación.
     */
    public int getScore() { return score; }

    /**
     * Obtiene el color asignado a este jugador.
     * @return El objeto Color.
     */
    public Color getColor() { return color; }

    // --- Dibujo ---
    /**
     * Dibuja la nave del jugador como un rectángulo del color asignado.
     * @param g Objeto Graphics donde se dibuja.
     */
    @Override
    public void draw(Graphics g) {
        // Establece el color del pincel al color del jugador.
        g.setColor(this.color);
        // Dibuja un rectángulo relleno en la posición y tamaño del jugador.
        g.fillRect(x, y, width, height);
        // Podríamos dibujar algo más complejo o cargar una imagen aquí.
        // Ejemplo: Dibujar un pequeño "cañón"
        g.setColor(Color.YELLOW);
        g.fillRect(x + width / 2 - 2, y - 4, 4, 4);
    }
}