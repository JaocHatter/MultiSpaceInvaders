package com.dirac.spaceinvaders.net;

import com.dirac.spaceinvaders.core.Servidor; // Para interactuar con el servidor principal
import com.dirac.spaceinvaders.game.GameState; // Para enviar el estado
import java.io.IOException;         // Para manejo de excepciones de red
import java.io.ObjectInputStream;   // Para leer objetos del cliente
import java.io.ObjectOutputStream;  // Para enviar objetos al cliente
import java.net.Socket;             // La conexión con un cliente específico
import java.net.SocketException;    // Para detectar desconexiones

/**
 * Clase ClientHandler: Se ejecuta en un hilo separado en el servidor
 * para manejar la comunicación con un único cliente conectado.
 * Implementa Runnable para poder ser ejecutada por el ExecutorService del servidor.
 */
public class ClientHandler implements Runnable {

    // --- Atributos ---
    private Socket socketCliente;           // Socket de la conexión con este cliente.
    private ObjectOutputStream outputStream; // Stream para enviar datos (GameState) al cliente.
    private ObjectInputStream inputStream;  // Stream para recibir datos (MessageAction) del cliente.
    private Servidor servidor;              // Referencia al servidor principal para interactuar con él.
    private int playerId;                   // ID único asignado a este cliente por el servidor.
    private volatile boolean running = true; // Bandera para controlar el bucle de escucha.

    // --- Constructor ---
    /**
     * Constructor del ClientHandler.
     * @param socket Socket de la conexión establecida con el cliente.
     * @param servidor Referencia al objeto Servidor principal.
     * @param playerId ID asignado a este jugador.
     */
    public ClientHandler(Socket socket, Servidor servidor, int playerId) {
        this.socketCliente = socket;
        this.servidor = servidor;
        this.playerId = playerId;
        try {
            // IMPORTANTE: Crear ObjectOutputStream PRIMERO antes de ObjectInputStream
            // puede prevenir bloqueos (deadlock) en la inicialización de streams.
            this.outputStream = new ObjectOutputStream(socket.getOutputStream());
            this.inputStream = new ObjectInputStream(socket.getInputStream());
            System.out.println("Servidor: Streams creados para Jugador " + playerId);
        } catch (IOException e) {
            System.err.println("Error al crear streams para el cliente " + playerId + ": " + e.getMessage());
            running = false; // No se pudo inicializar, marca para no ejecutar run()
            closeConnection(); // Intenta cerrar si algo falló
        }
    }

    // --- Ejecución del Hilo ---
    /**
     * Método run(): Contiene el bucle principal que escucha las acciones enviadas por el cliente.
     * Se ejecuta cuando el ExecutorService del servidor inicia este manejador.
     */
    @Override
    public void run() {
        // Mensaje inicial: Notifica al cliente su ID.
        // Podríamos encapsular esto en un objeto específico si el protocolo crece.
        try {
             outputStream.writeObject("ID:" + this.playerId); // Envía el ID como un String simple
             outputStream.flush(); // Asegura el envío inmediato
             System.out.println("Servidor: ID " + playerId + " enviado al cliente.");
        } catch (IOException e) {
             System.err.println("Error al enviar ID inicial al cliente " + playerId + ": " + e.getMessage());
             running = false; // No se pudo comunicar, detener
        }


        // Bucle principal de escucha mientras la conexión esté activa.
        while (running) {
            try {
                // Lee el objeto enviado por el cliente (espera que sea un MessageAction).
                // readObject() es bloqueante, espera hasta recibir algo.
                Object receivedObject = inputStream.readObject();

                // Verifica si lo recibido es del tipo esperado.
                if (receivedObject instanceof MessageAction) {
                    MessageAction action = (MessageAction) receivedObject;
                    // Procesa la acción recibida llamando al método correspondiente en el servidor.
                    // Es crucial que el servidor maneje estas acciones de forma sincronizada
                    // para evitar problemas de concurrencia con el bucle principal del juego.
                    servidor.procesarAccionCliente(this.playerId, action);
                } else {
                    // Si recibe algo inesperado, lo registra y podría ser motivo de desconexión.
                    System.err.println("Servidor: Recibido objeto inesperado del cliente " + playerId + ": " + receivedObject);
                }

            } catch (SocketException e) {
                // SocketException (como Connection reset) usualmente indica que el cliente cerró la conexión.
                System.out.println("Servidor: Cliente " + playerId + " desconectado (SocketException): " + e.getMessage());
                running = false; // Termina el bucle de escucha.
            } catch (IOException e) {
                // Otro error de I/O durante la lectura.
                System.err.println("Error de I/O leyendo del cliente " + playerId + ": " + e.getMessage());
                running = false; // Termina el bucle.
            } catch (ClassNotFoundException e) {
                // Error si el objeto recibido no corresponde a una clase conocida.
                System.err.println("Error: Clase no encontrada al leer del cliente " + playerId + ": " + e.getMessage());
                // Podría ser un error grave de protocolo o versión.
                running = false; // Termina el bucle.
            }
        }

        // --- Limpieza al terminar el bucle ---
        // Notifica al servidor que este cliente se ha desconectado para que pueda eliminarlo.
        servidor.eliminarCliente(this);
        // Cierra la conexión y los streams.
        closeConnection();
        System.out.println("Servidor: Conexión con cliente " + playerId + " cerrada.");
    }

    // --- Envío de Estado ---
    /**
     * Envía el estado actual del juego a este cliente.
     * Este método es llamado por el servidor principal (desde su bucle de juego/broadcast).
     * Debe manejar posibles errores de envío.
     * @param gameState El objeto GameState a enviar.
     */
    public void sendGameState(GameState gameState) {
        // Solo intenta enviar si la conexión sigue activa y el stream está listo.
        if (running && outputStream != null) {
            try {
                // Escribe el objeto GameState completo en el stream.
                outputStream.writeObject(gameState);
                // Importante: reset() evita que ObjectOutputStream cachee objetos enviados
                // previamente. Sin esto, los clientes podrían recibir estados antiguos
                // o referencias al mismo objeto si no se modificó profundamente.
                outputStream.reset();
                // flush() asegura que los datos se envíen por la red inmediatamente.
                outputStream.flush();
            } catch (SocketException se) {
                // Si ocurre un error de socket al enviar (ej. Broken pipe),
                // el cliente probablemente se desconectó.
                System.err.println("Error de Socket al enviar estado a cliente " + playerId + ". Desconectando: " + se.getMessage());
                running = false; // Marca para detener el hilo de escucha.
                // No llamar a closeConnection() aquí directamente para evitar doble cierre
                // si el hilo de escucha también detecta la excepción. El hilo run() lo hará.
            } catch (IOException e) {
                // Otro error de I/O al enviar.
                System.err.println("Error de I/O al enviar estado a cliente " + playerId + ": " + e.getMessage());
                // Considerar si detener el hilo o solo registrar el error.
                // Podría ser un error temporal, pero a menudo indica un problema persistente.
                 running = false; // Marca para detener por precaución.
            }
        }
    }

    // --- Cierre de Conexión ---
    /**
     * Cierra los streams y el socket de forma segura.
     */
    // lo cambie a public generaba error cuando se le llamaba desde otra clase
    public void closeConnection() {
        running = false; // Asegura que el bucle se detenga
        try {
            if (inputStream != null) inputStream.close();
        } catch (IOException e) {
            System.err.println("Error al cerrar InputStream para cliente " + playerId + ": " + e.getMessage());
        }
        try {
            if (outputStream != null) outputStream.close();
        } catch (IOException e) {
            System.err.println("Error al cerrar OutputStream para cliente " + playerId + ": " + e.getMessage());
        }
        try {
            if (socketCliente != null && !socketCliente.isClosed()) socketCliente.close();
        } catch (IOException e) {
            System.err.println("Error al cerrar Socket para cliente " + playerId + ": " + e.getMessage());
        }
    }

    // --- Getters ---
    /**
     * Obtiene el ID del jugador asociado a este manejador.
     * @return El ID del jugador.
     */
    public int getPlayerId() {
        return playerId;
    }

     /**
      * Comprueba si el manejador sigue activo y corriendo.
      * @return true si está activo, false si debe detenerse o ya se detuvo.
      */
     public boolean isRunning() {
         return running;
     }
}