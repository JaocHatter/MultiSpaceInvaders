package com.dirac.spaceinvaders.game;

// package com.dirac.spaceinvaders.game; // Keep your package

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GameState implements Serializable {
    private static final long serialVersionUID = 2L; // Increment version ID due to new field

    private List<Player> players;
    private List<Alien> aliens;
    private List<Bullet> bullets;
    private Boss boss; // New field for the boss

    private int level;
    private Map<Integer, Integer> scores;
    private boolean gameOver;
    private String statusMessage;

    public GameState() {
        this.players = new ArrayList<>();
        this.aliens = new ArrayList<>();
        this.bullets = new ArrayList<>();
        this.boss = null; // Initialize boss as null
        this.level = 1;
        this.gameOver = false;
        this.statusMessage = "Esperando jugadores...";
    }

    // Getters
    public List<Player> getPlayers() { return players; }
    public List<Alien> getAliens() { return aliens; }
    public List<Bullet> getBullets() { return bullets; }
    public Boss getBoss() { return boss; } // Getter for boss
    public int getLevel() { return level; }
    public Map<Integer, Integer> getScores() { return scores; }
    public boolean isGameOver() { return gameOver; }
    public String getStatusMessage() { return statusMessage; }

    // Setters
    public void setPlayers(List<Player> players) { this.players = players; }
    public void setAliens(List<Alien> aliens) { this.aliens = aliens; }
    public void setBullets(List<Bullet> bullets) { this.bullets = bullets; }
    public void setBoss(Boss boss) { this.boss = boss; } // Setter for boss
    public void setLevel(int level) { this.level = level; }
    public void setScores(Map<Integer, Integer> scores) { this.scores = scores; }
    public void setGameOver(boolean gameOver) { this.gameOver = gameOver; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }

    public void removeInactiveObjects() {
        for (int i = bullets.size() - 1; i >= 0; i--) {
            if (!bullets.get(i).isActive()) {
                bullets.remove(i);
            }
        }
        for (int i = aliens.size() - 1; i >= 0; i--) {
            if (!aliens.get(i).isActive()) {
                aliens.remove(i);
            }
        }
        // Check if boss is inactive (defeated) and handle if necessary
        // (e.g., set to null, though Servidor might handle this by not re-adding)
        // For now, Boss active state is checked before drawing/updating.
    }
}