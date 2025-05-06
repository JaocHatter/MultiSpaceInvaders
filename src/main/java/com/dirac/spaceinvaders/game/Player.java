package com.dirac.spaceinvaders.game;

import java.awt.Color;
import java.awt.Graphics;
import java.io.Serializable;


public class Player extends GameObject implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final int PLAYER_WIDTH = 30;
    public static final int PLAYER_HEIGHT = 20;
    public static final int PLAYER_SPEED = 5; // píxeles
    public static final int INITIAL_LIVES = 3; // numero de vidas

    private int playerId;
    private int score;
    private int lives;
    private long respawnTimestamp = 0L;
    private static final long INVULNERABILITY_MS = 2000;
    private Color color;

    public Player(int x, int y, int playerId, Color color) {
        super(x, y, PLAYER_WIDTH, PLAYER_HEIGHT);
        this.playerId = playerId;
        this.score = 0;
        this.color = color;
        this.lives = INITIAL_LIVES;
        this.respawnTimestamp = System.currentTimeMillis(); // al crear, darle invulnerabilidad breve
    }


    public void moveLeft(int minX) {
        int nextX = this.x - PLAYER_SPEED;
        if (nextX >= minX) {
            this.x = nextX;
        } else {
            this.x = minX;
        }
    }

    public void loseLife() {
        if (lives > 0) {
            lives--;
            if (lives > 0) {
                respawnTimestamp = System.currentTimeMillis();
            } else {
                setActive(false);
            }
        }
    }



    public int getLives() {
        return lives;
    }

    public void moveRight(int maxX) {
        int nextX = this.x + PLAYER_SPEED;
        if (nextX <= maxX) {
            this.x = nextX;
        } else {
            this.x = maxX;
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


    public void addScore(int points) {
        this.score += points;
    }


    public int getPlayerId() { return playerId; }


    public int getScore() { return score; }

    public Color getColor() { return color; }


    @Override
    public void draw(Graphics g) {
        g.setColor(this.color);
        g.fillRect(x, y, width, height);
        g.setColor(Color.YELLOW);
        g.fillRect(x + width / 2 - 2, y - 4, 4, 4);
    }
}