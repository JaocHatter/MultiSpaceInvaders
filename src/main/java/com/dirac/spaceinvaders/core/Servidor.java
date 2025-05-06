package com.dirac.spaceinvaders.core;

import com.dirac.spaceinvaders.game.*;
import com.dirac.spaceinvaders.net.ClientHandler;
import com.dirac.spaceinvaders.net.MessageAction;

import javax.swing.*;
import java.awt.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class Servidor implements Runnable {

    private static final int DEFAULT_PORT = 12345;
    private static final int MAX_JUGADORES = 4;
    private static final int GAME_UPDATE_RATE_MS = 50;
    private static final int ALIEN_MOVE_INTERVAL = 15;
    private static final int ALIEN_SHOOT_PROBABILITY = 5;
    private static final int ZIGZAG_DISTANCE = 120;
    private int zigzagDistanceCounter = 0;
    private static final int MAX_LEVELS = 6;
    private static final int MAX_BOSS_MINIONS = 10;
    private List<Alien> bossMinions = new ArrayList<>();

    private int port;
    private ServerSocket serverSocket;
    private ExecutorService clientExecutor;
    private List<ClientHandler> clientHandlers = Collections.synchronizedList(new ArrayList<>());

    private int currentAlienMoveInterval;
    private int currentAlienShootProbability;
    private int currentAlienSpeedMultiplier;

    private volatile GameState currentGameState;
    private volatile boolean gameRunning = false;
    private int alienMoveCounter = 0; //
    private DireccionAlien currentAlienDirection = DireccionAlien.DERECHA;
    private int alienSpeedMultiplier = 1;
    private Random random = new Random();
    private int nextPlayerId = 0;


    private JFrame serverFrame;
    private JTextField portField;
    private JButton startButton;
    private JTextArea logArea;
    private GamePanel gamePanel;
    private JComboBox<Integer> levelSelectorComboBox;


    public Servidor() {
        currentGameState = new GameState();
        setupGUI();
    }


    private void setupGUI() {
        serverFrame = new JFrame("Servidor Space Invaders");
        serverFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        serverFrame.setLayout(new BorderLayout(5, 5));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("Puerto:"));
        portField = new JTextField(String.valueOf(DEFAULT_PORT), 5);
        topPanel.add(portField);

        topPanel.add(new JLabel("Nivel Inicial:"));
        Integer[] levels = {1, 2, 3, 4, 5, 6};
        levelSelectorComboBox = new JComboBox<>(levels);
        topPanel.add(levelSelectorComboBox);

        startButton = new JButton("Iniciar Servidor");
        startButton.addActionListener(e -> toggleServer());
        topPanel.add(startButton);
        try {
            topPanel.add(new JLabel("IP Servidor: " + InetAddress.getLocalHost().getHostAddress()));
        } catch (UnknownHostException uhe) {
            topPanel.add(new JLabel("IP Servidor: (No disponible)"));
        }
        serverFrame.add(topPanel, BorderLayout.NORTH);

        gamePanel = new GamePanel();
        serverFrame.add(gamePanel, BorderLayout.CENTER);

        logArea = new JTextArea(10, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        serverFrame.add(scrollPane, BorderLayout.SOUTH);

        serverFrame.pack();
        serverFrame.setLocationRelativeTo(null);
        serverFrame.setVisible(true);
        log("GUI inicializada. Seleccione nivel, introduzca puerto y presione 'Iniciar Servidor'.");
    }


    private void toggleServer() {
        if (serverSocket == null || serverSocket.isClosed()) {
            try {
                port = Integer.parseInt(portField.getText());
                if (port < 1024 || port > 65535) throw new NumberFormatException("Puerto inválido");

                int selectedLevel = (Integer) levelSelectorComboBox.getSelectedItem();

                serverSocket = new ServerSocket(port);
                clientExecutor = Executors.newCachedThreadPool();
                gameRunning = true;
                new Thread(this).start();
                startAcceptingClients();

                startButton.setText("Detener Servidor");
                portField.setEnabled(false);
                levelSelectorComboBox.setEnabled(false);
                log("Servidor iniciado en el puerto " + port + ". Nivel inicial: " + selectedLevel);
                initializeGame(selectedLevel);

            } catch (NumberFormatException nfe) {
                log("Error: Puerto inválido. Introduce un número entre 1024 y 65535.");
                JOptionPane.showMessageDialog(serverFrame, "Puerto inválido.", "Error", JOptionPane.ERROR_MESSAGE);
            } catch (IOException e) {
                log("Error al iniciar el servidor en el puerto " + port + ": " + e.getMessage());
            }
        } else {
            stopServer();
            startButton.setText("Iniciar Servidor");
            portField.setEnabled(true);
            levelSelectorComboBox.setEnabled(true);
        }
    }


    private void stopServer() {
        log("Deteniendo el servidor...");
        gameRunning = false;
        synchronized (clientHandlers) {
             List<ClientHandler> handlersCopy = new ArrayList<>(clientHandlers);
             for (ClientHandler handler : handlersCopy) {
                 handler.closeConnection();
             }
             clientHandlers.clear();
        }

        if (clientExecutor != null) {
            clientExecutor.shutdown();
            try {
                if (!clientExecutor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    clientExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                clientExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Cierra el socket principal del servidor
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                log("Socket del servidor cerrado.");
            }
        } catch (IOException e) {
            log("Error al cerrar el socket del servidor: " + e.getMessage());
        }
        serverSocket = null; // Marca como cerrado
        log("Servidor detenido.");
    }


    private void startAcceptingClients() {
        // Usa un nuevo hilo para no bloquear el hilo principal o el de la GUI.
        new Thread(() -> {
            while (serverSocket != null && !serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();

                    // Verifica si se alcanzó el límite de jugadores.
                    if (clientHandlers.size() < MAX_JUGADORES) {
                        int playerId = nextPlayerId++; // Asigna id y lo incrementa
                        log("Cliente conectado desde " + clientSocket.getRemoteSocketAddress() + ". Asignado ID: " + playerId);

                        // Crea un manejador para este cliente.
                        ClientHandler handler = new ClientHandler(clientSocket, this, playerId);

                        // Añade el manejador a la lista sincronizada.
                        synchronized (clientHandlers) {
                            clientHandlers.add(handler);
                        }
                        // Añade un nuevo jugador al estado del juego.
                        addPlayerToGame(playerId);

                        // Inicia el hilo del manejador usando el ExecutorService.
                        clientExecutor.submit(handler);

                    } else {

                        log("Conexión rechazada desde " + clientSocket.getRemoteSocketAddress() + ". Límite de jugadores alcanzado.");
                        try { clientSocket.close(); } catch (IOException e) {}
                    }
                } catch (IOException e) {
                    // Si serverSocket está cerrado, salimos del bucle silenciosamente.
                    if (serverSocket != null && !serverSocket.isClosed()) {
                        log("Error al aceptar conexión de cliente: " + e.getMessage());
                    }
                    // Si el socket se cerró (parte de stopServer), la excepción es esperada.
                    break;
                }
            }
            log("Hilo de aceptación de clientes terminado.");
        }).start();
    }

    public void eliminarCliente(ClientHandler handler) {
        if (handler == null) return;
        int playerId = handler.getPlayerId();
        boolean removed;
        synchronized (clientHandlers) {
             removed = clientHandlers.remove(handler);
        }
        synchronized (currentGameState) {
             currentGameState.getPlayers().removeIf(player -> player.getPlayerId() == playerId);
             // También elimina su puntuación
             if (currentGameState.getScores() != null) {
                 currentGameState.getScores().remove(playerId);
             }
        }
         if (removed) {
            log("Cliente " + playerId + " eliminado del servidor.");
         } else {
             log("Intento de eliminar cliente " + playerId + " que no estaba en la lista.");
         }

    }


    //bucle principal del juego
    @Override
    public void run() {
        long lastUpdateTime = System.nanoTime();
        while (gameRunning) {
            long now = System.nanoTime();
            double deltaTime = (now - lastUpdateTime) / 1_000_000_000.0;
            lastUpdateTime = now;

            synchronized (currentGameState) {
                if (!currentGameState.isGameOver()) {
                    updateGameLogic(deltaTime);
                    checkCollisions();
                    currentGameState.removeInactiveObjects();
                    removeInactiveBossMinionsFromServerList();
                    checkGameOver();
                    checkLevelComplete();
                }
                final GameState stateToSend = copyGameState(currentGameState);
                SwingUtilities.invokeLater(() -> gamePanel.updateGameState(stateToSend));
            }
            GameState stateSnapshot = copyGameState(currentGameState);
            broadcastGameState(stateSnapshot);

            long sleepTime = GAME_UPDATE_RATE_MS - ((System.nanoTime() - now) / 1_000_000);
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    gameRunning = false;
                    log("Hilo del juego interrumpido.");
                }
            }
        }
        log("Bucle principal del juego terminado.");
    }

    private void removeInactiveBossMinionsFromServerList() {
        bossMinions.removeIf(minion -> !minion.isActive());
    }


      private GameState copyGameState(GameState original) {
        if (original == null) return null;
        GameState copy = new GameState();
        synchronized (original) {
            if (original.getPlayers() != null) {
                copy.setPlayers(new ArrayList<>(original.getPlayers()));
            }
            if (original.getAliens() != null) {
                copy.setAliens(new ArrayList<>(original.getAliens()));
            }
            if (original.getBullets() != null) {
                copy.setBullets(new ArrayList<>(original.getBullets()));
            }
            // copiar al boss
            if (original.getBoss() != null) {
                copy.setBoss(original.getBoss());
            }
            if (original.getScores() != null) {
                copy.setScores(new HashMap<>(original.getScores()));
            }
            copy.setLevel(original.getLevel());
            copy.setGameOver(original.isGameOver());
            copy.setStatusMessage(original.getStatusMessage());
        }
        return copy;
    }


    private void initializeGame(int startLevel) {
        synchronized (currentGameState) {
            currentGameState.getPlayers().clear();
            currentGameState.getAliens().clear();
            currentGameState.getBullets().clear();
            currentGameState.setBoss(null);
            currentGameState.setScores(new HashMap<>());
            currentGameState.setLevel(startLevel);
            currentGameState.setGameOver(false);
            currentGameState.setStatusMessage("Nivel " + startLevel);

            setDifficultyForLevel(startLevel);

            currentAlienDirection = DireccionAlien.DERECHA;
            alienMoveCounter = 0;
            zigzagDistanceCounter = 0;
            nextPlayerId = 0;

            respawnAllPlayers();
            spawnEntitiesForLevel(startLevel);
        }
        log("Juego inicializado para el Nivel " + startLevel + ".");
    }


    private void setDifficultyForLevel(int level) {
        log("Configurando dificultad para Nivel " + level);
        if (level == 1) {
            currentAlienMoveInterval = ALIEN_MOVE_INTERVAL - 2;
            currentAlienShootProbability = ALIEN_SHOOT_PROBABILITY + 2;
            currentAlienSpeedMultiplier = 1;
        } else if (level == 2) {
            currentAlienMoveInterval = ALIEN_MOVE_INTERVAL - 4;
            currentAlienShootProbability = ALIEN_SHOOT_PROBABILITY + 4;
            currentAlienSpeedMultiplier = 2;
        } else if (level == 3) {
            currentAlienMoveInterval = ALIEN_MOVE_INTERVAL - 6;
            currentAlienShootProbability = ALIEN_SHOOT_PROBABILITY + 6;
            currentAlienSpeedMultiplier = 3;
        } else if (level == 4) {
            currentAlienMoveInterval = ALIEN_MOVE_INTERVAL - 3;
            currentAlienShootProbability = ALIEN_SHOOT_PROBABILITY + 5;
            currentAlienSpeedMultiplier = 2;
        } else if (level == 5) {
            currentAlienMoveInterval = ALIEN_MOVE_INTERVAL - 5;
            currentAlienShootProbability = ALIEN_SHOOT_PROBABILITY + 7;
            currentAlienSpeedMultiplier = 3;
        } else if (level == MAX_LEVELS) {
            currentAlienMoveInterval = ALIEN_MOVE_INTERVAL;
            currentAlienShootProbability = ALIEN_SHOOT_PROBABILITY;
            currentAlienSpeedMultiplier = 1;
            bossMinions.clear(); // Clear any previous boss minions
        } else {
            currentAlienMoveInterval = ALIEN_MOVE_INTERVAL;
            currentAlienShootProbability = ALIEN_SHOOT_PROBABILITY;
            currentAlienSpeedMultiplier = 1;
        }
        log("Dificultad Nivel " + level + ": MoveInterval=" + currentAlienMoveInterval +
            ", ShootProb=" + currentAlienShootProbability + "%, SpeedMult=" + currentAlienSpeedMultiplier);
    }


    private void advanceToNextLevel() {
        synchronized (currentGameState) {
            int currentLevel = currentGameState.getLevel();
            if (currentLevel == MAX_LEVELS && (currentGameState.getBoss() == null || !currentGameState.getBoss().isActive())) {
                 currentGameState.setGameOver(true);
                 currentGameState.setStatusMessage("¡HAS GANADO! Fin del Juego.");
                 log("Juego completado. Todos los niveles superados.");
                 return;
            }

            int nextLevel = currentLevel + 1;
            if (nextLevel > MAX_LEVELS) {
                 currentGameState.setGameOver(true);
                 currentGameState.setStatusMessage("¡HAS GANADO! Fin del Juego.");
                 log("Juego completado. Todos los niveles superados.");
                 return;
            }

            currentGameState.setLevel(nextLevel);
            currentGameState.getBullets().clear();
            currentGameState.getAliens().clear();
            currentGameState.setBoss(null);
            currentGameState.setGameOver(false);
            currentGameState.setStatusMessage("Nivel " + nextLevel);

            setDifficultyForLevel(nextLevel);

            currentAlienDirection = DireccionAlien.DERECHA;
            alienMoveCounter = 0;
            zigzagDistanceCounter = 0;

            respawnAllPlayers();
            spawnEntitiesForLevel(nextLevel);
        }
        log("Avanzando al Nivel " + currentGameState.getLevel());
    }


    private void spawnEntitiesForLevel(int level) {
        synchronized (currentGameState) {
            currentGameState.getAliens().clear();
            bossMinions.clear();
            currentGameState.setBoss(null);

            if (level >= 1 && level <= 5) {
                int numRows = 3 + (level / 2) ;
                if (numRows > 5) numRows = 5;
                int numCols = 8 + (level -1);
                if (numCols > 12) numCols = 12;

                int startX = 50;
                int startY = 50;
                int spacingX = Alien.ALIEN_WIDTH + 15 - (level);
                if (spacingX < Alien.ALIEN_WIDTH + 5) spacingX = Alien.ALIEN_WIDTH + 5;
                int spacingY = Alien.ALIEN_HEIGHT + 10;

                for (int row = 0; row < numRows; row++) {
                    for (int col = 0; col < numCols; col++) {
                        int alienX = startX + col * spacingX;
                        int alienY = startY + row * spacingY;
                        int tipo;
                        if (row % 3 == 0) tipo = Alien.TIPO_PEQUENO;
                        else if (row % 3 == 1) tipo = Alien.TIPO_MEDIANO;
                        else tipo = Alien.TIPO_GRANDE;
                        currentGameState.getAliens().add(new Alien(alienX, alienY, tipo));
                    }
                }
                log("Generados " + currentGameState.getAliens().size() + " aliens para el Nivel " + level);


            } else if (level == MAX_LEVELS) {
                int bossX = GamePanel.ANCHO_JUEGO / 2 - Boss.BOSS_WIDTH / 2;
                int bossY = 60;
                Boss boss = new Boss(bossX, bossY);
                currentGameState.setBoss(boss);
                log("Jefe final (Nodriza) generado para el Nivel " + level + " con " + boss.getMaxHealth() + " HP.");
            }
        }
    }


     private void respawnAllPlayers() {
         synchronized (currentGameState.getPlayers()) {
             int playerSpacing = GamePanel.ANCHO_JUEGO / (currentGameState.getPlayers().size() + 1);
             int currentX = playerSpacing;
             int playerY = GamePanel.ALTO_JUEGO - Player.PLAYER_HEIGHT - 30; // Cerca del fondo

             for (Player player : currentGameState.getPlayers()) {
                 player.setX(currentX - Player.PLAYER_WIDTH / 2);
                 player.setY(playerY);
                 currentX += playerSpacing;
             }
         }
     }



    private void addPlayerToGame(int playerId) {
         Color playerColor;
         switch (playerId % 4) {
             case 0: playerColor = Color.BLUE; break;
             case 1: playerColor = Color.RED; break;
             case 2: playerColor = Color.MAGENTA; break;
             case 3: playerColor = Color.ORANGE; break;
             default: playerColor = Color.WHITE;
         }

        int numPlayers = currentGameState.getPlayers().size() + 1;
        int playerSpacing = GamePanel.ANCHO_JUEGO / (numPlayers + 1);
        int playerX = playerSpacing * numPlayers - Player.PLAYER_WIDTH / 2;
        int playerY = GamePanel.ALTO_JUEGO - Player.PLAYER_HEIGHT - 30;

        Player newPlayer = new Player(playerX, playerY, playerId, playerColor);

        synchronized (currentGameState) {
            currentGameState.getPlayers().add(newPlayer);
            if (currentGameState.getScores() == null) {
                 currentGameState.setScores(new HashMap<>());
            }
            currentGameState.getScores().put(playerId, 0);
             respawnAllPlayers();
        }
         log("Jugador " + playerId + " añadido al juego.");
    }


    public void procesarAccionCliente(int playerId, MessageAction action) {
        System.out.println("Acción recibida: " + action);
        // sincroniza el acceso al estado del juego para modificarlo.
        synchronized (currentGameState) {
            // si el juego ha terminado no procesda acciones de movimiento/disparo.
            if (currentGameState.isGameOver() && action != MessageAction.CONNECT && action != MessageAction.DISCONNECT) {
                return;
            }

            Player player = null;
            for (Player p : currentGameState.getPlayers()) {
                if (p.getPlayerId() == playerId) {
                    player = p;
                    break;
                }
            }

            if (player == null) {
                 log("Acción recibida para jugador no encontrado: " + playerId);
                return;
            }

            switch (action) {
                case MOVE_LEFT:
                    // mover izquierda
                    player.moveLeft(0); // Límite izquierdo es 0
                    break;
                case MOVE_RIGHT:
                    //mover derecho.
                    player.moveRight(GamePanel.ANCHO_JUEGO - Player.PLAYER_WIDTH);
                    break;
                case MOVE_UP:
                    player.moveUp(0);
                    break;
                case MOVE_DOWN:
                    player.moveDown(GamePanel.ALTO_JUEGO - Player.PLAYER_HEIGHT);
                    break;
                case SHOOT:
                    // crear bala
                    int bulletX = player.getX() + player.getWidth() / 2 - Bullet.BULLET_WIDTH / 2;
                    int bulletY = player.getY() - Bullet.BULLET_HEIGHT;
                    currentGameState.getBullets().add(new Bullet(bulletX, bulletY, playerId));
                    break;

                case CONNECT:
                     // coneccion
                    break;
                case DISCONNECT:
                     // desconectar
                     log("Recibida acción DISCONNECT de " + playerId + " (informativo).");
                    // forzar eliminacion
                    break;
            }
        }
    }

   //mover balas
    private void updateGameLogic(double deltaTime) {
        for (Bullet bullet : currentGameState.getBullets()) {
            if (bullet.isActive()) {
                bullet.move();
                if (bullet.getY() < 0 || bullet.getY() > GamePanel.ALTO_JUEGO + 20 || bullet.getY() < -20) { // Added margin
                    bullet.setActive(false);
                }
            }
        }

        if (currentGameState.getLevel() < MAX_LEVELS && !currentGameState.getAliens().isEmpty()) {
             alienMoveCounter++;
            int moveInterval = (int) Math.max(1, currentAlienMoveInterval - (currentGameState.getAliens().stream().filter(a -> !(bossMinions.contains(a))).count() / 4)); 

            if (alienMoveCounter >= moveInterval) {
                alienMoveCounter = 0;
                boolean changeDir = false;
                boolean moveDown = false;
                int dx = (currentAlienDirection == DireccionAlien.DERECHA)
                        ? currentAlienSpeedMultiplier * 2
                        : -currentAlienSpeedMultiplier * 2;

                boolean useZigZag = (currentGameState.getLevel() == 3 || currentGameState.getLevel() == 5) ;

                if (useZigZag) {
                    int speedH_std_alien = currentAlienSpeedMultiplier * 2;
                    int speedV_std_alien = currentAlienSpeedMultiplier;
                    dx = (currentAlienDirection == DireccionAlien.DERECHA) ? speedH_std_alien : -speedH_std_alien;

                    zigzagDistanceCounter += Math.abs(dx);
                    if (zigzagDistanceCounter >= ZIGZAG_DISTANCE) {
                        currentAlienDirection = (currentAlienDirection == DireccionAlien.DERECHA)
                                ? DireccionAlien.IZQUIERDA
                                : DireccionAlien.DERECHA;
                        zigzagDistanceCounter = 0;
                        for (Alien a : currentGameState.getAliens()) {
                             if (a.isActive() && !bossMinions.contains(a)) a.moverAbajo();
                        }
                    }
                    for (Alien a : currentGameState.getAliens()) {
                        if (a.isActive() && !bossMinions.contains(a)) {
                            a.moverHorizontal(dx);
                            a.setY(a.getY() + speedV_std_alien);
                        }
                    }
                } else {
                    for (Alien a : currentGameState.getAliens()) {
                        if (a.isActive() && !bossMinions.contains(a)) {
                            int nextX = a.getX() + dx;
                            if (nextX <= 0 || nextX >= GamePanel.ANCHO_JUEGO - Alien.ALIEN_WIDTH) {
                                changeDir = true;
                                moveDown = true;
                                break;
                            }
                        }
                    }
                    if (changeDir) {
                        currentAlienDirection = (currentAlienDirection == DireccionAlien.DERECHA)
                                ? DireccionAlien.IZQUIERDA
                                : DireccionAlien.DERECHA;
                        dx = 0;
                    }
                    for (Alien a : currentGameState.getAliens()) {
                        if (a.isActive() && !bossMinions.contains(a)) {
                            if (moveDown) a.moverAbajo();
                            else a.moverHorizontal(dx);
                        }
                    }
                }
            }

            if (random.nextInt(100) < currentAlienShootProbability) {
                List<Alien> traditionalAliens = new ArrayList<>();
                for(Alien a : currentGameState.getAliens()){
                    if(a.isActive() && !bossMinions.contains(a)) traditionalAliens.add(a);
                }
                if (!traditionalAliens.isEmpty()) {
                    Alien shooter = traditionalAliens.get(random.nextInt(traditionalAliens.size()));
                    currentGameState.getBullets().add(new Bullet(shooter.getX() + shooter.getWidth()/2 - Bullet.BULLET_WIDTH/2, shooter.getY() + shooter.getHeight(), -1));
                }
            }
        }
        

        for (Alien minion : bossMinions) {
            if (minion.isActive()) {
                minion.setY(minion.getY() + 2);
                if (minion.getY() > GamePanel.ALTO_JUEGO) {
                    minion.setActive(false);
                }
                if (random.nextInt(100) < 5) {
                    currentGameState.getBullets().add(new Bullet(minion.getX() + minion.getWidth()/2 - Bullet.BULLET_WIDTH/2, minion.getY() + minion.getHeight(), -1));
                }
            }
        }


        // logical del boss
        Boss boss = currentGameState.getBoss();
        if (currentGameState.getLevel() == MAX_LEVELS && boss != null && boss.isActive()) {
            boss.updateState(bossMinions, MAX_BOSS_MINIONS);
            if (boss.canShoot()) {
                List<Bullet> bossBullets = boss.shoot();
                currentGameState.getBullets().addAll(bossBullets);
            }

            if (boss.canSpawnMinion() && bossMinions.size() < MAX_BOSS_MINIONS) {
                List<Alien> newMinions = boss.spawnMinions();
                for (Alien minion : newMinions) {
                    if (bossMinions.size() < MAX_BOSS_MINIONS) {
                        currentGameState.getAliens().add(minion);
                        bossMinions.add(minion);
                    } else {
                        break;
                    }
                }
            }
        }
    }


    private void checkCollisions() {
        List<Bullet> playerBulletsCopy = new ArrayList<>();
        for (Bullet b : currentGameState.getBullets()) {
            if (b.isActive() && b.isPlayerBullet()) playerBulletsCopy.add(b);
        }

        List<Alien> allAliensCopy = new ArrayList<>(currentGameState.getAliens());

        for (Bullet bullet : playerBulletsCopy) {
            for (Alien alien : allAliensCopy) {
                if (alien.isActive() && bullet.collidesWith(alien)) {
                    bullet.setActive(false);
                    alien.setActive(false);
                    Player shooter = getPlayerById(bullet.getOwnerId());
                    if (shooter != null) {
                        int points = alien.getPuntos(); // Standard points
                        if(bossMinions.contains(alien)) points = 50; // More points for boss minions
                        addScoreToPlayer(shooter.getPlayerId(), points);
                    }
                    break;
                }
            }
        }
        
        Boss boss = currentGameState.getBoss();
        if (currentGameState.getLevel() == MAX_LEVELS && boss != null && boss.isActive()) {
            for (Bullet bullet : playerBulletsCopy) {
                if (bullet.isActive() && boss.collidesWith(bullet)) {
                    bullet.setActive(false);
                    if (!boss.isInSpecialAttackMode()) {
                        boss.takeDamage(15);
                        log("Boss fue golpeado! Salud restante: " + boss.getCurrentHealth() + "/" + boss.getMaxHealth());
                    }
                    Player shooter = getPlayerById(bullet.getOwnerId());
                    if (shooter != null) {
                        addScoreToPlayer(shooter.getPlayerId(), 75); // Score for hitting boss
                    }
                    if (!boss.isActive()) {
                        log("Boss DERROTADO!");
                        if (shooter != null) addScoreToPlayer(shooter.getPlayerId(), 5000); // Big bonus
                    }
                }
            }
        }

        List<Bullet> enemyBullets = new ArrayList<>();
        for (Bullet b : currentGameState.getBullets()) {
            if (b.isActive() && !b.isPlayerBullet()) enemyBullets.add(b);
        }
        List<Player> playersCopy = new ArrayList<>(currentGameState.getPlayers());
        for (Bullet bullet : enemyBullets) {
            for (Player player : playersCopy) {
                if (player.isActive() && !player.isInvulnerable() && bullet.collidesWith(player)) {
                    bullet.setActive(false);
                    player.loseLife();
                    log("Jugador " + player.getPlayerId() + " impactado. Vidas restantes: " + player.getLives());
                    if (player.getLives() > 0) {
                        respawnSinglePlayer(player);
                    } else {
                        removePlayerFromGame(player.getPlayerId());
                        log("Jugador " + player.getPlayerId() + " ha perdido todas sus vidas.");
                    }
                    break; 
                }
            }
        }
        
        for (Alien alien : allAliensCopy) {
            if(alien.isActive()){
                for (Player player : playersCopy) {
                    if (player.isActive() && !player.isInvulnerable() && alien.collidesWith(player)) {
                        player.loseLife();
                        log("Jugador " + player.getPlayerId() + " colisionó con alien. Vidas restantes: " + player.getLives());
                        if(player.getLives() > 0) {
                            respawnSinglePlayer(player);
                        } else {
                            removePlayerFromGame(player.getPlayerId());
                             log("Jugador " + player.getPlayerId() + " eliminado por colisión con alien.");
                        }
                        break;
                    }
                }
            }
        }
        
         if (currentGameState.getLevel() == MAX_LEVELS && boss != null && boss.isActive()) {
            for (Player player : playersCopy) {
                if (player.isActive() && !player.isInvulnerable() && boss.collidesWith(player)) {
                    player.loseLife();
                    player.loseLife();
                    log("Jugador " + player.getPlayerId() + " colisionó con el JEFE!");
                     if (player.getLives() > 0) {
                        respawnSinglePlayer(player);
                    } else {
                        removePlayerFromGame(player.getPlayerId());
                        log("Jugador " + player.getPlayerId() + " eliminado por el Jefe.");
                    }
                }
            }
        }
    }

    private Player getPlayerById(int playerId) {
        for (Player p : currentGameState.getPlayers()) {
            if (p.getPlayerId() == playerId) {
                return p;
            }
        }
        return null;
    }

    private void addScoreToPlayer(int playerId, int points) {
        if (currentGameState.getScores() != null) {
            currentGameState.getScores().merge(playerId, points, Integer::sum);
        }
    }
    
    private void removePlayerFromGame(int playerId){
        currentGameState.getPlayers().removeIf(p -> p.getPlayerId() == playerId);
        if (currentGameState.getScores() != null) {
            currentGameState.getScores().remove(playerId);
        }
    }



    private void respawnSinglePlayer(Player player) {
        int numPlayers = currentGameState.getPlayers().size();
        int index = 0;
        for (int i = 0; i < currentGameState.getPlayers().size(); i++) {
            if (currentGameState.getPlayers().get(i).getPlayerId() == player.getPlayerId()) {
                index = i + 1; // 1-based
                break;
            }
        }
        int spacing = GamePanel.ANCHO_JUEGO / (numPlayers + 1);
        int newX = spacing * index - Player.PLAYER_WIDTH / 2;
        int newY = GamePanel.ALTO_JUEGO - Player.PLAYER_HEIGHT - 30;
        player.setX(newX);
        player.setY(newY);
    }
    
    
    private void checkGameOver() {
        if (currentGameState.getLevel() < MAX_LEVELS) {
            int bottomLimit = GamePanel.ALTO_JUEGO - Alien.ALIEN_HEIGHT - 60;
            for (Alien alien : currentGameState.getAliens()) {
                if (alien.isActive() && alien.getY() + Alien.ALIEN_HEIGHT >= bottomLimit) {
                    currentGameState.setGameOver(true);
                    currentGameState.setStatusMessage("GAME OVER - ¡Los aliens invadieron!");
                    log("Game Over: Aliens alcanzaron la línea de defensa.");
                    return;
                }
            }
        }


        if (currentGameState.getPlayers().isEmpty() && nextPlayerId > 0) { // if players were ever in game
             currentGameState.setGameOver(true);
             currentGameState.setStatusMessage("GAME OVER - Todos los jugadores eliminados");
             log("Game Over: No quedan jugadores activos.");
        }
    }

    private void checkLevelComplete() {
        if (currentGameState.isGameOver()) return;

        boolean levelBeaten = false;
        int currentLevel = currentGameState.getLevel();

        if (currentLevel >= 1 && currentLevel <= 5) { // Traditional alien levels
            boolean aliensRemain = false;
            for (Alien alien : currentGameState.getAliens()) {
                if (alien.isActive()) {
                    aliensRemain = true;
                    break;
                }
            }
            if (!aliensRemain && !currentGameState.getAliens().isEmpty()) {
                levelBeaten = true;
            } else if (!aliensRemain && currentGameState.getAliens().isEmpty() && currentGameState.getStatusMessage().startsWith("Nivel")){

                if(currentLevel < MAX_LEVELS) levelBeaten = true;
            }


        } else if (currentLevel == MAX_LEVELS) {
            Boss boss = currentGameState.getBoss();
            if (boss != null && !boss.isActive()) {
                levelBeaten = true;
            }
        }

        if (levelBeaten) {
            log("Nivel " + currentLevel + " completado!");
            try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            advanceToNextLevel();
        }
    }


    private void broadcastGameState(GameState state) {
         List<ClientHandler> handlersCopy;
         synchronized (clientHandlers) {
             handlersCopy = new ArrayList<>(clientHandlers);
         }

        for (ClientHandler handler : handlersCopy) {
            if (handler.isRunning()) {
                handler.sendGameState(state);
            }
        }
    }


    private void log(String message) {
        String timestamp = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        String logMessage = "[" + timestamp + "] " + message + "\n";
        SwingUtilities.invokeLater(() -> {
            logArea.append(logMessage);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
         System.out.print(logMessage);
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new Servidor();
        });

    }
}