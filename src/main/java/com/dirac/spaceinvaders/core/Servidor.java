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
    // --- Componentes de Red ---
    private int port;                        // Puerto en el que escuchará el servidor
    private ServerSocket serverSocket;       // Socket de escucha principal
    private ExecutorService clientExecutor;  // Pool de hilos para manejar clientes
    // Lista SINCRONIZADA para almacenar los manejadores de los clientes conectados.
    private List<ClientHandler> clientHandlers = Collections.synchronizedList(new ArrayList<>());

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
    // 1) Campo para almacenar puntuaciones finales
    private Map<Integer, Integer> finalScores = new HashMap<>();

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
        serverFrame.setLayout(new BorderLayout(5, 5)); // Layout principal

        // --- Panel Superior (Controles) ---
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("Puerto:"));
        portField = new JTextField(String.valueOf(DEFAULT_PORT), 5); // Campo con puerto por defecto
        topPanel.add(portField);
        startButton = new JButton("Iniciar Servidor");
        startButton.addActionListener(e -> toggleServer()); // Llama a toggleServer al hacer clic
        topPanel.add(startButton);
        // Muestra la IP local para que los clientes sepan dónde conectar
        try {
            topPanel.add(new JLabel("IP Servidor: " + InetAddress.getLocalHost().getHostAddress()));
        } catch (UnknownHostException uhe) {
            topPanel.add(new JLabel("IP Servidor: (No disponible)"));
        }
        serverFrame.add(topPanel, BorderLayout.NORTH);

        // --- Panel Central (Visualización del Juego) ---
        gamePanel = new GamePanel(); // Crea el panel de juego
        serverFrame.add(gamePanel, BorderLayout.CENTER);

        // --- Panel Inferior (Log) ---
        logArea = new JTextArea(10, 50); // Área de texto para logs
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea); // Añade barras de desplazamiento
        serverFrame.add(scrollPane, BorderLayout.SOUTH);

        // Finaliza configuración de la ventana
        serverFrame.pack(); // Ajusta tamaño a los componentes
        serverFrame.setLocationRelativeTo(null); // Centra en pantalla
        serverFrame.setVisible(true);
        log("GUI inicializada. Introduce puerto y presiona 'Iniciar Servidor'.");
    }

    // --- Control del Servidor ---
    /**
     * Inicia o detiene el servidor según su estado actual.
     * Llamado por el botón 'startButton'.
     */
    private void toggleServer() {
        if (serverSocket == null || serverSocket.isClosed()) {
            // Iniciar Servidor
            try {
                port = Integer.parseInt(portField.getText());
                if (port < 1024 || port > 65535) throw new NumberFormatException("Puerto inválido");

                serverSocket = new ServerSocket(port); // Intenta abrir el puerto
                clientExecutor = Executors.newCachedThreadPool(); // Crea pool de hilos para clientes
                gameRunning = true; // Activa el bucle de juego
                new Thread(this).start(); // Inicia el bucle de juego en un nuevo hilo
                startAcceptingClients(); // Empieza a aceptar clientes en otro hilo

                startButton.setText("Detener Servidor"); // Cambia texto del botón
                portField.setEnabled(false); // Deshabilita campo de puerto
                log("Servidor iniciado en el puerto " + port + ". Esperando conexiones...");
                initializeGame(); // Prepara el primer nivel del juego

            } catch (NumberFormatException nfe) {
                log("Error: Puerto inválido. Introduce un número entre 1024 y 65535.");
                JOptionPane.showMessageDialog(serverFrame, "Puerto inválido.", "Error", JOptionPane.ERROR_MESSAGE);
            } catch (IOException e) {
                log("Error al iniciar el servidor en el puerto " + port + ": " + e.getMessage());
                JOptionPane.showMessageDialog(serverFrame, "No se pudo iniciar el servidor:\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                if (serverSocket != null && !serverSocket.isClosed()) {
                    try { serverSocket.close(); } catch (IOException io) {}
                }
            }
        } else {
            // Detener Servidor
            stopServer();
            startButton.setText("Iniciar Servidor");
            portField.setEnabled(true);
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
    public void run() {
        long lastUpdateTime = System.nanoTime(); // Tiempo de la última actualización

        while (gameRunning) {
            long now = System.nanoTime();
            // Calcula el tiempo transcurrido en segundos.
            double deltaTime = (now - lastUpdateTime) / 1_000_000_000.0;
            lastUpdateTime = now;

            // --- Actualiza el Estado del Juego ---
            // Es crucial sincronizar el acceso a currentGameState si otros hilos
            // (como los ClientHandlers al procesar acciones) lo modifican directamente.
            // Si las acciones se ponen en cola, la sincronización puede ser más localizada.
            synchronized (currentGameState) {
                if (!currentGameState.isGameOver()) {
                    updateGameLogic(deltaTime); // Aplica la lógica del juego
                    checkCollisions();          // Comprueba colisiones
                    currentGameState.removeInactiveObjects(); // Limpia objetos marcados
                    checkGameOver();            // Comprueba condiciones de fin de juego
                    checkLevelComplete();       // Comprueba si se completó el nivel
                }
                // Actualiza el panel de juego del propio servidor.
                // Usa invokeLater para asegurar que la actualización de Swing ocurra en el EDT.
                final GameState stateToSend = copyGameState(currentGameState); // Enviar una copia
                SwingUtilities.invokeLater(() -> gamePanel.updateGameState(stateToSend));

            } // Fin del bloque synchronized

            // --- Envía el Estado a los Clientes ---
            // Envía una copia del estado actual a todos los clientes conectados.
             // Creamos una copia inmutable o profunda para evitar problemas de concurrencia
            GameState stateSnapshot = copyGameState(currentGameState);
            broadcastGameState(stateSnapshot);


            // --- Control de Tiempo ---
            // Espera un poco para mantener la tasa de actualización deseada.
            long sleepTime = GAME_UPDATE_RATE_MS - ((System.nanoTime() - now) / 1_000_000);
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Restablece el estado de interrupción
                    gameRunning = false; // Detiene el juego si el hilo es interrumpido
                    log("Hilo del juego interrumpido.");
                }
            }
        }
        log("Bucle principal del juego terminado.");
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
         synchronized (original) { // Sincroniza el acceso al original durante la copia
             // Copia las listas creando nuevas ArrayLists a partir de las originales
             if (original.getPlayers() != null) {
                 copy.setPlayers(new ArrayList<>(original.getPlayers()));
             }
             if (original.getAliens() != null) {
                 copy.setAliens(new ArrayList<>(original.getAliens()));
             }
             if (original.getBullets() != null) {
                 copy.setBullets(new ArrayList<>(original.getBullets()));
             }

             // Copia el mapa de puntuaciones
             if (original.getScores() != null) {
                 copy.setScores(new HashMap<>(original.getScores()));
             }

             // Copia los valores primitivos y String (que son inmutables)
             copy.setLevel(original.getLevel());
             copy.setGameOver(original.isGameOver());
             copy.setStatusMessage(original.getStatusMessage());
         }
         return copy;
     }


    /**
     * Inicializa o resetea el estado del juego para el primer nivel (o un nuevo juego).
     */
    private void initializeGame() {
        synchronized (currentGameState) {
            currentGameState.getPlayers().clear(); // Limpia jugadores (se añadirán al conectar)
            currentGameState.getAliens().clear();
            currentGameState.getBullets().clear();
            currentGameState.setScores(new HashMap<>()); // Resetea puntuaciones
            currentGameState.setLevel(1);
            currentGameState.setGameOver(false);
            currentGameState.setStatusMessage("Nivel 1");
            alienSpeedMultiplier = 1; // Resetea velocidad
            currentAlienDirection = DireccionAlien.DERECHA; // Dirección inicial
            alienMoveCounter = 0;
            nextPlayerId = 0; // Resetea el contador de IDs

             // Llama a los métodos para añadir jugadores y aliens iniciales
             respawnAllPlayers(); // Coloca a los jugadores existentes al inicio
             spawnAliensForLevel(1); // Genera los aliens del nivel 1
        }
         log("Juego inicializado para el Nivel 1.");
    }


    /**
     * Prepara el juego para el siguiente nivel.
     * Limpia balas, regenera aliens, incrementa dificultad.
     */
    private void advanceToNextLevel() {
        synchronized (currentGameState) {
            int nextLevel = currentGameState.getLevel() + 1;
             // Limitamos a 2 niveles por la solicitud inicial
            if (nextLevel > 2) {
                 currentGameState.setGameOver(true);
                 currentGameState.setStatusMessage("¡Has Ganado! Fin del Juego.");
                 log("Juego completado. Todos los niveles superados.");
                 return; // Termina el juego
            }

            currentGameState.setLevel(nextLevel);
            currentGameState.getBullets().clear(); // Limpia balas pantalla
            currentGameState.getAliens().clear(); // Limpia aliens restantes (si los hubiera)
            currentGameState.setGameOver(false); // Asegura que no esté en game over
            currentGameState.setStatusMessage("Nivel " + nextLevel);

            // Incrementa la dificultad (ej. velocidad de aliens)
            alienSpeedMultiplier++; // Hace que se muevan más rápido
            currentAlienDirection = DireccionAlien.DERECHA; // Resetea dirección
            alienMoveCounter = 0; // Resetea contador de movimiento

            respawnAllPlayers(); // Recoloca a los jugadores
            spawnAliensForLevel(nextLevel); // Genera aliens del nuevo nivel
        }
        log("Avanzando al Nivel " + currentGameState.getLevel());
    }


    /**
     * Genera la formación de aliens para un nivel específico.
     * @param level El nivel para el cual generar aliens.
     */
    private void spawnAliensForLevel(int level) {
        currentGameState.getAliens().clear(); // Asegura que la lista esté vacía
        int numRows = 3; // Número de filas de aliens
        int numCols = 8; // Número de aliens por fila
        int startX = 50; // Posición X inicial de la formación
        int startY = 50; // Posición Y inicial de la formación
        int spacingX = Alien.ALIEN_WIDTH + 15; // Espacio horizontal entre aliens
        int spacingY = Alien.ALIEN_HEIGHT + 10; // Espacio vertical entre filas

        for (int row = 0; row < numRows; row++) {
            for (int col = 0; col < numCols; col++) {
                int alienX = startX + col * spacingX;
                int alienY = startY + row * spacingY;
                int tipo;
                // Asigna tipo según la fila (como en el PDF, aunque los puntos son inversos al clásico)
                if (row == 0) tipo = Alien.TIPO_PEQUENO; // Fila superior
                else if (row == 1) tipo = Alien.TIPO_MEDIANO; // Fila media
                else tipo = Alien.TIPO_GRANDE; // Fila inferior

                currentGameState.getAliens().add(new Alien(alienX, alienY, tipo));
            }
        }
         log("Generados " + currentGameState.getAliens().size() + " aliens para el Nivel " + level);
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
        // --- Mover Balas ---
        for (Bullet bullet : currentGameState.getBullets()) {
            if (bullet.isActive()) {
                bullet.move();
                if (bullet.getY() < 0 || bullet.getY() > GamePanel.ALTO_JUEGO) {
                    bullet.setActive(false);
                }
            }
        }

        // --- Mover Aliens ---
        alienMoveCounter++;
        int moveInterval = Math.max(1,
                ALIEN_MOVE_INTERVAL
                        - (currentGameState.getAliens().size() / 4)
                        - alienSpeedMultiplier);

        if (alienMoveCounter >= moveInterval) {
            alienMoveCounter = 0;

            if (currentGameState.getLevel() == 2) {
                // --- NIVEL 2: zigzag por distancia fija ---
                int speedH = alienSpeedMultiplier * 2;
                int speedV = alienSpeedMultiplier;
                int dx = (currentAlienDirection == DireccionAlien.DERECHA) ? speedH : -speedH;
                int dy = speedV;

                // Acumular distancia horizontal recorrida
                zigzagDistanceCounter += Math.abs(dx);
                // Si supera el umbral, invertimos dirección y reseteamos
                if (zigzagDistanceCounter >= ZIGZAG_DISTANCE) {
                    currentAlienDirection = (currentAlienDirection == DireccionAlien.DERECHA)
                            ? DireccionAlien.IZQUIERDA
                            : DireccionAlien.DERECHA;
                    zigzagDistanceCounter = 0;
                    // Opcional: en este punto podrías hacer un descenso extra
                }

                // Aplicar movimiento diagonal a todos los aliens
                for (Alien a : currentGameState.getAliens()) {
                    if (a.isActive()) {
                        a.moverHorizontal(dx);
                        a.setY(a.getY() + dy);
                    }
                }
            } else {
                // --- NIVEL 1 (y >=3): lógica original de bordes ---
                boolean changeDir = false;
                boolean moveDown = false;
                int dx = (currentAlienDirection == DireccionAlien.DERECHA)
                        ? alienSpeedMultiplier * 2
                        : -alienSpeedMultiplier * 2;

                for (Alien a : currentGameState.getAliens()) {
                    if (a.isActive()) {
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
                    if (a.isActive()) {
                        if (moveDown) {
                            a.moverAbajo();
                        } else {
                            a.moverHorizontal(dx);
                        }
                    }
                }
            }
        }

        // --- Disparo Aleatorio de Aliens ---
        if (!currentGameState.getAliens().isEmpty() && !currentGameState.isGameOver()) {
            if (random.nextInt(100) < ALIEN_SHOOT_PROBABILITY) {
                List<Alien> shooters = new ArrayList<>();
                for (Alien a : currentGameState.getAliens())
                    if (a.isActive()) shooters.add(a);

                if (!shooters.isEmpty()) {
                    Alien shooter = shooters.get(random.nextInt(shooters.size()));
                    boolean canShoot = true;
                    for (Bullet b : currentGameState.getBullets()) {
                        if (b.isActive() && !b.isPlayerBullet()
                                && b.getX() > shooter.getX()
                                && b.getX() < shooter.getX() + shooter.getWidth()
                                && b.getY() < shooter.getY() + 50) {
                            canShoot = false;
                            break;
                        }
                    }
                    if (canShoot) {
                        int bx = shooter.getX() + shooter.getWidth()/2 - Bullet.BULLET_WIDTH/2;
                        int by = shooter.getY() + shooter.getHeight();
                        currentGameState.getBullets().add(new Bullet(bx, by, -1));
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
        // --- Colisiones: Bala de Jugador vs Alien ---
        List<Bullet> playerBullets = new ArrayList<>();
        for (Bullet b : currentGameState.getBullets()) {
            if (b.isActive() && b.isPlayerBullet()) playerBullets.add(b);
        }

        List<Alien> activeAliens = new ArrayList<>();
        for (Alien a : currentGameState.getAliens()) {
            if (a.isActive()) activeAliens.add(a);
        }

        for (Bullet bullet : playerBullets) {
            for (Alien alien : activeAliens) {
                if (bullet.collidesWith(alien)) {
                    bullet.setActive(false);
                    alien.setActive(false);
                    Player shooter = null;
                    for (Player p : currentGameState.getPlayers()) {
                        if (p.getPlayerId() == bullet.getOwnerId()) {
                            shooter = p;
                            break;
                        }
                    }
                    if (shooter != null && currentGameState.getScores() != null) {
                        int currentScore = currentGameState.getScores()
                                .getOrDefault(shooter.getPlayerId(), 0);
                        currentGameState.getScores()
                                .put(shooter.getPlayerId(), currentScore + alien.getPuntos());
                    }
                    break;
                }
            }
        }

        // --- Colisiones: Bala de Alien vs Jugador ---
        List<Bullet> alienBullets = new ArrayList<>();
        for (Bullet b : currentGameState.getBullets()) {
            if (b.isActive() && !b.isPlayerBullet()) alienBullets.add(b);
        }

        List<Player> playersCopy = new ArrayList<>(currentGameState.getPlayers());
        for (Bullet bullet : alienBullets) {
            for (Player player : playersCopy) {
                if (!player.isActive() || player.isInvulnerable()) continue;
                if (bullet.collidesWith(player)) {
                    bullet.setActive(false);
                    player.loseLife();
                    if (player.getLives() > 0) {
                        log("Jugador " + player.getPlayerId()
                                + " impactado. Vidas restantes: " + player.getLives()
                                + ". Reapareciendo...");
                        respawnSinglePlayer(player);
                    } else {
                        int pid = player.getPlayerId();
                        currentGameState.getPlayers().removeIf(p -> p.getPlayerId() == pid);
                        if (currentGameState.getScores() != null) {
                            currentGameState.getScores().remove(pid);
                        }
                        log("Jugador " + pid
                                + " ha perdido todas sus vidas y queda eliminado.");
                    }
                    break;
                }
            }
        }

        // --- Colisiones: Alien vs Jugador ---
        for (Alien alien : activeAliens) {
            for (Player player : new ArrayList<>(currentGameState.getPlayers())) {
                if (!player.isActive() || player.isInvulnerable()) continue;
                if (alien.collidesWith(player)) {
                    player.loseLife();
                    if (player.getLives() > 0) {
                        log("Jugador " + player.getPlayerId()
                                + " colisionó con alien. Vidas restantes: " + player.getLives()
                                + ". Reapareciendo...");
                        respawnSinglePlayer(player);
                    } else {
                        int pid = player.getPlayerId();
                        currentGameState.getPlayers().removeIf(p -> p.getPlayerId() == pid);
                        if (currentGameState.getScores() != null) {
                            currentGameState.getScores().remove(pid);
                        }
                        log("Jugador " + pid
                                + " ha perdido todas sus vidas y queda eliminado.");
                    }
                    break;
                }
            }
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
        // Condición 1: Aliens llegan al fondo
        int bottomLimit = GamePanel.ALTO_JUEGO - Alien.ALIEN_HEIGHT - 10; // Límite inferior
        for (Alien alien : currentGameState.getAliens()) {
            if (alien.isActive() && alien.getY() >= bottomLimit) {
                currentGameState.setGameOver(true);
                currentGameState.setStatusMessage("GAME OVER - ¡Los aliens invadieron!");
                log("Game Over: Aliens alcanzaron la línea de defensa.");
                return; // Termina la comprobación
            }
        }

        // Condición 2: No quedan jugadores activos (si implementáramos vidas)
        // boolean anyPlayerAlive = false;
        // for (Player player : currentGameState.getPlayers()) {
        //     if (player.isActive()) {
        //         anyPlayerAlive = true;
        //         break;
        //     }
        // }
        // if (!anyPlayerAlive && !currentGameState.getPlayers().isEmpty()) { // Asegura que había jugadores al inicio
        //     currentGameState.setGameOver(true);
        //     currentGameState.setStatusMessage("GAME OVER - Todos los jugadores eliminados");
        //     log("Game Over: No quedan jugadores activos.");
        // }
    }

    /**
     * Comprueba si todos los aliens han sido eliminados para pasar al siguiente nivel.
     * Llamado repetidamente desde el bucle principal del juego (run).
     */
    private void checkLevelComplete() {
        // Si el juego ya terminó, no hacer nada.
        if (currentGameState.isGameOver()) return;

        // Comprueba si quedan aliens activos.
        boolean aliensRemain = false;
        for (Alien alien : currentGameState.getAliens()) {
            if (alien.isActive()) {
                aliensRemain = true;
                break;
            }
        }

        // Si no quedan aliens activos, el nivel está completo.
        if (!aliensRemain) {
            log("Nivel " + currentGameState.getLevel() + " completado!");
            // Podríamos añadir una pausa breve aquí antes de pasar al siguiente nivel.
            try { Thread.sleep(2000); } catch (InterruptedException e) {} // Pausa de 2 segundos
            advanceToNextLevel(); // Prepara el siguiente nivel
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