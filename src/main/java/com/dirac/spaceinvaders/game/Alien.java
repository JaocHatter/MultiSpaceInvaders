package com.dirac.spaceinvaders.game;

import java.awt.Color;
import java.awt.Graphics;
import java.io.Serializable;

public class Alien extends GameObject implements Serializable {
    private static final long serialVersionUID = 1L; // serialización

    public static final int ALIEN_WIDTH = 25;
    public static final int ALIEN_HEIGHT = 18;
    public static final int ALIEN_DROP_DISTANCE = 10; // Distancia que bajan al cambiar de dirección

    public static final int TIPO_GRANDE = 0;
    public static final int TIPO_MEDIANO = 1;
    public static final int TIPO_PEQUENO = 2;
    public static final int[] PUNTOS_POR_TIPO = {10, 20, 30};

    private int tipo;
    private int puntos;
    private Color color;

    public Alien(int x, int y, int tipo) {
        super(x, y, ALIEN_WIDTH, ALIEN_HEIGHT);
        this.tipo = tipo;
        // asigna puntos y color según el tipo.
        if (tipo >= 0 && tipo < PUNTOS_POR_TIPO.length) {
            this.puntos = PUNTOS_POR_TIPO[tipo];
            // asigna colores distintivos (pueden mejorarse)
            switch (tipo) {
                case TIPO_GRANDE: this.color = Color.GREEN; break;
                case TIPO_MEDIANO: this.color = Color.YELLOW; break;
                case TIPO_PEQUENO: this.color = Color.CYAN; break;
                default: this.color = Color.WHITE;
            }
        } else {
            this.puntos = 10;
            this.color = Color.WHITE;
            System.err.println("Advertencia: Tipo de alien inválido: " + tipo);
        }
    }


    public void moverHorizontal(int dx) {
        this.x += dx;
    }


    public void moverAbajo() {
        this.y += ALIEN_DROP_DISTANCE;
    }


    public int getPuntos() { return puntos; }


    public int getTipo() { return tipo; }


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