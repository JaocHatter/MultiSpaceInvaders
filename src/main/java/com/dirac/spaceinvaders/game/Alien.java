package com.dirac.spaceinvaders.game;

import java.awt.Color; // Para el color
import java.awt.Graphics; // Para dibujar
// import java.io.Serializable; // Necesario para enviar por red

/**
 * Clase Alien: Representa una nave enemiga (invasor).
 * Hereda de GameObject y añade tipo (para puntuación/apariencia) y velocidad.
 * Es Serializable para ser incluida en GameState.
 */
public class Alien extends GameObject {
    private static final long serialVersionUID = 1L; // Versión para serialización

    // --- Constantes ---
    public static final int ALIEN_WIDTH = 25;  // Ancho estándar
    public static final int ALIEN_HEIGHT = 18; // Alto estándar
    public static final int ALIEN_DROP_DISTANCE = 10; // Distancia que bajan al cambiar de dirección

    // --- Tipos de Alien y sus Puntos ---
    public static final int TIPO_GRANDE = 0; // Fila inferior (según PDF)
    public static final int TIPO_MEDIANO = 1; // Fila intermedia
    public static final int TIPO_PEQUENO = 2; // Fila superior
    public static final int[] PUNTOS_POR_TIPO = {10, 20, 30}; // Puntos según PDF

    // --- Atributos Específicos del Alien ---
    private int tipo; // Tipo de alien (0, 1, o 2)
    private int puntos; // Puntos que otorga al ser destruido
    private final Color color; // Color según el tipo

    // --- Constructor ---
    /**
     * Constructor para un nuevo alien.
     * @param x Posición inicial X.
     * @param y Posición inicial Y.
     * @param tipo Tipo de alien (TIPO_GRANDE, TIPO_MEDIANO, TIPO_PEQUENO).
     */
    public Alien(int x, int y, int tipo) {
        super(x, y, ALIEN_WIDTH, ALIEN_HEIGHT);
        this.tipo = tipo;
        // Asigna puntos y color según el tipo.
        if (tipo >= 0 && tipo < PUNTOS_POR_TIPO.length) {
            this.puntos = PUNTOS_POR_TIPO[tipo];
            // Asigna colores distintivos (pueden mejorarse)
            switch (tipo) {
                case TIPO_GRANDE: this.color = Color.GREEN; break;
                case TIPO_MEDIANO: this.color = Color.YELLOW; break;
                case TIPO_PEQUENO: this.color = Color.CYAN; break;
                default: this.color = Color.WHITE; // Color por defecto
            }
        } else {
            // Tipo inválido, usa valores por defecto.
            this.puntos = 10;
            this.color = Color.WHITE;
            System.err.println("Advertencia: Tipo de alien inválido: " + tipo);
        }
    }

    // --- Movimiento (Controlado por el Servidor) ---
    /**
     * Mueve el alien horizontalmente.
     * @param dx Cantidad de píxeles a mover (positivo para derecha, negativo para izquierda).
     */
    public void moverHorizontal(int dx) {
        this.x += dx;
    }

    /**
     * Mueve el alien verticalmente hacia abajo.
     * Usa la distancia estándar ALIEN_DROP_DISTANCE.
     */
    public void moverAbajo() {
        this.y += ALIEN_DROP_DISTANCE;
    }

    // --- Getters ---
    /**
     * Obtiene los puntos que otorga este alien al ser destruido.
     * @return Los puntos del alien.
     */
    public int getPuntos() { return puntos; }

    /**
     * Obtiene el tipo de este alien.
     * @return El tipo (0, 1, o 2).
     */
    public int getTipo() { return tipo; }

    // --- Dibujo ---
    /**
     * Dibuja el alien como un rectángulo del color correspondiente a su tipo.
     * @param g Objeto Graphics donde se dibuja.
     */
    @Override
    public void draw(Graphics g) {
        // Establece el color del pincel.
        g.setColor(this.color);
        // Dibuja un rectángulo relleno.
        g.fillRect(x, y, width, height);
        // Podríamos dibujar formas más parecidas a los invasores aquí.
        // Ejemplo simple: dibujar "ojos"
        g.setColor(Color.BLACK);
        g.fillRect(x + 5, y + 5, 4, 4);
        g.fillRect(x + width - 9, y + 5, 4, 4);
    }
}