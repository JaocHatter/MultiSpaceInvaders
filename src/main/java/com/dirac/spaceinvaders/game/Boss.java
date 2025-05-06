package com.dirac.spaceinvaders.game;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.io.Serializable;
import java.util.Random;
import java.util.ArrayList; // For bullets in special attack
import java.util.List;     // For bullets in special attack

public class Boss extends GameObject implements Serializable {
    private static final long serialVersionUID = 2L; // Updated serialVersionUID

    public static final int BOSS_WIDTH = 180; // Slightly wider
    public static final int BOSS_HEIGHT = 90;  // Slightly taller
    private static final int INITIAL_MAX_HEALTH = 10000;
    private static final int MAX_BOSS_MINIONS = 10; // Max small enemies spawned by boss
    private List<Alien> bossMinions = new ArrayList<>();

    // Base attributes (can be modified by phases)
    private int baseMovementSpeed = 5; // Increased base speed
    private int baseShootIntervalMin = 25; // Shoots much more frequently (e.g., 25-50 ticks)
    private int baseShootIntervalRange = 25;
    private int baseMinionsToSpawn = 1; // Minions to spawn per spawn action in normal phase

    // Current operational attributes (modified by phases)
    private int currentMovementSpeed;
    private int currentShootIntervalMin;
    private int currentShootIntervalRange;
    private int currentMinionsToSpawn;

    private int maxHealth;
    private int currentHealth;
    private Random random;
    private int timeToNextShot;
    private int moveDirectionX = 1; // 1 for right, -1 for left
    private int moveDirectionY = 0; // For zigzag

    // Phase and Special Attack Management
    private int currentPhase = 0; // 0 = normal, 1, 2, 3, ... for subsequent phases
    private int healthThresholdForNextPhase; // Health at which next phase triggers
    private static final int HEALTH_DROP_TRIGGER = 1000; // Triggers phase change every 1000 health lost
    private boolean inSpecialAttackMode = false;
    private int specialAttackTimer = 0; // Duration of special attack movement
    private static final int SPECIAL_ATTACK_DURATION = 200; // Ticks for zigzag special
    private int specialAttackZigzagSpeedY = 4;

    // Minion Spawning
    private int timeToNextMinionSpawn;
    private int baseMinionSpawnCooldown = 150; // Ticks (e.g., every 3-5 seconds at 50ms/tick)
    private int currentMinionSpawnCooldown;


    public Boss(int x, int y) {
        super(x, y, BOSS_WIDTH, BOSS_HEIGHT);
        this.maxHealth = INITIAL_MAX_HEALTH;
        this.currentHealth = INITIAL_MAX_HEALTH;
        this.random = new Random();

        // Initialize operational attributes from base
        this.currentMovementSpeed = this.baseMovementSpeed;
        this.currentShootIntervalMin = this.baseShootIntervalMin;
        this.currentShootIntervalRange = this.baseShootIntervalRange;
        this.currentMinionsToSpawn = this.baseMinionsToSpawn;
        this.currentMinionSpawnCooldown = this.baseMinionSpawnCooldown;

        this.timeToNextShot = calculateNextShotTime();
        this.timeToNextMinionSpawn = this.currentMinionSpawnCooldown;
        this.healthThresholdForNextPhase = this.maxHealth - HEALTH_DROP_TRIGGER;
    }

    private int calculateNextShotTime() {
        return currentShootIntervalMin + random.nextInt(currentShootIntervalRange + 1);
    }

    public void takeDamage(int amount) {
        if (!isActive() || inSpecialAttackMode) return; // Optional: Boss might be invulnerable during special

        this.currentHealth -= amount;
        if (this.currentHealth < 0) this.currentHealth = 0;

        if (this.currentHealth <= 0) {
            setActive(false); // Boss is defeated
            return;
        }

        // Check for phase change
        if (this.currentHealth <= healthThresholdForNextPhase) {
            triggerNewPhase();
        }
    }

    private void triggerNewPhase() {
        currentPhase++;
        healthThresholdForNextPhase -= HEALTH_DROP_TRIGGER; // Set next threshold
        if (healthThresholdForNextPhase < 0) healthThresholdForNextPhase = 0; // Don't go below zero

        // --- Activate Special Attack ---
        inSpecialAttackMode = true;
        specialAttackTimer = SPECIAL_ATTACK_DURATION;
        moveDirectionX = (random.nextBoolean()) ? 1 : -1; // Random initial zigzag direction X
        moveDirectionY = (random.nextBoolean()) ? 1 : -1; // Random initial zigzag direction Y


        // --- Increase Difficulty (applied after special attack or immediately) ---
        // For simplicity, let's apply permanent increases per phase.
        // This could also be temporary during the special attack.
        this.currentMovementSpeed = baseMovementSpeed + (currentPhase * 2); // Faster movement
        this.currentShootIntervalMin = Math.max(10, baseShootIntervalMin - (currentPhase * 3)); // Faster shooting
        this.currentShootIntervalRange = Math.max(10, baseShootIntervalRange - (currentPhase * 2));
        this.currentMinionsToSpawn = baseMinionsToSpawn + (currentPhase / 2); // Spawn more minions
        if (this.currentMinionsToSpawn > 3) this.currentMinionsToSpawn = 3; // Cap minions spawned at once
        this.currentMinionSpawnCooldown = Math.max(60, baseMinionSpawnCooldown - (currentPhase * 15)); // Spawn minions more often

        System.out.println("Boss entering Phase " + currentPhase + "! New Speed: " + currentMovementSpeed + ", Shoot Interval: " + currentShootIntervalMin);
    }

    public void updateState(List<Alien> existingMinions, int maxMinions) {
        if (!isActive()) return;

        if (inSpecialAttackMode) {
            executeSpecialAttackMove();
            specialAttackTimer--;
            if (specialAttackTimer <= 0) {
                inSpecialAttackMode = false;
                // Reset to normal movement pattern, possibly centered
                this.y = 50 + random.nextInt(50); // Return to a typical Y position
            }
        } else {
            executeNormalMove();
        }
    }
    
    private void executeNormalMove() {
        this.x += currentMovementSpeed * moveDirectionX;
        // Boundary detection
        if (this.x <= 0) {
            this.x = 0;
            moveDirectionX = 1;
        } else if (this.x + this.width >= GamePanel.ANCHO_JUEGO) {
            this.x = GamePanel.ANCHO_JUEGO - this.width;
            moveDirectionX = -1;
        }
        // Optional: Slight vertical drift
        // this.y += verticalDrift;
    }

    private void executeSpecialAttackMove() {
        // Full map zigzag
        this.x += (currentMovementSpeed + 2) * moveDirectionX; // Even faster during special
        this.y += specialAttackZigzagSpeedY * moveDirectionY;

        if (this.x <= 0) {
            this.x = 0;
            moveDirectionX = 1;
        } else if (this.x + this.width >= GamePanel.ANCHO_JUEGO) {
            this.x = GamePanel.ANCHO_JUEGO - this.width;
            moveDirectionX = -1;
        }

        if (this.y <= 20) { // Top boundary for zigzag
            this.y = 20;
            moveDirectionY = 1;
        } else if (this.y + this.height >= GamePanel.ALTO_JUEGO / 2) { // Bottom boundary for zigzag (e.g., top half of screen)
            this.y = GamePanel.ALTO_JUEGO / 2 - this.height;
            moveDirectionY = -1;
        }
    }


    public boolean canShoot() {
        if (!isActive()) return false;
        timeToNextShot--;
        if (timeToNextShot <= 0) {
            timeToNextShot = calculateNextShotTime();
            return true;
        }
        return false;
    }

    public List<Bullet> shoot() { // Modified to return a list for potential multi-shot
        List<Bullet> bullets = new ArrayList<>();
        if (!isActive()) return bullets;

        // Example: Triple shot
        int bulletY = this.y + this.height;
        int centerX = this.x + this.width / 2 - Bullet.BULLET_WIDTH / 2;

        bullets.add(new Bullet(centerX, bulletY, -1)); // Center
        bullets.add(new Bullet(centerX - 30, bulletY, -1)); // Left
        bullets.add(new Bullet(centerX + 30, bulletY, -1)); // Right
        
        // During special attack, maybe a different pattern or more bullets
        if (inSpecialAttackMode) {
             bullets.add(new Bullet(this.x + Bullet.BULLET_WIDTH, bulletY + 10, -1));
             bullets.add(new Bullet(this.x + this.width - 2 * Bullet.BULLET_WIDTH, bulletY + 10, -1));
        }

        return bullets;
    }

    public boolean canSpawnMinion() {
        if (!isActive() || inSpecialAttackMode) return false; // Don't spawn during special move for now
        timeToNextMinionSpawn--;
        if (timeToNextMinionSpawn <= 0) {
            timeToNextMinionSpawn = currentMinionSpawnCooldown;
            return true;
        }
        return false;
    }

    public List<Alien> spawnMinions() {
        List<Alien> newMinions = new ArrayList<>();
        if (!isActive()) return newMinions;

        for (int i = 0; i < currentMinionsToSpawn; i++) {
            // Spawn minions near the boss, avoiding direct overlap
            int minionX = this.x + (this.width / (currentMinionsToSpawn + 1) * (i + 1)) - Alien.ALIEN_WIDTH / 2;
            minionX += random.nextInt(40) - 20; // Slight random offset
            int minionY = this.y + this.height + 10 + random.nextInt(20);

            if (minionX < 0) minionX = 0;
            if (minionX + Alien.ALIEN_WIDTH > GamePanel.ANCHO_JUEGO) minionX = GamePanel.ANCHO_JUEGO - Alien.ALIEN_WIDTH;
            if (minionY + Alien.ALIEN_HEIGHT > GamePanel.ALTO_JUEGO) minionY = GamePanel.ALTO_JUEGO - Alien.ALIEN_HEIGHT - 50;


            // Create a small type of alien (e.g., TIPO_PEQUENO)
            newMinions.add(new Alien(minionX, minionY, Alien.TIPO_PEQUENO));
        }
        return newMinions;
    }


    public int getCurrentHealth() {
        return currentHealth;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public boolean isInSpecialAttackMode() {
        return inSpecialAttackMode;
    }

    @Override
    public void draw(Graphics g) {
        if (!isActive()) return;

        // Change color or appearance during special attack or phases
        Color bossColor = Color.MAGENTA;
        if (inSpecialAttackMode) {
            bossColor = new Color(random.nextInt(255), random.nextInt(255), random.nextInt(255)); // Flashing colors
        } else if (currentPhase > 0) {
            switch (currentPhase % 3) { // Cycle through a few colors for phases
                case 1: bossColor = new Color(200, 0, 200); break; // Darker Magenta
                case 2: bossColor = new Color(255, 50, 255); break; // Brighter Magenta
                default: bossColor = Color.MAGENTA;
            }
        }
        g.setColor(bossColor);
        g.fillRect(x, y, width, height);

        g.setColor(Color.YELLOW);
        g.fillRect(x + 20, y + 20, 30, 30); // Larger "Eyes"
        g.fillRect(x + width - 50, y + 20, 30, 30);

        // Health Bar (as before, or enhanced)
        int healthBarWidth = width;
        int healthBarHeight = 15; // Slightly thicker
        int healthBarX = x;
        int healthBarY = y - healthBarHeight - 10;

        g.setColor(Color.DARK_GRAY);
        g.fillRect(healthBarX, healthBarY, healthBarWidth, healthBarHeight);
        float healthPercentage = (float) currentHealth / maxHealth;
        g.setColor(Color.RED);
        g.fillRect(healthBarX, healthBarY, (int) (healthBarWidth * healthPercentage), healthBarHeight);
        g.setColor(Color.WHITE);
        g.drawRect(healthBarX, healthBarY, healthBarWidth, healthBarHeight);
        g.setFont(new Font("Monospaced", Font.BOLD, 14));
        String healthText = currentHealth + "/" + maxHealth + " (Fase: " + currentPhase + ")";
        g.setColor(Color.WHITE);
        g.drawString(healthText, healthBarX + 5, healthBarY + healthBarHeight - 2);
    }
}