package com.dirac.spaceinvaders.game;

import java.awt.Graphics; // Para dibujar
import java.awt.Rectangle; // Para colisiones
import java.io.Serializable; // Necesario para enviar por red

/**
 * Clase abstracta GameObject: Representa cualquier objeto en el juego
 * que tiene una posición, dimensiones y puede ser dibujado.
 * Es Serializable para poder ser incluido en GameState y enviado por red.
 */
public abstract class GameObject implements Serializable {
    // Número de versión para la serialización. Importante si la clase cambia.
    private static final long serialVersionUID = 1L;

    // --- Atributos ---
    // Posición del objeto en el plano 2D (coordenada superior izquierda).
    protected int x;
    protected int y;

    // Dimensiones del objeto.
    protected int width;
    protected int height;

    // Indica si el objeto está activo o debe ser eliminado (ej. bala impactada, alien muerto).
    protected boolean active = true;

    // --- Constructor ---
    /**
     * Constructor para inicializar un GameObject.
     * @param x Posición inicial en el eje X.
     * @param y Posición inicial en el eje Y.
     * @param width Ancho del objeto.
     * @param height Alto del objeto.
     */
    public GameObject(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    // --- Métodos Abstractos ---
    /**
     * Método abstracto para dibujar el objeto en el contexto gráfico proporcionado.
     * Cada subclase (Player, Alien, Bullet) implementará su propia forma de dibujarse.
     * @param g Objeto Graphics donde se realizará el dibujo.
     */
    public abstract void draw(Graphics g);

    // --- Métodos Concretos ---
    /**
     * Obtiene la posición X actual del objeto.
     * @return La coordenada X.
     */
    public int getX() { return x; }

    /**
     * Establece la posición X del objeto.
     * @param x La nueva coordenada X.
     */
    public void setX(int x) { this.x = x; }

    /**
     * Obtiene la posición Y actual del objeto.
     * @return La coordenada Y.
     */
    public int getY() { return y; }

    /**
     * Establece la posición Y del objeto.
     * @param y La nueva coordenada Y.
     */
    public void setY(int y) { this.y = y; }

    /**
     * Obtiene el ancho del objeto.
     * @return El ancho.
     */
    public int getWidth() { return width; }

    /**
     * Obtiene la altura del objeto.
     * @return La altura.
     */
    public int getHeight() { return height; }

    /**
     * Comprueba si el objeto está activo.
     * @return true si está activo, false en caso contrario.
     */
    public boolean isActive() { return active; }

    /**
     * Marca el objeto como inactivo (para ser eliminado posteriormente).
     */
    public void setActive(boolean active) { this.active = active; }

    /**
     * Devuelve un objeto Rectangle que representa los límites (bounds)
     * de este GameObject. Útil para la detección de colisiones.
     * @return Un Rectangle con la posición y dimensiones del objeto.
     */
    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }

    /**
     * Comprueba si este GameObject colisiona con otro GameObject.
     * Utiliza la intersección de sus rectángulos delimitadores.
     * @param other El otro GameObject con el que comprobar la colisión.
     * @return true si hay colisión, false en caso contrario.
     */
    public boolean collidesWith(GameObject other) {
        // Solo colisiona si ambos objetos están activos.
        if (!this.isActive() || !other.isActive()) {
            return false;
        }
        // Comprueba si los rectángulos se solapan.
        return this.getBounds().intersects(other.getBounds());
    }
}