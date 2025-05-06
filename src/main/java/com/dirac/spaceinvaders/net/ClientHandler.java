package com.dirac.spaceinvaders.net;

import com.dirac.spaceinvaders.core.Servidor; // Para interactuar con el servidor principal
import com.dirac.spaceinvaders.game.GameState; // Para enviar el estado
import java.io.IOException;         // Para manejo de excepciones de red
import java.io.ObjectInputStream;   // Para leer objetos del cliente
import java.io.ObjectOutputStream;  // Para enviar objetos al cliente
import java.net.Socket;             // La conexión con un cliente específico
import java.net.SocketException;    // Para detectar desconexiones


public class ClientHandler implements Runnable {

    private Socket socketCliente;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;
    private Servidor servidor;
    private int playerId;
    private volatile boolean running = true;


    public ClientHandler(Socket socket, Servidor servidor, int playerId) {
        this.socketCliente = socket;
        this.servidor = servidor;
        this.playerId = playerId;
        try {

            this.outputStream = new ObjectOutputStream(socket.getOutputStream());
            this.inputStream = new ObjectInputStream(socket.getInputStream());
            System.out.println("Servidor: Streams creados para Jugador " + playerId);
        } catch (IOException e) {
            System.err.println("Error al crear streams para el cliente " + playerId + ": " + e.getMessage());
            running = false;
            closeConnection();
        }
    }

    @Override
    public void run() {

        try {
             outputStream.writeObject("ID:" + this.playerId);
             outputStream.flush();
             System.out.println("Servidor: ID " + playerId + " enviado al cliente.");
        } catch (IOException e) {
             System.err.println("Error al enviar ID inicial al cliente " + playerId + ": " + e.getMessage());
             running = false;
        }


        while (running) {
            try {

                Object receivedObject = inputStream.readObject();


                if (receivedObject instanceof MessageAction) {
                    MessageAction action = (MessageAction) receivedObject;
                    servidor.procesarAccionCliente(this.playerId, action);
                } else {
                    System.err.println("Servidor: Recibido objeto inesperado del cliente " + playerId + ": " + receivedObject);
                }

            } catch (SocketException e) {
                System.out.println("Servidor: Cliente " + playerId + " desconectado (SocketException): " + e.getMessage());
                running = false;
            } catch (IOException e) {
                System.err.println("Error de I/O leyendo del cliente " + playerId + ": " + e.getMessage());
                running = false;
            } catch (ClassNotFoundException e) {
                System.err.println("Error: Clase no encontrada al leer del cliente " + playerId + ": " + e.getMessage());
                running = false;
            }
        }


        servidor.eliminarCliente(this);
        closeConnection();
        System.out.println("Servidor: Conexión con cliente " + playerId + " cerrada.");
    }


    public void sendGameState(GameState gameState) {
        if (running && outputStream != null) {
            try {
                outputStream.writeObject(gameState);
                outputStream.reset();
                outputStream.flush();
            } catch (SocketException se) {

                System.err.println("Error de Socket al enviar estado a cliente " + playerId + ". Desconectando: " + se.getMessage());
                running = false;
            } catch (IOException e) {
                System.err.println("Error de I/O al enviar estado a cliente " + playerId + ": " + e.getMessage());
                 running = false;
            }
        }
    }


    public void closeConnection() {
        running = false;
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


    public int getPlayerId() {
        return playerId;
    }


     public boolean isRunning() {
         return running;
     }
}