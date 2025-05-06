package com.dirac.spaceinvaders.game;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.swing.JPanel;


public class GamePanel extends JPanel {

    public static final int ANCHO_JUEGO = 800;
    public static final int ALTO_JUEGO = 600;
    private static final Font SCORE_FONT = new Font("Monospaced", Font.BOLD, 14);
    private static final Font STATUS_FONT = new Font("Monospaced", Font.BOLD, 24);


    private volatile GameState currentState;


    public GamePanel() {
        setPreferredSize(new Dimension(ANCHO_JUEGO, ALTO_JUEGO));
        setBackground(Color.BLACK);
        setFocusable(true);
    }


    public void updateGameState(GameState newState) {
        this.currentState = newState;

        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        GameState state = this.currentState;

        if (state == null) {
            g.setColor(Color.WHITE);
            g.setFont(STATUS_FONT);
            g.drawString("Esperando conexión...", ANCHO_JUEGO / 2 - 150, ALTO_JUEGO / 2);
            return;
        }

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


        Boss boss = state.getBoss();
        if (boss != null && boss.isActive()) {
            boss.draw(g);
        }

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
            drawGameOver(g);
        }
    }


    private void drawScores(Graphics g, Map<Integer, Integer> scores, List<Player> players) {
        if (scores == null || players == null) return;

        g.setFont(SCORE_FONT);
        int yPos = 20;

        List<Player> safePlayers = Collections.synchronizedList(new ArrayList<>(players));

        for (Map.Entry<Integer, Integer> entry : scores.entrySet()) {
            int playerId = entry.getKey();
            int score = entry.getValue();
            Color playerColor = Color.WHITE;
            int lives = 0;

            synchronized (safePlayers) {
                for (Player p : safePlayers) {
                    if (p.getPlayerId() == playerId) {
                        playerColor = p.getColor();
                        lives = p.getLives();
                        break;
                    }
                }
            }

            g.setColor(playerColor);
            String text = String.format("Jugador %d: %d pts (Vidas: %d)", playerId, score, lives);
            g.drawString(text, 10, yPos);
            yPos += 18;
        }
    }



    private void drawGameInfo(Graphics g, int level, String statusMessage) {
        g.setFont(SCORE_FONT);
        g.setColor(Color.LIGHT_GRAY);

        String levelText = "Nivel: " + level;
        int levelWidth = g.getFontMetrics().stringWidth(levelText);
        g.drawString(levelText, ANCHO_JUEGO - levelWidth - 10, 20);

        if (statusMessage != null && !statusMessage.isEmpty()) {
             g.setFont(STATUS_FONT);
             int statusWidth = g.getFontMetrics().stringWidth(statusMessage);
             g.drawString(statusMessage, (ANCHO_JUEGO - statusWidth) / 2, 40);
        }
    }


    private void drawGameOver(Graphics g) {
        g.setFont(STATUS_FONT);
        g.setColor(Color.RED);
        String msg = "GAME OVER";
        int msgWidth = g.getFontMetrics().stringWidth(msg);
        g.drawString(msg, (ANCHO_JUEGO - msgWidth) / 2, ALTO_JUEGO / 2);
    }
}