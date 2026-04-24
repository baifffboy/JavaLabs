package com.example.lab;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private static final int PORT = 12345;
    private static final int MAX_PLAYERS = 4;
    private ServerSocket serverSocket;
    private List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private Map<Integer, List<ClientHandler>> gameRooms = new ConcurrentHashMap<>();
    private Map<Integer, Boolean> roomGameActive = new ConcurrentHashMap<>();
    private Set<String> activeNames = new HashSet<>();

    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Сервер запущен на порту " + PORT);
            System.out.println("Максимум игроков: " + MAX_PLAYERS);
            System.out.println("Свободно мест: " + (MAX_PLAYERS - clients.size()));

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Новое подключение!");

                if (clients.size() >= MAX_PLAYERS) {
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    out.println("SERVER_FULL");
                    socket.close();
                    System.out.println("Сервер заполнен, отказ. Свободно мест: 0");
                    continue;
                }

                ClientHandler handler = new ClientHandler(socket, this);
                clients.add(handler);
                new Thread(handler).start();
                System.out.println("Свободно мест: " + (MAX_PLAYERS - clients.size()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void broadcastToRoom(int roomId, String msg, ClientHandler exclude) {
        List<ClientHandler> room = gameRooms.get(roomId);
        if (room != null) {
            for (ClientHandler c : room) {
                if (c != exclude) {
                    c.send(msg);
                }
            }
        }
    }

    public synchronized void broadcastToRoom(int roomId, String msg) {
        List<ClientHandler> room = gameRooms.get(roomId);
        if (room != null) {
            for (ClientHandler c : room) {
                c.send(msg);
            }
        }
    }

    public synchronized void remove(ClientHandler c) {
        clients.remove(c);
        if (c.name != null) {
            activeNames.remove(c.name);
        }

        if (c.roomId != -1) {
            List<ClientHandler> room = gameRooms.get(c.roomId);
            if (room != null) {
                room.remove(c);
                System.out.println("Игрок " + c.name + " покинул комнату " + c.roomId);
                if (room.isEmpty()) {
                    gameRooms.remove(c.roomId);
                    roomGameActive.remove(c.roomId);
                    System.out.println("Комната " + c.roomId + " удалена");
                } else {
                    broadcastToRoom(c.roomId, "PLAYER_LEFT:" + c.name);
                    for (ClientHandler remaining : room) {
                        remaining.ready = false;
                        remaining.gameActive = false;
                    }
                }
            }
        }
        System.out.println("Игрок " + c.name + " отключился. Осталось: " + clients.size());
        System.out.println("Свободно мест: " + (MAX_PLAYERS - clients.size()));
    }

    public synchronized void checkAndStartGame(int roomId) {
        List<ClientHandler> room = gameRooms.get(roomId);
        if (room == null) return;

        ClientHandler player1 = null;
        ClientHandler player2 = null;
        for (ClientHandler c : room) {
            if (c.id == 1) player1 = c;
            if (c.id == 2) player2 = c;
        }

        if (player1 == null || player2 == null) {
            System.out.println("В комнате " + roomId + " нет обоих игроков");
            return;
        }

        System.out.println("=== Проверка комнаты " + roomId + " ===");
        System.out.println("  Игрок1 (ID=1): " + player1.name + " готов=" + player1.ready);
        System.out.println("  Игрок2 (ID=2): " + player2.name + " готов=" + player2.ready);

        if (player1.ready && player2.ready && !roomGameActive.getOrDefault(roomId, false)) {
            System.out.println(">>> ЗАПУСК ИГРЫ в комнате " + roomId + "! <<<");
            roomGameActive.put(roomId, true);

            player1.score = 0;
            player1.shots = 0;
            player1.gameActive = true;
            player1.ready = false;

            player2.score = 0;
            player2.shots = 0;
            player2.gameActive = true;
            player2.ready = false;

            player1.send("OPPONENT:" + player2.name);
            player2.send("OPPONENT:" + player1.name);

            player1.send("SCORE:" + player1.id + ":0:0:" + player1.name);
            player2.send("SCORE:" + player1.id + ":0:0:" + player1.name);
            player1.send("SCORE:" + player2.id + ":0:0:" + player2.name);
            player2.send("SCORE:" + player2.id + ":0:0:" + player2.name);

            player1.send("START");
            player2.send("START");
        }
    }

    public synchronized void handleShot(int roomId, String player, int points, ClientHandler shooter) {
        if (!roomGameActive.getOrDefault(roomId, false)) return;

        shooter.score += points;
        broadcastToRoom(roomId, "SCORE:" + shooter.id + ":" + shooter.score + ":" + shooter.shots + ":" + player);
        System.out.println("Попадание от " + player + " (+" + points + ")");

        if (shooter.score >= 6) {
            roomGameActive.put(roomId, false);
            broadcastToRoom(roomId, "WINNER:" + shooter.id);
            System.out.println("Победитель: " + player);

            List<ClientHandler> room = gameRooms.get(roomId);
            if (room != null) {
                for (ClientHandler c : room) {
                    c.gameActive = false;
                    c.ready = false;
                }
            }
        }
    }

    public synchronized void handleShotCount(int roomId, int shots, ClientHandler shooter) {
        if (!roomGameActive.getOrDefault(roomId, false)) return;

        shooter.shots = shots;
        broadcastToRoom(roomId, "SCORE:" + shooter.id + ":" + shooter.score + ":" + shooter.shots + ":" + shooter.name);
    }

    public synchronized void handleStop(int roomId, String playerName) {
        if (roomGameActive.getOrDefault(roomId, false)) {
            roomGameActive.put(roomId, false);
            broadcastToRoom(roomId, "STOP:" + playerName + " остановил игру");

            List<ClientHandler> room = gameRooms.get(roomId);
            if (room != null) {
                for (ClientHandler c : room) {
                    c.gameActive = false;
                    c.ready = false;
                }
            }
        }
    }

    private class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private Server server;
        private String name;
        private int id;
        private int roomId = -1;
        private boolean ready = false;
        private boolean gameActive = false;
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
                int requestedRoom = Integer.parseInt(parts[2]);

                System.out.println("Подключение: " + name + ", ID=" + id + ", комната=" + requestedRoom);

                // Проверка: существует ли уже комната?
                List<ClientHandler> room = server.gameRooms.get(requestedRoom);

                // Проверка: комната заполнена?
                if (room != null && room.size() >= 2) {
                    out.println("ROOM_FULL");
                    socket.close();
                    System.out.println("Комната " + requestedRoom + " заполнена");
                    return;
                }

                // Проверка: в комнате уже есть игрок с таким ID?
                if (room != null) {
                    for (ClientHandler c : room) {
                        if (c.id == id) {
                            out.println("ID_TAKEN");
                            socket.close();
                            System.out.println("В комнате уже есть игрок с ID=" + id);
                            return;
                        }
                    }
                }

                // Проверка уникальности имени ТОЛЬКО в этой комнате
                if (room != null) {
                    for (ClientHandler c : room) {
                        if (c.name != null && c.name.equals(name)) {
                            out.println("NAME_TAKEN");
                            socket.close();
                            System.out.println("Имя " + name + " уже занято в комнате " + requestedRoom);
                            return;
                        }
                    }
                }

                roomId = requestedRoom;
                server.gameRooms.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>()).add(this);
                server.roomGameActive.putIfAbsent(roomId, false);

                out.println("OK:" + id + ":" + roomId);
                System.out.println("Игрок " + name + " (ID:" + id + ") подключен в комнату " + roomId);

                // Отправляем информацию о других игроках
                List<ClientHandler> currentRoom = server.gameRooms.get(roomId);
                for (ClientHandler c : currentRoom) {
                    if (c != this && c.name != null) {
                        out.println("NEW_PLAYER:" + c.id + ":" + c.name);
                        out.println("SCORE:" + c.id + ":" + c.score + ":" + c.shots + ":" + c.name);
                    }
                }

                // Уведомляем других
                for (ClientHandler c : currentRoom) {
                    if (c != this) {
                        c.send("NEW_PLAYER:" + id + ":" + name);
                        c.send("SCORE:" + id + ":" + score + ":" + shots + ":" + name);
                    }
                }

                String msg;
                while ((msg = in.readLine()) != null) {
                    System.out.println("От " + name + ": " + msg);

                    if (msg.equals("READY")) {
                        ready = true;
                        System.out.println(name + " готов!");
                        server.checkAndStartGame(roomId);
                    }
                    else if (msg.startsWith("SHOT:")) {
                        int points = Integer.parseInt(msg.split(":")[1]);
                        server.handleShot(roomId, name, points, this);
                    }
                    else if (msg.startsWith("SHOT_COUNT:")) {
                        int shots = Integer.parseInt(msg.split(":")[1]);
                        server.handleShotCount(roomId, shots, this);
                    }
                    else if (msg.equals("STOP")) {
                        server.handleStop(roomId, name);
                    }
                }
            } catch (IOException e) {
                System.out.println(name + " отключился");
            } finally {
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