package com.dirac.spaceinvaders.game;

import java.awt.Graphics;
import java.awt.Rectangle;
import java.io.Serializable;

public abstract class GameObject implements Serializable {
    private static final long serialVersionUID = 1L;


    protected int x;
    protected int y;

    protected int width;
    protected int height;

    protected boolean active = true;


    public GameObject(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }


    public abstract void draw(Graphics g);


    public int getX() { return x; }


    public void setX(int x) { this.x = x; }


    public int getY() { return y; }


    public void setY(int y) { this.y = y; }


    public int getWidth() { return width; }


    public int getHeight() { return height; }

    public boolean isActive() { return active; }


    public void setActive(boolean active) { this.active = active; }


    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }


    public boolean collidesWith(GameObject other) {
        if (!this.isActive() || !other.isActive()) {
            return false;
        }
        return this.getBounds().intersects(other.getBounds());
    }
}