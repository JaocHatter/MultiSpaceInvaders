package com.dirac.spaceinvaders.game;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.io.Serializable;
import java.util.Random;
import java.util.ArrayList;
import java.util.List;

public class Boss extends GameObject implements Serializable {
    private static final long serialVersionUID = 2L;

    public static final int BOSS_WIDTH = 180;
    public static final int BOSS_HEIGHT = 90;
    private static final int INITIAL_MAX_HEALTH = 10000;
    private static final int MAX_BOSS_MINIONS = 10;
    private List<Alien> bossMinions = new ArrayList<>();

    private int baseMovementSpeed = 5;
    private int baseShootIntervalMin = 25;
    private int baseShootIntervalRange = 25;
    private int baseMinionsToSpawn = 1;

    private int currentMovementSpeed;
    private int currentShootIntervalMin;
    private int currentShootIntervalRange;
    private int currentMinionsToSpawn;

    private int maxHealth;
    private int currentHealth;
    private Random random;
    private int timeToNextShot;
    private int moveDirectionX = 1;
    private int moveDirectionY = 0;

    private int currentPhase = 0;
    private int healthThresholdForNextPhase;
    private static final int HEALTH_DROP_TRIGGER = 1000;
    private boolean inSpecialAttackMode = false;
    private int specialAttackTimer = 0;
    private static final int SPECIAL_ATTACK_DURATION = 200;
    private int specialAttackZigzagSpeedY = 4;

    // Minion Spawning
    private int timeToNextMinionSpawn;
    private int baseMinionSpawnCooldown = 150;
    private int currentMinionSpawnCooldown;


    public Boss(int x, int y) {
        super(x, y, BOSS_WIDTH, BOSS_HEIGHT);
        this.maxHealth = INITIAL_MAX_HEALTH;
        this.currentHealth = INITIAL_MAX_HEALTH;
        this.random = new Random();

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
        if (!isActive() || inSpecialAttackMode) return;

        this.currentHealth -= amount;
        if (this.currentHealth < 0) this.currentHealth = 0;

        if (this.currentHealth <= 0) {
            setActive(false); // Boss is defeated
            return;
        }

        if (this.currentHealth <= healthThresholdForNextPhase) {
            triggerNewPhase();
        }
    }

    private void triggerNewPhase() {
        currentPhase++;
        healthThresholdForNextPhase -= HEALTH_DROP_TRIGGER;
        if (healthThresholdForNextPhase < 0) healthThresholdForNextPhase = 0;

        inSpecialAttackMode = true;
        specialAttackTimer = SPECIAL_ATTACK_DURATION;
        moveDirectionX = (random.nextBoolean()) ? 1 : -1;
        moveDirectionY = (random.nextBoolean()) ? 1 : -1;

        this.currentMovementSpeed = baseMovementSpeed + (currentPhase * 2);
        this.currentShootIntervalMin = Math.max(10, baseShootIntervalMin - (currentPhase * 3));
        this.currentShootIntervalRange = Math.max(10, baseShootIntervalRange - (currentPhase * 2));
        this.currentMinionsToSpawn = baseMinionsToSpawn + (currentPhase / 2);
        if (this.currentMinionsToSpawn > 3) this.currentMinionsToSpawn = 3;
        this.currentMinionSpawnCooldown = Math.max(60, baseMinionSpawnCooldown - (currentPhase * 15));

        System.out.println("Boss entering Phase " + currentPhase + "! New Speed: " + currentMovementSpeed + ", Shoot Interval: " + currentShootIntervalMin);
    }

    public void updateState(List<Alien> existingMinions, int maxMinions) {
        if (!isActive()) return;

        if (inSpecialAttackMode) {
            executeSpecialAttackMove();
            specialAttackTimer--;
            if (specialAttackTimer <= 0) {
                inSpecialAttackMode = false;
                this.y = 50 + random.nextInt(50);
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
        this.x += (currentMovementSpeed + 2) * moveDirectionX;
        this.y += specialAttackZigzagSpeedY * moveDirectionY;

        if (this.x <= 0) {
            this.x = 0;
            moveDirectionX = 1;
        } else if (this.x + this.width >= GamePanel.ANCHO_JUEGO) {
            this.x = GamePanel.ANCHO_JUEGO - this.width;
            moveDirectionX = -1;
        }

        if (this.y <= 20) {
            this.y = 20;
            moveDirectionY = 1;
        } else if (this.y + this.height >= GamePanel.ALTO_JUEGO / 2) {
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

    public List<Bullet> shoot() {
        List<Bullet> bullets = new ArrayList<>();
        if (!isActive()) return bullets;

        int bulletY = this.y + this.height;
        int centerX = this.x + this.width / 2 - Bullet.BULLET_WIDTH / 2;

        bullets.add(new Bullet(centerX, bulletY, -1));
        bullets.add(new Bullet(centerX - 30, bulletY, -1));
        bullets.add(new Bullet(centerX + 30, bulletY, -1));
        
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

        Color bossColor = Color.MAGENTA;
        if (inSpecialAttackMode) {
            bossColor = new Color(random.nextInt(255), random.nextInt(255), random.nextInt(255)); // Flashing colors
        } else if (currentPhase > 0) {
            switch (currentPhase % 3) {
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