package com.dirac.spaceinvaders.game;

import java.awt.Color; // Panel de Swing para dibujar
import java.awt.Dimension;     // Colores
import java.awt.Font; // Para especificar el tamaño del panel
import java.awt.Graphics;      // Para dibujar texto (puntuaciones, mensajes)
import java.util.ArrayList;  // Objeto para dibujar
import java.util.Collections;
import java.util.List; // Para sincronizar listas si es necesario
import java.util.Map;      // Interfaz List
import javax.swing.JPanel;       // Interfaz Map

/**
 * Clase GamePanel: Es el lienzo donde se dibuja el estado actual del juego.
 * Extiende JPanel y sobrescribe el método paintComponent para realizar el dibujo.
 */
public class GamePanel extends JPanel {

    // --- Constantes ---
    public static final int ANCHO_JUEGO = 800; // Ancho del área de juego en píxeles
    public static final int ALTO_JUEGO = 600;  // Alto del área de juego en píxeles
    private static final Font SCORE_FONT = new Font("Monospaced", Font.BOLD, 14); // Fuente para puntuaciones
    private static final Font STATUS_FONT = new Font("Monospaced", Font.BOLD, 24); // Fuente para mensajes grandes

    // --- Atributos ---
    // Referencia al último estado del juego recibido del servidor.
    // 'volatile' asegura que los cambios hechos por el hilo de red sean visibles
    // por el hilo de Swing (EDT) que llama a paintComponent.
    private volatile GameState currentState;

    // --- Constructor ---
    /**
     * Constructor del GamePanel. Establece el tamaño preferido y el color de fondo.
     */
    public GamePanel() {
        // Establece el tamaño preferido del panel.
        setPreferredSize(new Dimension(ANCHO_JUEGO, ALTO_JUEGO));
        // Establece el color de fondo del área de juego.
        setBackground(Color.BLACK);
        // Permite que el panel reciba eventos de teclado (si se usa KeyListener aquí).
        setFocusable(true);
    }

    // --- Actualización del Estado ---
    /**
     * Actualiza el estado del juego que se va a dibujar.
     * Este método será llamado por el hilo del cliente que recibe datos del servidor.
     * @param newState El nuevo GameState recibido.
     */
    public void updateGameState(GameState newState) {
        this.currentState = newState;
        // Solicita que el panel se redibuje lo antes posible.
        // Swing se encargará de llamar a paintComponent en el hilo de eventos (EDT).
        repaint();
    }

    // --- Dibujo Principal ---
    /**
     * Método clave de Swing para dibujar el contenido del panel.
     * Es llamado automáticamente por Swing cuando se necesita redibujar (ej. al llamar a repaint()).
     * NUNCA se debe llamar a este método directamente.
     * @param g El contexto gráfico proporcionado por Swing para dibujar.
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        GameState state = this.currentState;

        if (state == null) {
            g.setColor(Color.WHITE);
            g.setFont(STATUS_FONT); // Ensure STATUS_FONT is defined
            g.drawString("Esperando conexión...", ANCHO_JUEGO / 2 - 150, ALTO_JUEGO / 2);
            return;
        }

        // --- Dibuja los Elementos del Juego ---
        List<Player> players = state.getPlayers();
        if (players != null) {
            List<Player> safePlayers = Collections.synchronizedList(new ArrayList<>(players));
            synchronized (safePlayers) {
                 for (Player player : safePlayers) {
                    if (player != null && player.isActive()) {
                        player.draw(g);
                    }
                 }
            }
        }

        List<Alien> aliens = state.getAliens();
         if (aliens != null) {
            List<Alien> safeAliens = Collections.synchronizedList(new ArrayList<>(aliens));
             synchronized (safeAliens) {
                 for (Alien alien : safeAliens) {
                     if (alien != null && alien.isActive()) {
                         alien.draw(g);
                     }
                 }
             }
         }

        // --- Draw Boss ---
        Boss boss = state.getBoss();
        if (boss != null && boss.isActive()) {
            boss.draw(g); // The Boss class's draw method handles drawing the boss and health bar
        }
        // --- End Draw Boss ---

         List<Bullet> bullets = state.getBullets();
         if (bullets != null) {
             List<Bullet> safeBullets = Collections.synchronizedList(new ArrayList<>(bullets));
             synchronized (safeBullets) {
                 for (Bullet bullet : safeBullets) {
                    if (bullet != null && bullet.isActive()) {
                        bullet.draw(g);
                    }
                 }
             }
         }

        drawScores(g, state.getScores(), state.getPlayers());
        drawGameInfo(g, state.getLevel(), state.getStatusMessage());

        if (state.isGameOver()) {
            drawGameOver(g); // Ensure drawGameOver is defined
        }
    }

    /**
     * Dibuja las puntuaciones de los jugadores en la esquina superior izquierda.
     * @param g Contexto gráfico.
     * @param scores Mapa de ID de jugador a puntuación.
     * @param players Lista de jugadores (para obtener colores si es necesario).
     */
    // En GamePanel.java, localiza el método drawScores(...) y reemplázalo por esto:

    /**
     * Dibuja las puntuaciones y vidas de los jugadores en la esquina superior izquierda.
     * @param g Contexto gráfico.
     * @param scores Mapa de ID de jugador a puntuación.
     * @param players Lista de jugadores (para obtener colores y vidas).
     */
    private void drawScores(Graphics g, Map<Integer, Integer> scores, List<Player> players) {
        if (scores == null || players == null) return;

        g.setFont(SCORE_FONT);
        int yPos = 20; // Y inicial para la primera línea

        // Copia segura de jugadores para buscar color y vidas
        List<Player> safePlayers = Collections.synchronizedList(new ArrayList<>(players));

        for (Map.Entry<Integer, Integer> entry : scores.entrySet()) {
            int playerId = entry.getKey();
            int score = entry.getValue();
            Color playerColor = Color.WHITE;
            int lives = 0;

            // Buscar el jugador para sacar su color y vidas
            synchronized (safePlayers) {
                for (Player p : safePlayers) {
                    if (p.getPlayerId() == playerId) {
                        playerColor = p.getColor();
                        lives = p.getLives();  // obtenemos vidas actuales
                        break;
                    }
                }
            }

            // Dibujar texto: "Jugador X: Puntos (Vidas: Y)"
            g.setColor(playerColor);
            String text = String.format("Jugador %d: %d pts (Vidas: %d)", playerId, score, lives);
            g.drawString(text, 10, yPos);
            yPos += 18; // espacio entre líneas
        }
    }


    /**
     * Dibuja información general del juego como el nivel y mensajes de estado.
     * @param g Contexto gráfico.
     * @param level Nivel actual.
     * @param statusMessage Mensaje de estado actual.
     */
    private void drawGameInfo(Graphics g, int level, String statusMessage) {
        g.setFont(SCORE_FONT); // Reutiliza la fuente de puntuación
        g.setColor(Color.LIGHT_GRAY);

        // Dibuja el nivel en la esquina superior derecha
        String levelText = "Nivel: " + level;
        int levelWidth = g.getFontMetrics().stringWidth(levelText);
        g.drawString(levelText, ANCHO_JUEGO - levelWidth - 10, 20);

        // Dibuja el mensaje de estado centrado en la parte superior
        if (statusMessage != null && !statusMessage.isEmpty()) {
             g.setFont(STATUS_FONT); // Fuente más grande para mensajes
             g.setColor(Color.ORANGE);
             int statusWidth = g.getFontMetrics().stringWidth(statusMessage);
             g.drawString(statusMessage, (ANCHO_JUEGO - statusWidth) / 2, 40);
        }
    }

    /**
     * Dibuja el mensaje de "Game Over" centrado en la pantalla.
     * @param g Contexto gráfico.
     */
    private void drawGameOver(Graphics g) {
        g.setFont(STATUS_FONT);
        g.setColor(Color.RED);
        String msg = "GAME OVER";
        int msgWidth = g.getFontMetrics().stringWidth(msg);
        g.drawString(msg, (ANCHO_JUEGO - msgWidth) / 2, ALTO_JUEGO / 2);
    }
}