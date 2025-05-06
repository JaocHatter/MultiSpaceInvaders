package com.dirac.spaceinvaders.core;

import com.dirac.spaceinvaders.game.*; // Importa todas las clases del paquete game
import com.dirac.spaceinvaders.net.ClientHandler; // Manejador de cliente
import com.dirac.spaceinvaders.net.MessageAction; // Acciones del cliente

import javax.swing.*; // Para la GUI del servidor
import java.awt.*;    // Para Layouts, Color, Dimension, Font
import java.awt.event.ActionEvent; // Para eventos de botones
import java.awt.event.ActionListener; // Listener para botones
import java.io.IOException; // Excepciones de red
import java.net.InetAddress; // Para obtener la IP del servidor
import java.net.ServerSocket; // Socket de escucha del servidor
import java.net.Socket;       // Socket de conexión con cliente
import java.net.UnknownHostException; // Excepción si no se encuentra la IP
import java.util.ArrayList;    // Para la lista de ClientHandlers
import java.util.Collections;  // Para crear listas sincronizadas
import java.util.List;       // Interfaz List
import java.util.Map;        // Interfaz Map
import java.util.HashMap;    // Implementación HashMap para puntuaciones
import java.util.Random;     // Para disparos aleatorios de aliens
import java.util.concurrent.ExecutorService; // Para manejar hilos de clientes
import java.util.concurrent.Executors;   // Para crear ExecutorService

/**
 * Clase Servidor: Gestiona la lógica central del juego Space Invaders Multijugador,
 * acepta conexiones de clientes y sincroniza el estado del juego entre ellos.
 * También proporciona una GUI básica para iniciar el servidor y ver el estado.
 */
public class Servidor implements Runnable { // Implementa Runnable para el bucle principal del juego

    // --- Constantes del Servidor ---
    private static final int DEFAULT_PORT = 12345; // Puerto por defecto si no se especifica
    private static final int MAX_JUGADORES = 4;    // Número máximo de jugadores permitidos
    private static final int GAME_UPDATE_RATE_MS = 50; // Milisegundos entre actualizaciones del juego (20 FPS)
    private static final int ALIEN_MOVE_INTERVAL = 15; // El contador para mover aliens (más bajo = más rápido)
    private static final int ALIEN_SHOOT_PROBABILITY = 5; // Probabilidad (en %) de que un alien dispare en un tick
    private static final int ZIGZAG_DISTANCE = 120;      // distancia horizontal antes de invertir
    private int zigzagDistanceCounter = 0;               // contador acumulado
    private static final int MAX_LEVELS = 6; // Total number of levels
    private static final int MAX_BOSS_MINIONS = 10; // Max small enemies spawned by boss
    private List<Alien> bossMinions = new ArrayList<>();

    // --- Componentes de Red ---
    private int port;                        // Puerto en el que escuchará el servidor
    private ServerSocket serverSocket;       // Socket de escucha principal
    private ExecutorService clientExecutor;  // Pool de hilos para manejar clientes
    // Lista SINCRONIZADA para almacenar los manejadores de los clientes conectados.
    private List<ClientHandler> clientHandlers = Collections.synchronizedList(new ArrayList<>());

    // --- Game Difficulty Parameters (will be set based on level) ---
    private int currentAlienMoveInterval;
    private int currentAlienShootProbability;
    private int currentAlienSpeedMultiplier;

    // --- Estado del Juego ---
    private volatile GameState currentGameState; // El estado actual y autoritativo del juego. 'volatile' por acceso multihilo.
    private volatile boolean gameRunning = false; // Indica si el bucle del juego está activo
    private int alienMoveCounter = 0; // Contador para controlar la velocidad de movimiento alien
    private DireccionAlien currentAlienDirection = DireccionAlien.DERECHA; // Dirección actual de los aliens
    private int alienSpeedMultiplier = 1; // Multiplicador de velocidad de aliens (incrementa con nivel/menos aliens)
    private Random random = new Random(); // Para decisiones aleatorias (disparos alien)
    private int nextPlayerId = 0; // Contador para asignar IDs únicos a los jugadores

    // --- Componentes de la GUI del Servidor ---
    private JFrame serverFrame;        // Ventana principal
    private JTextField portField;      // Campo para introducir el puerto
    private JButton startButton;       // Botón para iniciar/detener el servidor
    private JTextArea logArea;         // Área para mostrar logs y mensajes
    private GamePanel gamePanel;       // Panel para visualizar el estado del juego (como un cliente)
    private JComboBox<Integer> levelSelectorComboBox;

    // --- Constructor ---
    /**
     * Constructor del Servidor. Inicializa la GUI.
     */
    public Servidor() {
        currentGameState = new GameState(); // Inicializa el estado del juego vacío
        setupGUI(); // Configura la interfaz gráfica
    }

    // --- Configuración de la GUI ---
    /**
     * Configura la interfaz gráfica de usuario (GUI) del servidor.
     */
    private void setupGUI() {
        serverFrame = new JFrame("Servidor Space Invaders");
        serverFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        serverFrame.setLayout(new BorderLayout(5, 5));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("Puerto:"));
        portField = new JTextField(String.valueOf(DEFAULT_PORT), 5);
        topPanel.add(portField);

        // --- Level Selector Menu ---
        topPanel.add(new JLabel("Nivel Inicial:"));
        Integer[] levels = {1, 2, 3, 4, 5, 6};
        levelSelectorComboBox = new JComboBox<>(levels);
        topPanel.add(levelSelectorComboBox);
        // --- End Level Selector Menu ---

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

    // --- Control del Servidor ---
    /**
     * Inicia o detiene el servidor según su estado actual.
     * Llamado por el botón 'startButton'.
     */
    private void toggleServer() {
        if (serverSocket == null || serverSocket.isClosed()) {
            try {
                port = Integer.parseInt(portField.getText());
                if (port < 1024 || port > 65535) throw new NumberFormatException("Puerto inválido");

                // --- Get selected level ---
                int selectedLevel = (Integer) levelSelectorComboBox.getSelectedItem();
                // --- End get selected level ---

                serverSocket = new ServerSocket(port);
                clientExecutor = Executors.newCachedThreadPool();
                gameRunning = true;
                new Thread(this).start();
                startAcceptingClients();

                startButton.setText("Detener Servidor");
                portField.setEnabled(false);
                levelSelectorComboBox.setEnabled(false); // Disable level selector while running
                log("Servidor iniciado en el puerto " + port + ". Nivel inicial: " + selectedLevel);
                initializeGame(selectedLevel); // Pass selected level

            } catch (NumberFormatException nfe) {
                log("Error: Puerto inválido. Introduce un número entre 1024 y 65535.");
                JOptionPane.showMessageDialog(serverFrame, "Puerto inválido.", "Error", JOptionPane.ERROR_MESSAGE);
            } catch (IOException e) {
                log("Error al iniciar el servidor en el puerto " + port + ": " + e.getMessage());
                // ... (rest of your catch block)
            }
        } else {
            stopServer();
            startButton.setText("Iniciar Servidor");
            portField.setEnabled(true);
            levelSelectorComboBox.setEnabled(true); // Re-enable level selector
        }
    }

    /**
     * Detiene el servidor de forma ordenada. Cierra sockets, detiene hilos.
     */
    private void stopServer() {
        log("Deteniendo el servidor...");
        gameRunning = false; // Detiene el bucle de juego

        // Cierra las conexiones de todos los clientes
        synchronized (clientHandlers) { // Necesario para iterar/modificar de forma segura
             // Creamos una copia para evitar ConcurrentModificationException al eliminar dentro del bucle
             List<ClientHandler> handlersCopy = new ArrayList<>(clientHandlers);
             for (ClientHandler handler : handlersCopy) {
                 // ClientHandler se encargará de cerrar su propio socket y streams
                 // al detectar que 'running' es false o por errores. Forzamos el cierre.
                 handler.closeConnection(); // Llama al método de cierre del handler
             }
             clientHandlers.clear(); // Vacía la lista original
        }


        // Detiene el pool de hilos de clientes
        if (clientExecutor != null) {
            clientExecutor.shutdown(); // No acepta nuevas tareas
            try {
                // Espera un poco a que terminen las tareas actuales
                if (!clientExecutor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    clientExecutor.shutdownNow(); // Fuerza la detención
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

    /**
     * Inicia un hilo separado para aceptar continuamente conexiones de clientes.
     */
    private void startAcceptingClients() {
        // Usa un nuevo hilo para no bloquear el hilo principal o el de la GUI.
        new Thread(() -> {
            while (serverSocket != null && !serverSocket.isClosed()) {
                try {
                    // accept() es bloqueante: espera hasta que un cliente se conecte.
                    Socket clientSocket = serverSocket.accept();

                    // Verifica si se alcanzó el límite de jugadores.
                    if (clientHandlers.size() < MAX_JUGADORES) {
                        int playerId = nextPlayerId++; // Asigna ID y lo incrementa
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
                        // Límite alcanzado, rechaza la conexión.
                        log("Conexión rechazada desde " + clientSocket.getRemoteSocketAddress() + ". Límite de jugadores alcanzado.");
                        // Opcional: Enviar mensaje de "servidor lleno" antes de cerrar.
                        try { clientSocket.close(); } catch (IOException e) {}
                    }
                } catch (IOException e) {
                    // Si serverSocket está cerrado, salimos del bucle silenciosamente.
                    // De lo contrario, es un error al aceptar.
                    if (serverSocket != null && !serverSocket.isClosed()) {
                        log("Error al aceptar conexión de cliente: " + e.getMessage());
                        // Podría ser necesario detener el servidor si el error es grave.
                    }
                    // Si el socket se cerró (parte de stopServer), la excepción es esperada.
                    break; // Salir del bucle de aceptación
                }
            }
            log("Hilo de aceptación de clientes terminado.");
        }).start(); // Inicia el hilo de aceptación.
    }

    /**
     * Elimina un cliente del servidor (llamado por ClientHandler cuando se desconecta).
     * @param handler El ClientHandler del cliente a eliminar.
     */
    public void eliminarCliente(ClientHandler handler) {
        if (handler == null) return;
        int playerId = handler.getPlayerId();
        boolean removed;
        // Elimina el manejador de la lista sincronizada.
        synchronized (clientHandlers) {
             removed = clientHandlers.remove(handler);
        }
        // Elimina al jugador del estado del juego.
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


    // --- Bucle Principal del Juego (Hilo Runnable) ---
    /**
     * Método run(): Contiene el bucle principal que actualiza el estado del juego
     * y envía las actualizaciones a los clientes. Se ejecuta en su propio hilo.
     */
    @Override
    public void run() { // Bucle principal del juego
        long lastUpdateTime = System.nanoTime();
        while (gameRunning) {
            long now = System.nanoTime();
            double deltaTime = (now - lastUpdateTime) / 1_000_000_000.0;
            lastUpdateTime = now;

            synchronized (currentGameState) {
                if (!currentGameState.isGameOver()) {
                    updateGameLogic(deltaTime);
                    checkCollisions();
                    currentGameState.removeInactiveObjects(); // This also removes inactive boss minions from GameState's list
                    removeInactiveBossMinionsFromServerList(); // Keep server-side list sync
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

    // --- Lógica del Juego (Ejecutada en el Hilo del Juego) ---

     /**
      * Crea una copia profunda (o razonablemente segura) del GameState actual.
      * Esto es importante para evitar que los hilos de red (ClientHandler) o el hilo
      * de la GUI (EDT) trabajen con una referencia que está siendo modificada
      * concurrentemente por el hilo principal del juego.
      *
      * NOTA: Una copia verdaderamente profunda puede ser compleja. Esta versión hace
      * copias de las listas y del mapa, asumiendo que los objetos GameObject
      * contenidos son *inmutables* una vez creados o que sus cambios son atómicos/sincronizados.
      * Si los GameObjects son muy mutables, se necesitaría una clonación más profunda.
      *
      * @param original El GameState original a copiar.
      * @return Una nueva instancia de GameState con copias de las listas y mapas.
      */
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
            // --- Copy Boss ---
            if (original.getBoss() != null) {
                // Assuming Boss is serializable and has a copy constructor or is immutable enough
                // For simplicity, we are directly assigning. If Boss has mutable state that needs deep copying,
                // you'd need a Boss copy constructor or clone method.
                // Boss newBoss = new Boss(original.getBoss().getX(), original.getBoss().getY());
                // newBoss.setCurrentHealth(original.getBoss().getCurrentHealth()); ... etc.
                // For now, let's assume the Boss object itself is copied by reference if mutable,
                // or handle its state copying if it's simple.
                // A safer approach for complex mutable objects is a dedicated copy method.
                // Here, we'll create a new one for snapshot if needed or pass by reference if the state is self-contained
                // and modifications in the game loop are fine. Given it's sent over network, it should be fine.
                copy.setBoss(original.getBoss()); // This might share the boss object if mutable and modified later.
                                                 // Proper deep copy needed if boss changes state after this snapshot.
                                                 // For now, let's assume game logic updates a central boss, and this sends a snapshot.
            }
            // --- End Copy Boss ---
            if (original.getScores() != null) {
                copy.setScores(new HashMap<>(original.getScores()));
            }
            copy.setLevel(original.getLevel());
            copy.setGameOver(original.isGameOver());
            copy.setStatusMessage(original.getStatusMessage());
        }
        return copy;
    }


    /**
     * Inicializa o resetea el estado del juego para el primer nivel (o un nuevo juego).
     */
    private void initializeGame(int startLevel) { // Added startLevel parameter
        synchronized (currentGameState) {
            currentGameState.getPlayers().clear();
            currentGameState.getAliens().clear();
            currentGameState.getBullets().clear();
            currentGameState.setBoss(null); // Clear any previous boss
            currentGameState.setScores(new HashMap<>());
            currentGameState.setLevel(startLevel); // Use the selected start level
            currentGameState.setGameOver(false);
            currentGameState.setStatusMessage("Nivel " + startLevel);

            // Set difficulty for the starting level
            setDifficultyForLevel(startLevel);

            currentAlienDirection = DireccionAlien.DERECHA;
            alienMoveCounter = 0;
            zigzagDistanceCounter = 0;
            nextPlayerId = 0;

            respawnAllPlayers();
            spawnEntitiesForLevel(startLevel); // Changed from spawnAliensForLevel
        }
        log("Juego inicializado para el Nivel " + startLevel + ".");
    }


    private void setDifficultyForLevel(int level) {
        log("Configurando dificultad para Nivel " + level);
        if (level == 1) {
            currentAlienMoveInterval = ALIEN_MOVE_INTERVAL - 2; // 13
            currentAlienShootProbability = ALIEN_SHOOT_PROBABILITY + 2; // 7
            currentAlienSpeedMultiplier = 1;
        } else if (level == 2) {
            currentAlienMoveInterval = ALIEN_MOVE_INTERVAL - 4; // 11
            currentAlienShootProbability = ALIEN_SHOOT_PROBABILITY + 4; // 9
            currentAlienSpeedMultiplier = 2;
        } else if (level == 3) {
            currentAlienMoveInterval = ALIEN_MOVE_INTERVAL - 6; // 9
            currentAlienShootProbability = ALIEN_SHOOT_PROBABILITY + 6; // 11
            currentAlienSpeedMultiplier = 3;
        } else if (level == 4) { // Slower than level 3 trend
            currentAlienMoveInterval = ALIEN_MOVE_INTERVAL - 3; // 12 (L3 was 9) - Slower movement pace
            currentAlienShootProbability = ALIEN_SHOOT_PROBABILITY + 5; // 10 (L3 was 11) - Slightly less shooting
            currentAlienSpeedMultiplier = 2; // (L3 was 3) - Slower individual alien speed
        } else if (level == 5) { // Slightly harder than L4, but still managed
            currentAlienMoveInterval = ALIEN_MOVE_INTERVAL - 5; // 10 (L4 was 12, L3 was 9)
            currentAlienShootProbability = ALIEN_SHOOT_PROBABILITY + 7; // 12 (L4 was 10, L3 was 11)
            currentAlienSpeedMultiplier = 3; // (L4 was 2, L3 was 3)
        } else if (level == MAX_LEVELS) { // Level 6 - Boss Level
            // Boss parameters are self-contained in Boss.java, reset alien params to default
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


    /**
     * Prepara el juego para el siguiente nivel.
     * Limpia balas, regenera aliens, incrementa dificultad.
     */
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
            if (nextLevel > MAX_LEVELS) { // Should be caught by above, but as a safeguard
                 currentGameState.setGameOver(true);
                 currentGameState.setStatusMessage("¡HAS GANADO! Fin del Juego.");
                 log("Juego completado. Todos los niveles superados.");
                 return;
            }

            currentGameState.setLevel(nextLevel);
            currentGameState.getBullets().clear();
            currentGameState.getAliens().clear(); // Clear traditional aliens
            currentGameState.setBoss(null);       // Clear boss before spawning new level entities
            currentGameState.setGameOver(false);
            currentGameState.setStatusMessage("Nivel " + nextLevel);

            setDifficultyForLevel(nextLevel); // Set difficulty for the new level

            currentAlienDirection = DireccionAlien.DERECHA;
            alienMoveCounter = 0;
            zigzagDistanceCounter = 0;

            respawnAllPlayers();
            spawnEntitiesForLevel(nextLevel); // Changed from spawnAliensForLevel
        }
        log("Avanzando al Nivel " + currentGameState.getLevel());
    }


    /**
     * Genera la formación de aliens para un nivel específico.
     * @param level El nivel para el cual generar aliens.
     */
    private void spawnEntitiesForLevel(int level) {
        synchronized (currentGameState) {
            currentGameState.getAliens().clear(); // Clear all aliens first
            bossMinions.clear();                  // Clear the server-side list of boss minions
            currentGameState.setBoss(null);

            if (level >= 1 && level <= 5) {
                // ... (your existing alien spawning logic for levels 1-5)
                // Make sure this adds to currentGameState.getAliens()
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

     /**
      * Recoloca a todos los jugadores conectados en su posición inicial.
      * Útil al empezar un nivel o reiniciar.
      */
     private void respawnAllPlayers() {
         synchronized (currentGameState.getPlayers()) {
             int playerSpacing = GamePanel.ANCHO_JUEGO / (currentGameState.getPlayers().size() + 1);
             int currentX = playerSpacing;
             int playerY = GamePanel.ALTO_JUEGO - Player.PLAYER_HEIGHT - 30; // Cerca del fondo

             for (Player player : currentGameState.getPlayers()) {
                 player.setX(currentX - Player.PLAYER_WIDTH / 2);
                 player.setY(playerY);
                 // Podríamos resetear más cosas del jugador si fuera necesario (ej. vidas)
                 currentX += playerSpacing;
             }
         }
     }


    /**
     * Añade un nuevo jugador al estado del juego cuando se conecta.
     * @param playerId El ID del nuevo jugador.
     */
    private void addPlayerToGame(int playerId) {
         // Asigna colores distintos a los primeros jugadores
         Color playerColor;
         switch (playerId % 4) { // Modulo 4 para ciclar colores si hay más de 4
             case 0: playerColor = Color.BLUE; break;
             case 1: playerColor = Color.RED; break;
             case 2: playerColor = Color.MAGENTA; break;
             case 3: playerColor = Color.ORANGE; break;
             default: playerColor = Color.WHITE;
         }

        // Calcula posición inicial (distribuida)
        int numPlayers = currentGameState.getPlayers().size() + 1; // +1 por el que se está añadiendo
        int playerSpacing = GamePanel.ANCHO_JUEGO / (numPlayers + 1);
        int playerX = playerSpacing * numPlayers - Player.PLAYER_WIDTH / 2;
        int playerY = GamePanel.ALTO_JUEGO - Player.PLAYER_HEIGHT - 30; // Posición Y fija cerca del fondo

        Player newPlayer = new Player(playerX, playerY, playerId, playerColor);

        synchronized (currentGameState) {
            currentGameState.getPlayers().add(newPlayer);
            // Inicializa la puntuación para el nuevo jugador
            if (currentGameState.getScores() == null) {
                 currentGameState.setScores(new HashMap<>());
            }
            currentGameState.getScores().put(playerId, 0);
             // Ajusta las posiciones de los jugadores ya existentes para hacer espacio
             respawnAllPlayers();
        }
         log("Jugador " + playerId + " añadido al juego.");
    }

    /**
     * Procesa una acción recibida de un cliente.
     * Llamado por el ClientHandler correspondiente. Debe ser seguro para hilos.
     * @param playerId El ID del jugador que realizó la acción.
     * @param action La acción realizada (MOVE_LEFT, MOVE_RIGHT, SHOOT).
     */
    public void procesarAccionCliente(int playerId, MessageAction action) {
        System.out.println("Acción recibida: " + action);
        // Sincroniza el acceso al estado del juego para modificarlo.
        synchronized (currentGameState) {
            // Si el juego ha terminado, no procesa acciones de movimiento/disparo.
            if (currentGameState.isGameOver() && action != MessageAction.CONNECT && action != MessageAction.DISCONNECT) {
                return;
            }

            // Busca al jugador correspondiente al ID.
            Player player = null;
            for (Player p : currentGameState.getPlayers()) {
                if (p.getPlayerId() == playerId) {
                    player = p;
                    break;
                }
            }

            // Si no se encuentra al jugador (podría haberse desconectado justo antes), no hace nada.
            if (player == null) {
                 log("Acción recibida para jugador no encontrado: " + playerId);
                return;
            }

            // Realiza la acción.
            switch (action) {
                case MOVE_LEFT:
                    // Llama al método moveLeft del jugador, pasando el límite izquierdo.
                    player.moveLeft(0); // Límite izquierdo es 0
                    break;
                case MOVE_RIGHT:
                    // Llama al método moveRight, pasando el límite derecho.
                    // El límite es el ancho del panel menos el ancho del jugador.
                    player.moveRight(GamePanel.ANCHO_JUEGO - Player.PLAYER_WIDTH);
                    break;
                case MOVE_UP:
                    player.moveUp(0);
                    break;
                case MOVE_DOWN:
                    player.moveDown(GamePanel.ALTO_JUEGO - Player.PLAYER_HEIGHT);
                    break;
                case SHOOT:
                    // Creamos siempre una nueva bala sin restricción de una sola activa
                    int bulletX = player.getX() + player.getWidth() / 2 - Bullet.BULLET_WIDTH / 2;
                    int bulletY = player.getY() - Bullet.BULLET_HEIGHT;
                    currentGameState.getBullets().add(new Bullet(bulletX, bulletY, playerId));
                    break;

                case CONNECT:
                     // La lógica de conexión principal está en startAcceptingClients y addPlayerToGame.
                     log("Recibida acción CONNECT de " + playerId + " (informativo).");
                    break;
                case DISCONNECT:
                     // La desconexión se maneja principalmente cuando ClientHandler detecta error/cierre.
                     log("Recibida acción DISCONNECT de " + playerId + " (informativo).");
                    // Podríamos forzar la eliminación aquí, pero es mejor que ClientHandler lo inicie.
                    break;
            }
        } // Fin del bloque synchronized
    }

    /**
     * Actualiza la lógica principal del juego (movimiento de aliens, balas, etc.).
     * Llamado repetidamente desde el bucle principal del juego (run).
     * @param deltaTime Tiempo transcurrido desde la última actualización (en segundos), no usado aquí pero útil para física más compleja.
     */
    /**
     * Actualiza la lógica principal del juego (movimiento de aliens, balas, etc.).
     * Llamado repetidamente desde el bucle principal del juego (run).
     * @param deltaTime Tiempo transcurrido desde la última actualización (en segundos).
     */
    private void updateGameLogic(double deltaTime) {
        // --- Mover Balas --- (existing logic)
        for (Bullet bullet : currentGameState.getBullets()) {
            if (bullet.isActive()) {
                bullet.move();
                if (bullet.getY() < 0 || bullet.getY() > GamePanel.ALTO_JUEGO + 20 || bullet.getY() < -20) { // Added margin
                    bullet.setActive(false);
                }
            }
        }

        // --- Logic for Levels 1-5 (Traditional Aliens) --- (existing logic using difficulty parameters)
        if (currentGameState.getLevel() < MAX_LEVELS && !currentGameState.getAliens().isEmpty()) {
            // ... (Your existing alien movement and shooting logic for levels 1-5)
            // Make sure it uses currentAlienMoveInterval, currentAlienSpeedMultiplier, currentAlienShootProbability
            // This part seems okay from the previous response.
             alienMoveCounter++;
            int moveInterval = (int) Math.max(1, currentAlienMoveInterval - (currentGameState.getAliens().stream().filter(a -> !(bossMinions.contains(a))).count() / 4)); 

            if (alienMoveCounter >= moveInterval) {
                alienMoveCounter = 0;
                boolean changeDir = false;
                boolean moveDown = false;
                int dx = (currentAlienDirection == DireccionAlien.DERECHA)
                        ? currentAlienSpeedMultiplier * 2
                        : -currentAlienSpeedMultiplier * 2;

                boolean useZigZag = (currentGameState.getLevel() == 3 || currentGameState.getLevel() == 5) ; // Example for levels 3 & 5 for variety

                if (useZigZag) {
                    // ... (Zigzag logic for traditional aliens if you want to keep it)
                    // This example uses a simplified version of your previous zigzag
                    int speedH_std_alien = currentAlienSpeedMultiplier * 2;
                    int speedV_std_alien = currentAlienSpeedMultiplier; // Simpler vertical component
                    dx = (currentAlienDirection == DireccionAlien.DERECHA) ? speedH_std_alien : -speedH_std_alien;

                    zigzagDistanceCounter += Math.abs(dx);
                    if (zigzagDistanceCounter >= ZIGZAG_DISTANCE) {
                        currentAlienDirection = (currentAlienDirection == DireccionAlien.DERECHA)
                                ? DireccionAlien.IZQUIERDA
                                : DireccionAlien.DERECHA;
                        zigzagDistanceCounter = 0;
                        for (Alien a : currentGameState.getAliens()) { // All aliens descend on dir change
                             if (a.isActive() && !bossMinions.contains(a)) a.moverAbajo();
                        }
                    }
                    for (Alien a : currentGameState.getAliens()) {
                        if (a.isActive() && !bossMinions.contains(a)) {
                            a.moverHorizontal(dx);
                            a.setY(a.getY() + speedV_std_alien); // Slight diagonal movement
                        }
                    }
                } else { // Original movement
                    for (Alien a : currentGameState.getAliens()) {
                        if (a.isActive() && !bossMinions.contains(a)) { // Exclude boss minions from this general logic
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
            // --- Disparo Aleatorio de Aliens (traditional) ---
            if (random.nextInt(100) < currentAlienShootProbability) {
                List<Alien> traditionalAliens = new ArrayList<>();
                for(Alien a : currentGameState.getAliens()){
                    if(a.isActive() && !bossMinions.contains(a)) traditionalAliens.add(a);
                }
                if (!traditionalAliens.isEmpty()) {
                    Alien shooter = traditionalAliens.get(random.nextInt(traditionalAliens.size()));
                    // ... (rest of your canShoot logic for traditional aliens)
                    currentGameState.getBullets().add(new Bullet(shooter.getX() + shooter.getWidth()/2 - Bullet.BULLET_WIDTH/2, shooter.getY() + shooter.getHeight(), -1));
                }
            }
        }
        
        // --- Update Boss Minions Movement (if any) ---
        // Boss minions could have simpler movement logic, e.g., move downwards or towards players
        for (Alien minion : bossMinions) {
            if (minion.isActive()) {
                minion.setY(minion.getY() + 2); // Simple downward movement for minions
                if (minion.getY() > GamePanel.ALTO_JUEGO) {
                    minion.setActive(false);
                }
                // Minions could also shoot
                if (random.nextInt(100) < 5) { // Minions have low shoot probability
                    currentGameState.getBullets().add(new Bullet(minion.getX() + minion.getWidth()/2 - Bullet.BULLET_WIDTH/2, minion.getY() + minion.getHeight(), -1));
                }
            }
        }


        // --- Logic for Level 6 (Boss) ---
        Boss boss = currentGameState.getBoss();
        if (currentGameState.getLevel() == MAX_LEVELS && boss != null && boss.isActive()) {
            boss.updateState(bossMinions, MAX_BOSS_MINIONS); // Pass minion list for context if needed by Boss

            if (boss.canShoot()) {
                List<Bullet> bossBullets = boss.shoot();
                currentGameState.getBullets().addAll(bossBullets);
            }

            if (boss.canSpawnMinion() && bossMinions.size() < MAX_BOSS_MINIONS) {
                List<Alien> newMinions = boss.spawnMinions();
                for (Alien minion : newMinions) {
                    if (bossMinions.size() < MAX_BOSS_MINIONS) {
                        currentGameState.getAliens().add(minion); // Add to global alien list for drawing & collision
                        bossMinions.add(minion); // Add to server's tracking list for boss minions
                    } else {
                        break; // Reached max minion cap
                    }
                }
            }
        }
    }



    /**
     * Comprueba todas las posibles colisiones entre objetos del juego.
     * Llamado repetidamente desde el bucle principal del juego (run).
     */
    private void checkCollisions() {
        // --- Colisiones: Bala de Jugador vs Alien (includes boss minions if they are in getAliens()) ---
        List<Bullet> playerBulletsCopy = new ArrayList<>();
        for (Bullet b : currentGameState.getBullets()) {
            if (b.isActive() && b.isPlayerBullet()) playerBulletsCopy.add(b);
        }

        List<Alien> allAliensCopy = new ArrayList<>(currentGameState.getAliens()); // Includes traditional and boss minions

        for (Bullet bullet : playerBulletsCopy) {
            for (Alien alien : allAliensCopy) {
                if (alien.isActive() && bullet.collidesWith(alien)) {
                    bullet.setActive(false);
                    alien.setActive(false); // Marks for removal by removeInactiveObjects
                    // Score logic...
                    Player shooter = getPlayerById(bullet.getOwnerId());
                    if (shooter != null) {
                        int points = alien.getPuntos(); // Standard points
                        if(bossMinions.contains(alien)) points = 50; // More points for boss minions
                        addScoreToPlayer(shooter.getPlayerId(), points);
                    }
                    break; // Bullet hits only one alien
                }
            }
        }
        
        // --- Colisiones: Bala de Jugador vs Boss --- (existing logic from previous response)
        Boss boss = currentGameState.getBoss();
        if (currentGameState.getLevel() == MAX_LEVELS && boss != null && boss.isActive()) {
            // Reuse playerBulletsCopy from above
            for (Bullet bullet : playerBulletsCopy) {
                if (bullet.isActive() && boss.collidesWith(bullet)) { // Check if bullet is still active
                    bullet.setActive(false);
                    if (!boss.isInSpecialAttackMode()) { // Boss might be invulnerable during special
                        boss.takeDamage(15); // Example damage, can be weapon dependent
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

        // --- Colisiones: Bala de Alien/Minion/Boss vs Jugador --- (existing logic)
        List<Bullet> enemyBullets = new ArrayList<>();
        for (Bullet b : currentGameState.getBullets()) {
            if (b.isActive() && !b.isPlayerBullet()) enemyBullets.add(b);
        }
        List<Player> playersCopy = new ArrayList<>(currentGameState.getPlayers()); // Iterate over a copy
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
        
        // --- Colisiones: Alien (incl. minions) vs Jugador --- (existing logic)
        for (Alien alien : allAliensCopy) { // allAliensCopy includes minions
            if(alien.isActive()){
                for (Player player : playersCopy) { // Use the same playersCopy
                    if (player.isActive() && !player.isInvulnerable() && alien.collidesWith(player)) {
                        // Alien does not die, player loses life
                        player.loseLife();
                        log("Jugador " + player.getPlayerId() + " colisionó con alien. Vidas restantes: " + player.getLives());
                        if(player.getLives() > 0) {
                            respawnSinglePlayer(player);
                        } else {
                            removePlayerFromGame(player.getPlayerId());
                             log("Jugador " + player.getPlayerId() + " eliminado por colisión con alien.");
                        }
                        // Potentially deactivate alien too, or push player back
                        // For now, only player is affected as per classic Space Invaders style
                        break; // Alien hits one player, or player hits one alien (depending on perspective)
                    }
                }
            }
        }
        
        // --- Colisiones: Boss vs Jugador --- (existing logic)
         if (currentGameState.getLevel() == MAX_LEVELS && boss != null && boss.isActive()) {
            for (Player player : playersCopy) {
                if (player.isActive() && !player.isInvulnerable() && boss.collidesWith(player)) {
                    player.loseLife(); // Boss collision is serious
                    player.loseLife(); // Lose 2 lives for example
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

    // Helper to add score (you might have this in a more complex way)
    private void addScoreToPlayer(int playerId, int points) {
        if (currentGameState.getScores() != null) {
            currentGameState.getScores().merge(playerId, points, Integer::sum);
        }
    }
    
    // Helper to remove player (consolidates logic)
    private void removePlayerFromGame(int playerId){
        currentGameState.getPlayers().removeIf(p -> p.getPlayerId() == playerId);
        if (currentGameState.getScores() != null) {
            currentGameState.getScores().remove(playerId);
        }
    }

    /**
     * Comprueba si se cumplen las condiciones para terminar el juego.
     * Llamado repetidamente desde el bucle principal del juego (run).
     */

    /** Nuevo método en Servidor para recolocar solo a un jugador */
    private void respawnSinglePlayer(Player player) {
        // Misma lógica de respawnAllPlayers pero solo para este jugador:
        // Calcular posición inicial en base a su playerId y número de jugadores
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
        // Existing: Aliens reach bottom (for levels 1-5)
        if (currentGameState.getLevel() < MAX_LEVELS) {
            int bottomLimit = GamePanel.ALTO_JUEGO - Alien.ALIEN_HEIGHT - 60; // Give a bit more room
            for (Alien alien : currentGameState.getAliens()) {
                if (alien.isActive() && alien.getY() + Alien.ALIEN_HEIGHT >= bottomLimit) { // Check bottom of alien
                    currentGameState.setGameOver(true);
                    currentGameState.setStatusMessage("GAME OVER - ¡Los aliens invadieron!");
                    log("Game Over: Aliens alcanzaron la línea de defensa.");
                    return;
                }
            }
        }

        // Existing: No players left
        // (Your existing logic for this is fine)
        if (currentGameState.getPlayers().isEmpty() && nextPlayerId > 0) { // if players were ever in game
             currentGameState.setGameOver(true);
             currentGameState.setStatusMessage("GAME OVER - Todos los jugadores eliminados");
             log("Game Over: No quedan jugadores activos.");
        }
    }

    /**
     * Comprueba si todos los aliens han sido eliminados para pasar al siguiente nivel.
     * Llamado repetidamente desde el bucle principal del juego (run).
     */
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
            if (!aliensRemain && !currentGameState.getAliens().isEmpty()) { // Check if aliens list was populated for this level
                levelBeaten = true;
            } else if (!aliensRemain && currentGameState.getAliens().isEmpty() && currentGameState.getStatusMessage().startsWith("Nivel")){
                // This can happen if spawnEntitiesForLevel was called but no aliens were added (e.g. level 6 start)
                // Only consider level beaten if there were aliens meant to be there or it's the boss level
                if(currentLevel < MAX_LEVELS) levelBeaten = true; // Assume if list is empty and it's not boss level, it was cleared
            }


        } else if (currentLevel == MAX_LEVELS) { // Boss Level
            Boss boss = currentGameState.getBoss();
            if (boss != null && !boss.isActive()) { // Boss defeated
                levelBeaten = true;
            }
        }

        if (levelBeaten) {
            log("Nivel " + currentLevel + " completado!");
            try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            advanceToNextLevel();
        }
    }

    // --- Comunicación con Clientes ---
    /**
     * Envía el estado actual del juego a todos los clientes conectados.
     * @param state El GameState a enviar (debería ser una copia inmutable o segura).
     */
    private void broadcastGameState(GameState state) {
         // Itera sobre una copia de la lista para evitar problemas si un cliente
         // se desconecta y modifica la lista original mientras iteramos.
         List<ClientHandler> handlersCopy;
         synchronized (clientHandlers) {
             handlersCopy = new ArrayList<>(clientHandlers);
         }

        for (ClientHandler handler : handlersCopy) {
            // Verifica si el handler sigue activo antes de intentar enviar.
            if (handler.isRunning()) {
                handler.sendGameState(state);
            }
        }
    }

    // --- Utilidades ---
    /**
     * Añade un mensaje al área de log de la GUI del servidor.
     * Usa SwingUtilities.invokeLater para asegurar que la actualización
     * de la GUI se haga en el hilo de despacho de eventos (EDT).
     * @param message El mensaje a mostrar.
     */
    private void log(String message) {
        // Añade timestamp simple para claridad.
        String timestamp = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        String logMessage = "[" + timestamp + "] " + message + "\n";
        // Usa invokeLater para actualizar la JTextArea de forma segura desde cualquier hilo.
        SwingUtilities.invokeLater(() -> {
            logArea.append(logMessage);
            // Hace scroll automático al final del área de log.
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
         // También imprime en la consola estándar por si la GUI falla.
         System.out.print(logMessage);
    }


    // --- Punto de Entrada del Servidor ---
    /**
     * Método principal para iniciar la aplicación del servidor.
     * @param args Argumentos de línea de comandos (no se usan actualmente).
     */
    public static void main(String[] args) {
        // Crea y muestra la GUI del servidor en el Hilo de Despacho de Eventos (EDT).
        SwingUtilities.invokeLater(() -> {
            new Servidor(); // Crea la instancia, lo que configura la GUI.
        });
        // La lógica del servidor (aceptar clientes, bucle de juego) se inicia
        // cuando el usuario presiona el botón "Iniciar Servidor" en la GUI.
    }
}