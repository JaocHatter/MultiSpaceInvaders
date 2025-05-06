package com.dirac.spaceinvaders.game;

import java.io.Serializable; // Necesario para enviar por red
import java.util.ArrayList; // Para las listas de objetos
import java.util.List;      // Interfaz List
import java.util.Map;       // Para el mapa de puntuaciones

/**
 * Clase GameState: Representa el estado completo del juego en un momento dado.
 * Contiene listas de todos los objetos activos (jugadores, aliens, balas),
 * el nivel actual, puntuaciones y estado de fin de juego.
 * Es Serializable para poder ser enviado desde el servidor a los clientes.
 */
public class GameState implements Serializable {
    private static final long serialVersionUID = 1L; // Versión para serialización

    // --- Atributos del Estado ---
    // Listas de los objetos actualmente en el juego. Se usan ArrayList por ser Serializable.
    private List<Player> players;
    private List<Alien> aliens;
    private List<Bullet> bullets;

    // Nivel actual del juego.
    private int level;

    // Puntuaciones de los jugadores (ID del jugador -> Puntuación).
    // Usamos Map directamente ya que las implementaciones comunes (HashMap) son Serializable.
    private Map<Integer, Integer> scores;

    // Bandera que indica si el juego ha terminado.
    private boolean gameOver;

    // Mensaje de estado (ej. "Nivel 1", "Game Over").
    private String statusMessage;

    // --- Constructor ---
    /**
     * Constructor para crear un nuevo objeto GameState.
     * Inicializa las listas y establece valores predeterminados.
     */
    public GameState() {
        this.players = new ArrayList<>();
        this.aliens = new ArrayList<>();
        this.bullets = new ArrayList<>();
        this.level = 1; // El juego empieza en el nivel 1
        this.gameOver = false; // El juego no empieza terminado
        this.statusMessage = "Esperando jugadores..."; // Mensaje inicial
    }

    // --- Getters (Necesarios para que los clientes lean el estado) ---
    public List<Player> getPlayers() { return players; }
    public List<Alien> getAliens() { return aliens; }
    public List<Bullet> getBullets() { return bullets; }
    public int getLevel() { return level; }
    public Map<Integer, Integer> getScores() { return scores; }
    public boolean isGameOver() { return gameOver; }
    public String getStatusMessage() { return statusMessage; }

    // --- Setters (Usados principalmente por el Servidor para actualizar el estado) ---
    // Nota: Para las listas, es común obtener la lista con get() y modificarla directamente
    // en el servidor (añadir/eliminar elementos). No siempre se necesitan setters para las listas.
    public void setPlayers(List<Player> players) { this.players = players; }
    public void setAliens(List<Alien> aliens) { this.aliens = aliens; }
    public void setBullets(List<Bullet> bullets) { this.bullets = bullets; }
    public void setLevel(int level) { this.level = level; }
    public void setScores(Map<Integer, Integer> scores) { this.scores = scores; }
    public void setGameOver(boolean gameOver) { this.gameOver = gameOver; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }

    // --- Métodos de Ayuda (Ejecutados en el Servidor) ---
    /**
     * Elimina los objetos inactivos (balas impactadas, aliens destruidos) de las listas.
     * Se debe llamar periódicamente en el bucle del servidor.
     */
    public void removeInactiveObjects() {
        // Elimina balas inactivas (más eficiente iterar hacia atrás al eliminar)
        for (int i = bullets.size() - 1; i >= 0; i--) {
            if (!bullets.get(i).isActive()) {
                bullets.remove(i);
            }
        }
        // Elimina aliens inactivos
        for (int i = aliens.size() - 1; i >= 0; i--) {
            if (!aliens.get(i).isActive()) {
                aliens.remove(i);
            }
        }
        // Podríamos necesitar eliminar jugadores si implementamos desconexión o muerte
        // for (int i = players.size() - 1; i >= 0; i--) {
        //     if (!players.get(i).isActive()) { // Suponiendo que Player tuviera estado 'active'
        //         players.remove(i);
        //     }
        // }
    }
}