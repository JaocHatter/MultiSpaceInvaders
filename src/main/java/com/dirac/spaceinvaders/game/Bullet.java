package com.dirac.spaceinvaders.game;

import java.awt.Color;
import java.awt.Graphics;
import java.io.Serializable;


public class Bullet extends GameObject implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final int BULLET_WIDTH = 4;
    public static final int BULLET_HEIGHT = 10;
    public static final int PLAYER_BULLET_SPEED = -8;
    public static final int ALIEN_BULLET_SPEED = 5;

    private int ownerId;
    private int speedY;
    private Color color;


    public Bullet(int x, int y, int ownerId) {
        super(x, y, BULLET_WIDTH, BULLET_HEIGHT);
        this.ownerId = ownerId;

        if (ownerId == -1) {
            this.speedY = ALIEN_BULLET_SPEED;
            this.color = Color.RED;
        } else {
            this.speedY = PLAYER_BULLET_SPEED;
            this.color = Color.YELLOW;
        }
    }


    public void move() {
        this.y += this.speedY;
    }


    public int getOwnerId() { return ownerId; }


    public boolean isPlayerBullet() { return ownerId != -1; }


    @Override
    public void draw(Graphics g) {
        g.setColor(this.color);
        g.fillRect(x, y, width, height);
    }
}