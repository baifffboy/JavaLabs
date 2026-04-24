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

    public void broadcast(String msg, ClientHandler exclude) {
        for (ClientHandler c : clients) {
            if (c != exclude) {
                c.send(msg);
            }
        }
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
        int readyCount = 0;
        for (ClientHandler c : clients) {
            if (c.ready) readyCount++;
        }
        if (readyCount == clients.size() && clients.size() >= 2) {
            gameRunning = true;
            for (ClientHandler c : clients) {
                c.score = 0;
                c.shots = 0;
                c.send("SCORE:" + c.id + ":" + c.score + ":" + c.shots + ":" + c.name);
            }
            broadcast("START");
            System.out.println("Игра началась!");
        }
    }

    public void handleShot(String player, int points, ClientHandler shooter) {
        if (!gameRunning) return;
        shooter.score += points;
        // shots обновляется отдельно через SHOT_COUNT
        broadcast("SCORE:" + shooter.id + ":" + shooter.score + ":" + shooter.shots + ":" + player);
        System.out.println("Попадание от " + player + " (+" + points + ")");

        if (shooter.score >= 6) {
            gameRunning = false;
            broadcast("WINNER:" + shooter.id);
            System.out.println("Победитель: " + player);
            for (ClientHandler c : clients) c.ready = false;
        }
    }

    public void handleShotCount(int shots, ClientHandler shooter) {
        if (!gameRunning) return;
        shooter.shots = shots;
        broadcast("SCORE:" + shooter.id + ":" + shooter.score + ":" + shooter.shots + ":" + shooter.name);
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
        private int id;
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
                String line = in.readLine();
                String[] parts = line.split(":");
                name = parts[0];
                id = Integer.parseInt(parts[1]);

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
                out.println("OK:" + id);
                System.out.println("Игрок " + name + " (ID:" + id + ") подключен");

                for (ClientHandler c : server.clients) {
                    if (c != this && c.name != null) {
                        out.println("NEW_PLAYER:" + c.id + ":" + c.name);
                        out.println("SCORE:" + c.id + ":" + c.score + ":" + c.shots + ":" + c.name);
                    }
                }
                server.broadcast("NEW_PLAYER:" + id + ":" + name, this);

                String msg;
                while ((msg = in.readLine()) != null) {
                    if (msg.equals("READY")) {
                        ready = true;
                        System.out.println(name + " готов");
                        server.checkStart();
                    }
                    else if (msg.startsWith("SHOT:")) {
                        int points = Integer.parseInt(msg.split(":")[1]);
                        server.handleShot(name, points, this);
                    }
                    else if (msg.startsWith("SHOT_COUNT:")) {
                        int shots = Integer.parseInt(msg.split(":")[1]);
                        server.handleShotCount(shots, this);
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

        void send(String msg) {
            out.println(msg);
        }
    }

    public static void main(String[] args) {
        new Server().start();
    }
}