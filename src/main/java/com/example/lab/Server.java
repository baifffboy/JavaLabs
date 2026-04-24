package com.example.lab;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private static final int PORT = 12345;
    private ServerSocket serverSocket;
    private List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private boolean gameRunning = false;

    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Сервер запущен");

            while (true) {
                Socket socket = serverSocket.accept();
                if (clients.size() >= 4) {
                    new PrintWriter(socket.getOutputStream(), true).println("FULL");
                    socket.close();
                    continue;
                }
                ClientHandler handler = new ClientHandler(socket, this);
                clients.add(handler);
                new Thread(handler).start();
            }
        } catch (IOException e) {}
    }

    public void broadcast(String msg) {
        for (ClientHandler c : clients) {
            c.send(msg);
        }
    }

    public void remove(ClientHandler c) {
        clients.remove(c);
        if (gameRunning) broadcast("STOP:Игрок отключился");
    }

    public void checkStart() {
        if (gameRunning) return;
        for (ClientHandler c : clients) {
            if (!c.ready) return;
        }
        if (clients.size() >= 2) {
            gameRunning = true;
            for (ClientHandler c : clients) {
                c.score = 0;
                c.shots = 0;
            }
            broadcast("START");
            System.out.println("Игра началась!");
        }
    }

    public void handleShot(String player, int points, ClientHandler shooter) {
        if (!gameRunning) return;
        shooter.score += points;
        shooter.shots++;
        broadcast("SCORE:" + player + ":" + shooter.score + ":" + shooter.shots);

        if (shooter.score >= 6) {
            gameRunning = false;
            broadcast("WINNER:" + player);
            for (ClientHandler c : clients) c.ready = false;
        }
    }

    public void handleStop() {
        if (gameRunning) {
            gameRunning = false;
            broadcast("STOP:Игра остановлена");
            for (ClientHandler c : clients) c.ready = false;
        }
    }

    private class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private Server server;
        private String name;
        private boolean ready = false;
        private int score = 0;
        private int shots = 0;

        ClientHandler(Socket s, Server server) throws IOException {
            this.socket = s;
            this.server = server;
            this.out = new PrintWriter(s.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(s.getInputStream()));
        }

        public void run() {
            try {
                name = in.readLine();
                // Проверка уникальности имени
                boolean nameTaken = false;
                for (ClientHandler c : server.clients) {
                    if (c.name != null && c.name.equals(name) && c != this) {
                        nameTaken = true;
                        break;
                    }
                }
                if (nameTaken) {
                    out.println("NAME_TAKEN");
                    socket.close();
                    return;
                }
                out.println("OK");
                System.out.println("Игрок " + name + " подключился");

                String msg;
                while ((msg = in.readLine()) != null) {
                    if (msg.equals("READY")) {
                        ready = true;
                        server.checkStart();
                    }
                    else if (msg.startsWith("SHOT:")) {
                        int points = Integer.parseInt(msg.split(":")[1]);
                        server.handleShot(name, points, this);
                    }
                    else if (msg.equals("STOP")) {
                        server.handleStop();
                    }
                }
            } catch (IOException e) {}
            finally {
                server.remove(this);
                try { socket.close(); } catch (IOException e) {}
            }
        }

        void send(String msg) { out.println(msg); }
    }

    public static void main(String[] args) {
        new Server().start();
    }
}