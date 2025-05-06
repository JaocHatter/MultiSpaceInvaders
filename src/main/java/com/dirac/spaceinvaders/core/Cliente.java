package com.dirac.spaceinvaders.core;

import com.dirac.spaceinvaders.game.GamePanel;   // Panel de dibujo
import com.dirac.spaceinvaders.game.GameState;  // Objeto de estado
import com.dirac.spaceinvaders.net.MessageAction; // Acciones a enviar
import javax.swing.Timer;  // Necesario para movimiento continuo

import javax.swing.*; // GUI
import java.awt.*;    // Layouts, Dimension, etc.
import java.awt.event.*; // Listeners (ActionListener, KeyAdapter)
import java.io.IOException; // Excepciones de red
import java.io.ObjectInputStream; // Leer estado del servidor
import java.io.ObjectOutputStream; // Enviar acciones al servidor
import java.net.ConnectException; // Error específico de conexión
import java.net.Socket;           // Socket del cliente
import java.net.SocketException;  // Error de socket
import java.net.UnknownHostException; // Host no encontrado

/**
 * Clase Cliente: Representa la aplicación cliente que se conecta al servidor
 * de Space Invaders, muestra el juego y envía las acciones del usuario.
 */
public class Cliente implements Runnable { // Runnable para el hilo de escucha del servidor

    // --- Constantes ---
    private static final String DEFAULT_SERVER_IP = "127.0.0.1"; // IP por defecto (localhost)
    private static final int DEFAULT_SERVER_PORT = 12345;      // Puerto por defecto

    // --- Componentes de Red ---
    private String serverIp;             // IP del servidor al que conectar
    private int serverPort;              // Puerto del servidor
    private Socket socket;               // Socket para la conexión con el servidor
    private ObjectOutputStream outputStream; // Stream para enviar acciones (MessageAction)
    private ObjectInputStream inputStream;  // Stream para recibir estado (GameState)
    private volatile boolean connected = false; // Indica si estamos conectados
    private volatile boolean listening = false; // Controla el hilo de escucha
    private int myPlayerId = -1; // ID asignado por el servidor

    // --- Componentes de la GUI ---
    private JFrame clientFrame;    // Ventana principal
    private JTextField ipField;      // Campo para IP del servidor
    private JTextField portField;    // Campo para Puerto del servidor
    private JButton connectButton;  // Botón Conectar/Desconectar
    private GamePanel gamePanel;    // Panel donde se dibuja el juego
    // Podríamos añadir botones para controles como en el PDF, pero KeyListener es más común para juegos
    // private JButton leftButton, rightButton, fireButton;

    // --- Control ---
    // Banderas para saber qué tecla de movimiento está presionada. 'volatile' por acceso multihilo.
    private volatile boolean movingLeft = false;
    private volatile boolean movingRight = false;
    private boolean movingUp = false;     // ← Agregar esto
    private boolean movingDown = false;   // ← Y esto también

    // Timer para movimiento continuo
    private Timer movementTimer;
    // --- Constructor ---
    /**
     * Constructor del Cliente. Inicializa la GUI.
     */
    public Cliente() {
        setupGUI();
        initMovementTimer();
    }

    private void initMovementTimer() {
        movementTimer = new Timer(50, e -> {
            if (connected) {
                if (movingLeft)  sendActionToServer(MessageAction.MOVE_LEFT);
                if (movingRight) sendActionToServer(MessageAction.MOVE_RIGHT);
                if (movingUp)    sendActionToServer(MessageAction.MOVE_UP);
                if (movingDown)  sendActionToServer(MessageAction.MOVE_DOWN);
            }
        });
        movementTimer.start();
    }
    // --- Configuración de la GUI ---
    /**
     * Configura la interfaz gráfica de usuario (GUI) del cliente.
     */
    private void setupGUI() {
        // Ventana principal
        clientFrame = new JFrame("Cliente Space Invaders");
        clientFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        clientFrame.setLayout(new BorderLayout(5, 5));

        // --- Panel Superior: Conexión ---
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("IP Servidor:"));
        ipField = new JTextField(DEFAULT_SERVER_IP, 15);
        topPanel.add(ipField);
        topPanel.add(new JLabel("Puerto:"));
        portField = new JTextField(String.valueOf(DEFAULT_SERVER_PORT), 5);
        topPanel.add(portField);
        connectButton = new JButton("Conectar");
        connectButton.addActionListener(e -> toggleConnection());
        topPanel.add(connectButton);
        clientFrame.add(topPanel, BorderLayout.NORTH);

        // --- Panel Central: Juego ---
        gamePanel = new GamePanel();
        clientFrame.add(gamePanel, BorderLayout.CENTER);

        // --- KeyListener en gamePanel ---
        // 1) Permitimos que el panel reciba foco
        gamePanel.setFocusable(true);
        // 2) Evitamos que las flechas cambien el foco de componente
        gamePanel.setFocusTraversalKeysEnabled(false);
        // 3) Añadimos el listener
        gamePanel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                System.out.println("[KeyPress] " + KeyEvent.getKeyText(e.getKeyCode()));
                handleKeyPress(e.getKeyCode());
            }
            @Override
            public void keyReleased(KeyEvent e) {
                handleKeyRelease(e.getKeyCode());
            }
        });

        // --- Mostrar ventana y solicitar foco ---
        clientFrame.pack();
        clientFrame.setLocationRelativeTo(null);
        clientFrame.setVisible(true);

        // Pedir foco al gamePanel para que reciba las teclas inmediatamente
        gamePanel.requestFocusInWindow();
    }


    // --- Manejo de Conexión ---
    /**
     * Intenta conectar o desconectar del servidor. Llamado por el botón.
     */
    private void toggleConnection() {
        if (!connected) {
            // Conectar
            try {
                serverIp = ipField.getText();
                serverPort = Integer.parseInt(portField.getText());
                if (serverPort < 1024 || serverPort > 65535) throw new NumberFormatException("Puerto inválido");

                // Intenta establecer la conexión. El constructor de Socket puede bloquear.
                // Hacemos esto en un hilo separado para no congelar la GUI.
                setStatus("Conectando a " + serverIp + ":" + serverPort + "...");
                connectButton.setEnabled(false); // Deshabilita botón mientras conecta

                new Thread(() -> {
                    try {
                        socket = new Socket(serverIp, serverPort); // Intenta conectar

                        // Si la conexión tiene éxito:
                        outputStream = new ObjectOutputStream(socket.getOutputStream());
                        inputStream = new ObjectInputStream(socket.getInputStream());
                        connected = true;
                        listening = true; // Activa bandera para el hilo de escucha

                        // Lee el ID asignado por el servidor (espera el formato "ID:X")
                        Object idMessage = inputStream.readObject();
                        if (idMessage instanceof String && ((String)idMessage).startsWith("ID:")) {
                             try {
                                myPlayerId = Integer.parseInt(((String)idMessage).substring(3));
                                clientFrame.setTitle("Cliente Space Invaders - Jugador " + myPlayerId); // Actualiza título ventana
                                setStatus("Conectado como Jugador " + myPlayerId);
                             } catch (NumberFormatException nfe) {
                                 System.err.println("Error parseando ID del servidor: " + idMessage);
                                 setStatus("Error: ID inválido recibido.");
                                 disconnect(); // Desconecta si el ID es incorrecto
                                 return;
                             }
                        } else {
                             System.err.println("Mensaje inesperado al esperar ID: " + idMessage);
                             setStatus("Error: Respuesta inicial del servidor inesperada.");
                             disconnect();
                             return;
                        }


                        // Inicia el hilo que escucha los GameState del servidor.
                        new Thread(this).start();

                        // Habilita/deshabilita controles en el hilo de Swing
                        SwingUtilities.invokeLater(() -> {
                            connectButton.setText("Desconectar");
                            connectButton.setEnabled(true);
                            ipField.setEnabled(false);
                            portField.setEnabled(false);
                            gamePanel.requestFocusInWindow();

                        });


                    } catch (ConnectException ce) {
                        showError("Error de Conexión: El servidor no está disponible o rechazó la conexión.\nVerifica IP/Puerto y que el servidor esté iniciado.");
                        resetConnectionUI();
                    } catch (UnknownHostException uhe) {
                         showError("Error: Host desconocido.\nNo se pudo encontrar el servidor en la dirección IP: " + serverIp);
                         resetConnectionUI();
                    } catch (IOException | ClassNotFoundException e) {
                         showError("Error durante la conexión o lectura inicial: " + e.getMessage());
                         resetConnectionUI();
                         disconnect(); // Intenta limpiar si algo se creó
                    }
                }).start(); // Inicia el hilo de conexión

            } catch (NumberFormatException nfe) {
                showError("Puerto inválido. Introduce un número entre 1024 y 65535.");
            }
        } else {
            // Desconectar
            disconnect();
        }
    }

    /**
     * Cierra la conexión con el servidor y limpia los recursos.
     */
    private void disconnect() {
        setStatus("Desconectando...");
        connected = false;
        listening = false; // Indica al hilo de escucha que se detenga

        try {
            // Cierra los streams y el socket. Es importante cerrar streams primero.
            if (outputStream != null) outputStream.close();
        } catch (IOException e) { /* Ignora errores al cerrar */ }
        try {
            if (inputStream != null) inputStream.close();
        } catch (IOException e) { /* Ignora errores al cerrar */ }
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) { /* Ignora errores al cerrar */ }

        // Limpia las referencias.
        outputStream = null;
        inputStream = null;
        socket = null;
        myPlayerId = -1;

        resetConnectionUI(); // Restaura la GUI al estado desconectado
        // Resetea el panel de juego a un estado inicial vacío o de "Desconectado"
        SwingUtilities.invokeLater(() -> gamePanel.updateGameState(null)); // Muestra panel vacío
        setStatus("Desconectado.");
    }


    /**
     * Restaura la interfaz de usuario al estado desconectado.
     * Debe llamarse desde el hilo de Swing (EDT).
     */
    private void resetConnectionUI() {
         SwingUtilities.invokeLater(() -> {
             connectButton.setText("Conectar");
             connectButton.setEnabled(true);
             ipField.setEnabled(true);
             portField.setEnabled(true);
             clientFrame.setTitle("Cliente Space Invaders");
         });
    }

     /**
      * Muestra un mensaje de error en un diálogo y en el status.
      * @param message El mensaje de error.
      */
     private void showError(String message) {
         setStatus("Error: " + message);
         // Muestra el error en un JOptionPane en el hilo de Swing
         SwingUtilities.invokeLater(() ->
             JOptionPane.showMessageDialog(clientFrame, message, "Error de Conexión/Red", JOptionPane.ERROR_MESSAGE)
         );
     }

     /**
      * Actualiza un JLabel de estado o similar en la GUI (si tuviéramos uno).
      * Por ahora, lo imprimimos en consola.
      * @param message El mensaje a mostrar.
      */
     private void setStatus(String message) {
         // Si tuviéramos un JLabel statusLabel:
         // SwingUtilities.invokeLater(() -> statusLabel.setText(message));
         System.out.println("Cliente Status: " + message); // Log en consola por ahora
     }


    // --- Hilo de Escucha del Servidor (Runnable) ---
    /**
     * Método run(): Escucha continuamente los objetos GameState enviados por el servidor.
     * Se ejecuta en un hilo separado una vez conectado.
     */
    @Override
    public void run() {
        try {
            while (listening && connected && inputStream != null) {
                // Lee el siguiente objeto del servidor (bloqueante).
                Object receivedObject = inputStream.readObject();

                // Verifica si es un GameState.
                if (receivedObject instanceof GameState) {
                    GameState newState = (GameState) receivedObject;
                    // Actualiza el panel del juego con el nuevo estado.
                    // updateGameState ya llama a repaint() internamente.
                    // Esta llamada debe ser segura para hilos, GamePanel usa 'volatile'.
                    gamePanel.updateGameState(newState);
                } else {
                    // Recibido algo inesperado.
                    System.err.println("Cliente: Recibido objeto inesperado del servidor: " + receivedObject);
                }
            }
        } catch (SocketException se) {
             // Ocurre si el servidor cierra la conexión o hay un problema de red.
             if (listening) { // Solo muestra error si esperábamos seguir escuchando
                showError("Se perdió la conexión con el servidor (SocketException): " + se.getMessage());
             }
        } catch (IOException e) {
             if (listening) {
                 showError("Error de I/O leyendo del servidor: " + e.getMessage());
             }
        } catch (ClassNotFoundException e) {
             if (listening) {
                 showError("Error: Clase no encontrada al leer GameState del servidor: " + e.getMessage());
             }
        } finally {
            // Asegura la desconexión si el bucle termina por cualquier razón.
            if (connected) {
                disconnect();
            }
            System.out.println("Cliente: Hilo de escucha terminado.");
        }
    }

    // --- Manejo de Entrada del Usuario (Teclado) ---
    /**
     * Maneja el evento cuando una tecla es presionada.
     * @param keyCode El código de la tecla presionada.
     */
    private void handleKeyPress(int keyCode) {
        if (!connected) return;

        switch (keyCode) {
            case KeyEvent.VK_LEFT:
            case KeyEvent.VK_A:
                movingLeft = true;
                break;
            case KeyEvent.VK_RIGHT:
            case KeyEvent.VK_D:
                movingRight = true;
                break;
            case KeyEvent.VK_UP:
            case KeyEvent.VK_W:
                movingUp = true;
                break;
            case KeyEvent.VK_DOWN:
            case KeyEvent.VK_S:
                movingDown = true;
                break;
            case KeyEvent.VK_SPACE:
            case KeyEvent.VK_V:
                sendActionToServer(MessageAction.SHOOT);
                break;
        }
    }
    /**
     * Maneja el evento cuando una tecla es liberada.
     * Principalmente para detener el movimiento continuo.
     * @param keyCode El código de la tecla liberada.
     */
    private void handleKeyRelease(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_LEFT:
            case KeyEvent.VK_A:
                movingLeft = false;
                break;
            case KeyEvent.VK_RIGHT:
            case KeyEvent.VK_D:
                movingRight = false;
                break;
            case KeyEvent.VK_UP:
            case KeyEvent.VK_W:
                movingUp = false;
                break;
            case KeyEvent.VK_DOWN:
            case KeyEvent.VK_S:
                movingDown = false;
                break;
        }
    }

    // --- Envío de Acciones al Servidor ---
    /**
     * Envía una acción (MessageAction) al servidor.
     * Debe ser seguro llamarlo desde el hilo de eventos de Swing (KeyListener).
     * @param action La acción a enviar.
     */
    private void sendActionToServer(MessageAction action) {
        // Solo envía si estamos conectados y el stream de salida está listo.
        if (connected && outputStream != null) {
            try {
                // Escribe la acción en el stream.
                outputStream.writeObject(action);
                outputStream.flush(); // Asegura que se envíe inmediatamente.
            } catch (SocketException se) {
                 // Error al enviar, probablemente desconectado.
                 showError("Error al enviar acción (SocketException): " + se.getMessage() + ". Desconectando.");
                 disconnect();
            } catch (IOException e) {
                // Otro error de I/O.
                showError("Error de I/O al enviar acción: " + e.getMessage());
                // Considerar desconectar si el error es persistente.
            }
        } else {
            // Informa si se intenta enviar sin estar conectado (puede pasar justo al desconectar).
            // System.out.println("Cliente: Intento de enviar acción sin conexión.");
        }
    }

    // --- Punto de Entrada del Cliente ---
    /**
     * Método principal para iniciar la aplicación del cliente.
     * @param args Argumentos de línea de comandos: [IP_Servidor] [Puerto_Servidor] (opcionales).
     */
    public static void main(String[] args) {
        // Procesa argumentos de línea de comandos (si los hay)
        String startIp = DEFAULT_SERVER_IP;
        int startPort = DEFAULT_SERVER_PORT;
        if (args.length >= 1) {
            startIp = args[0];
        }
        if (args.length >= 2) {
            try {
                startPort = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Puerto inválido proporcionado como argumento. Usando puerto por defecto: " + DEFAULT_SERVER_PORT);
            }
        }

        // Guarda los valores iniciales para usarlos en la GUI
        final String finalStartIp = startIp;
        final int finalStartPort = startPort;

        // Crea y muestra la GUI del cliente en el Hilo de Despacho de Eventos (EDT).
        SwingUtilities.invokeLater(() -> {
            Cliente cliente = new Cliente();
            // Establece los valores iniciales (de argumentos o por defecto) en los campos de la GUI.
            cliente.ipField.setText(finalStartIp);
            cliente.portField.setText(String.valueOf(finalStartPort));
        });
        // La conexión se inicia cuando el usuario presiona el botón "Conectar" en la GUI.
    }
}