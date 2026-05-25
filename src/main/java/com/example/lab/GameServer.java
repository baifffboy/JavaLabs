package com.example.lab;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.lang.System.out;

public class GameServer {
    private static final int PORT = 12345;
    private static final int MAX_PLAYERS = 4;
    private ServerSocket serverSocket;
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final Map<Integer, List<ClientHandler>> gameRooms = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> roomGameActive = new ConcurrentHashMap<>();
    private final Set<String> activeNames = new HashSet<>();
    private static org.h2.tools.Server h2TcpServer;
    private static org.h2.tools.Server h2WebServer;
    private final List<ClientHandler> observers = new CopyOnWriteArrayList<>();

    public void start() {
        startH2Database();

        try {
            serverSocket = new ServerSocket(PORT);

            while (true) {
                Socket socket = serverSocket.accept();

                BufferedReader tempReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String tempLine = tempReader.readLine();
                String[] parts = tempLine.split(":");
                int id = Integer.parseInt(parts[1]);

                boolean isObserver = (id == 0);

                if (!isObserver && clients.size() >= MAX_PLAYERS) {
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    out.println("SERVER_FULL");
                    socket.close();
                    continue;
                }

                ClientHandler handler = new ClientHandler(socket, this, tempLine);
                clients.add(handler);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startH2Database() {
        try {
            h2TcpServer = org.h2.tools.Server.createTcpServer(
                    "-tcpPort", "9092",
                    "-tcpAllowOthers",
                    "-tcpDaemon",
                    "-ifNotExists"
            );
            h2TcpServer.start();

            h2WebServer = org.h2.tools.Server.createWebServer(
                    "-webPort", "8082",
                    "-webAllowOthers"
            );
            h2WebServer.start();

        } catch (SQLException e) {
            System.err.println("Failed to start H2 server: " + e.getMessage());
            throw new RuntimeException("Cannot start H2 database", e);
        }
    }

    private void stopH2Database() {
        if (h2TcpServer != null) {
            h2TcpServer.stop();
        }
        if (h2WebServer != null) {
            h2WebServer.stop();
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
                if (room.isEmpty()) {
                    gameRooms.remove(c.roomId);
                    roomGameActive.remove(c.roomId);
                } else {
                    broadcastToRoom(c.roomId, "PLAYER_LEFT:" + c.name);
                    broadcastToObservers("PLAYER_LEFT:" + c.name);

                    for (ClientHandler remaining : room) {
                        remaining.ready = false;
                        remaining.gameActive = false;
                    }
                }
            }
        }
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
            return;
        }

        if (player1.ready && player2.ready && !roomGameActive.getOrDefault(roomId, false)) {
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

            player1.send("SCORE:" + player1.id + ":0:0:" + player1.name + ":" + roomId);
            player2.send("SCORE:" + player1.id + ":0:0:" + player1.name + ":" + roomId);
            player1.send("SCORE:" + player2.id + ":0:0:" + player2.name + ":" + roomId);
            player2.send("SCORE:" + player2.id + ":0:0:" + player2.name + ":" + roomId);

            player1.send("START");
            player2.send("START");

            broadcastToObservers("START");

            broadcastToObservers("SCORE:" + player1.id + ":0:0:" + player1.name + ":" + roomId);
            broadcastToObservers("SCORE:" + player2.id + ":0:0:" + player2.name + ":" + roomId);
            broadcastToObservers("NEW_PLAYER:" + player1.id + ":" + player1.name + ":" + roomId);
            broadcastToObservers("NEW_PLAYER:" + player2.id + ":" + player2.name + ":" + roomId);
        }
    }

    public synchronized void handleShot(int roomId, String player, int points, ClientHandler shooter) {
        if (!roomGameActive.getOrDefault(roomId, false)) return;

        shooter.score += points;
        broadcastToRoom(roomId, "SCORE:" + shooter.id + ":" + shooter.score + ":" + shooter.shots + ":" + shooter.name + ":" + roomId);
        broadcastToObservers("SCORE:" + shooter.id + ":" + shooter.score + ":" + shooter.shots + ":" + shooter.name + ":" + roomId);

        if (shooter.score >= 6) {
            roomGameActive.put(roomId, false);
            broadcastToRoom(roomId, "WINNER:" + shooter.id);

            broadcastToObservers("WINNER:" + shooter.id);

            try {
                DatabaseService.incrementWins(shooter.name);
            } catch (Exception e) {
                System.err.println("Ошибка сохранения победы: " + e.getMessage());
            }

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
        broadcastToRoom(roomId, "SCORE:" + shooter.id + ":" + shooter.score + ":" + shooter.shots + ":" + shooter.name + ":" + roomId);
        broadcastToObservers("SCORE:" + shooter.id + ":" + shooter.score + ":" + shooter.shots + ":" + shooter.name + ":" + roomId);
    }

    public synchronized void handleStop(int roomId, String playerName) {
        if (roomGameActive.getOrDefault(roomId, false)) {
            roomGameActive.put(roomId, false);
            broadcastToRoom(roomId, "STOP:" + playerName + " остановил игру");
            broadcastToObservers("STOP:" + playerName + " остановил игру");

            List<ClientHandler> room = gameRooms.get(roomId);
            if (room != null) {
                for (ClientHandler c : room) {
                    c.gameActive = false;
                    c.ready = false;
                }
            }
        }
    }

    private void sendCurrentGameState(int roomId) {
        List<ClientHandler> room = gameRooms.get(roomId);
        if (room != null) {
            for (ClientHandler player : room) {
                out.println("NEW_PLAYER:" + player.id + ":" + player.name + ":" + roomId);
                out.println("SCORE:" + player.id + ":" + player.score + ":" + player.shots + ":" + player.name + ":" + roomId);
            }
        }
        if (roomGameActive.getOrDefault(roomId, false)) {
            out.println("START");
        } else {
            out.println("STOP:Ожидание игроков");
        }
    }

    private void sendLeaderboardToClient(PrintWriter clientOut) {
        try {
            List<Player> allPlayers = DatabaseService.getAllPlayers();
            if (allPlayers.isEmpty()) {
                clientOut.println("LEADER:Нет данных:0");
            } else {
                for (Player p : allPlayers) {
                    clientOut.println("LEADER:" + p.getPlayerName() + ":" + p.getWins());
                }
            }
            clientOut.println("END_LEADERBOARD");
        } catch (Exception e) {
            System.err.println("Ошибка отправки таблицы лидеров: " + e.getMessage());
            clientOut.println("LEADER:Ошибка:0");
            clientOut.println("END_LEADERBOARD");
        }
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private final PrintWriter out;
        private final BufferedReader in;
        private final GameServer server;
        private String name;
        private int id;
        private int roomId = -1;
        private boolean ready = false;
        private boolean gameActive = false;
        private int score = 0;
        private int shots = 0;
        private final String preReadLine;

        ClientHandler(Socket s, GameServer server, String alreadyReadLine) throws IOException {
            this.socket = s;
            this.server = server;
            this.out = new PrintWriter(s.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            this.preReadLine = alreadyReadLine; // Сохраняем уже прочитанную строку
        }

        public void run() {
            try {
                String line;
                if (preReadLine != null) {
                    line = preReadLine;
                } else {
                    line = in.readLine();
                }
                String[] parts = line.split(":");
                name = parts[0];
                id = Integer.parseInt(parts[1]);
                int requestedRoom = Integer.parseInt(parts[2]);

                boolean isObserver = (id == 0);

                if (isObserver) {
                    out.println("OK:" + 999 + ":" + requestedRoom);
                    server.addObserver(this);

                    sendCurrentGameState(requestedRoom);

                    String msg;
                    while ((msg = in.readLine()) != null) {
                        if (msg.equals("GET_LEADERBOARD")) {
                            sendLeaderboardToClient(out);
                        }
                    }

                    server.removeObserver(this);
                    return;
                }

                List<ClientHandler> room = server.gameRooms.get(requestedRoom);

                if (room != null && room.size() >= 2) {
                    out.println("ROOM_FULL");
                    socket.close();
                    return;
                }

                if (room != null) {
                    for (ClientHandler c : room) {
                        if (c.id == id) {
                            out.println("ID_TAKEN");
                            socket.close();
                            return;
                        }
                    }
                }

                if (room != null) {
                    for (ClientHandler c : room) {
                        if (c.name != null && c.name.equals(name)) {
                            out.println("NAME_TAKEN");
                            socket.close();
                            return;
                        }
                    }
                }

                roomId = requestedRoom;
                server.gameRooms.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>()).add(this);
                server.roomGameActive.putIfAbsent(roomId, false);

                out.println("OK:" + id + ":" + roomId);

                List<ClientHandler> currentRoom = server.gameRooms.get(roomId);
                for (ClientHandler c : currentRoom) {
                    if (c != this && c.name != null) {
                        out.println("NEW_PLAYER:" + c.id + ":" + c.name + ":" + roomId);
                        out.println("SCORE:" + c.id + ":" + c.score + ":" + c.shots + ":" + c.name);
                    }
                }

                for (ClientHandler c : currentRoom) {
                    if (c != this) {
                        c.send("NEW_PLAYER:" + id + ":" + name + ":" + roomId);
                        c.send("SCORE:" + id + ":" + score + ":" + shots + ":" + name);
                    }
                }

                String msg;
                while ((msg = in.readLine()) != null) {
                    if (msg.equals("READY")) {
                        ready = true;
                        server.checkAndStartGame(roomId);
                    } else if (msg.startsWith("SHOT:")) {
                        int points = Integer.parseInt(msg.split(":")[1]);
                        server.handleShot(roomId, name, points, this);
                    } else if (msg.startsWith("SHOT_COUNT:")) {
                        int shots = Integer.parseInt(msg.split(":")[1]);
                        server.handleShotCount(roomId, shots, this);
                    } else if (msg.equals("STOP")) {
                        server.handleStop(roomId, name);
                    } else if (msg.equals("PAUSE_GAME")) {
                        server.broadcastToRoom(roomId, "PAUSE");
                    } else if (msg.equals("RESUME_GAME")) {
                        server.broadcastToRoom(roomId, "RESUME");
                    } else if (msg.equals("ENEMY_SHOT_STOP")) {
                        server.broadcastToRoom(roomId, "ENEMY_SHOT_STOP", this);
                    } else if (msg.equals("PLAYER_SHOT")) {
                        server.broadcastToRoom(roomId, "ENEMY_SHOT:" + id, this);
                    } else if (msg.equals("GET_LEADERBOARD")) {
                        sendLeaderboardToClient(out);
                    } else if (msg.equals("GET_ALL_PLAYERS")) {
                        sendAllPlayersToClient(out);
                    }
                }
            } catch (IOException e) {
                System.out.println(name + " отключился");
            } finally {
                server.remove(this);
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
        }

        void send(String msg) {
            out.println(msg);
        }
    }

    public void addObserver(ClientHandler observer) {
        observers.add(observer);
    }

    public void removeObserver(ClientHandler observer) {
        observers.remove(observer);
    }

    public void broadcastToObservers(String msg) {
        for (ClientHandler observer : observers) {
            observer.send(msg);
        }
    }

    public List<String> getAllActivePlayerNames() {
        List<String> allNames = new ArrayList<>();
        for (List<ClientHandler> room : gameRooms.values()) {
            for (ClientHandler player : room) {
                if (player.name != null && !player.name.isEmpty()) {
                    allNames.add(player.name);
                }
            }
        }
        return allNames;
    }

    private void sendAllPlayersToClient(PrintWriter clientOut) {
        List<String> allPlayers = getAllActivePlayerNames();
        StringBuilder sb = new StringBuilder("ALL_PLAYERS:");
        for (String name : allPlayers) {
            sb.append(name).append(",");
        }
        clientOut.println(sb);
    }

    public static void main(String[] args) {
        final GameServer server = new GameServer();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stopH2Database();
            DatabaseService.shutdown();
        }));

        server.start();
    }
}