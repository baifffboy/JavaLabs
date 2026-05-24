package com.example.lab;

import jakarta.persistence.*;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class DatabaseService {
    private static EntityManagerFactory emf;
    private static final ReentrantLock lock = new ReentrantLock();

    private static synchronized EntityManagerFactory getEntityManagerFactory() {
        if (emf == null || !emf.isOpen()) {
            try {
                emf = Persistence.createEntityManagerFactory("gamePU");
                System.out.println("EntityManagerFactory успешно создан");
            } catch (Exception e) {
                System.err.println("Ошибка создания EntityManagerFactory: " + e.getMessage());
                e.printStackTrace();
            }
        }
        return emf;
    }

    public static void incrementWins(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) return;

        lock.lock();
        try {
            EntityManagerFactory factory = getEntityManagerFactory();
            if (factory == null) {
                System.err.println("Не удалось получить EntityManagerFactory");
                return;
            }

            EntityManager em = factory.createEntityManager();
            try {
                em.getTransaction().begin();

                TypedQuery<Player> query = em.createQuery(
                        "SELECT p FROM Player p WHERE p.playerName = :name", Player.class);
                query.setParameter("name", playerName);
                List<Player> results = query.getResultList();

                Player player;
                if (results.isEmpty()) {
                    player = new Player(playerName, 1);
                    em.persist(player);
                } else {
                    player = results.get(0);
                    player.setWins(player.getWins() + 1);
                    em.merge(player);
                }
                em.getTransaction().commit();
                System.out.println("Победа сохранена для " + playerName);
            } catch (Exception e) {
                if (em.getTransaction().isActive()) {
                    em.getTransaction().rollback();
                }
                System.err.println("Ошибка при сохранении победы: " + e.getMessage());
                e.printStackTrace();
            } finally {
                em.close();
            }
        } finally {
            lock.unlock();
        }
    }

    public static List<Player> getAllPlayers() {
        lock.lock();
        try {
            EntityManagerFactory factory = getEntityManagerFactory();
            if (factory == null) {
                System.err.println("EntityManagerFactory не доступен");
                return List.of();
            }

            EntityManager em = factory.createEntityManager();
            try {
                List<Player> players = em.createQuery("SELECT p FROM Player p ORDER BY p.wins DESC", Player.class)
                        .getResultList();
                System.out.println("Загружено игроков: " + players.size());
                return players;
            } catch (Exception e) {
                System.err.println("Ошибка при получении игроков: " + e.getMessage());
                return List.of();
            } finally {
                em.close();
            }
        } finally {
            lock.unlock();
        }
    }

    // Добавьте этот метод в класс DatabaseService

    public static void resetAllPlayers() {
        lock.lock();
        try {
            EntityManagerFactory factory = getEntityManagerFactory();
            if (factory == null) {
                System.err.println("Не удалось получить EntityManagerFactory");
                return;
            }

            EntityManager em = factory.createEntityManager();
            try {
                em.getTransaction().begin();

                // Удаляем всех игроков
                Query deleteQuery = em.createQuery("DELETE FROM Player");
                int deletedCount = deleteQuery.executeUpdate();

                em.getTransaction().commit();
                System.out.println("Удалено игроков: " + deletedCount);
            } catch (Exception e) {
                if (em.getTransaction().isActive()) {
                    em.getTransaction().rollback();
                }
                System.err.println("Ошибка при сбросе таблицы: " + e.getMessage());
                e.printStackTrace();
                throw e;
            } finally {
                em.close();
            }
        } finally {
            lock.unlock();
        }
    }

    // Добавьте этот метод в класс DatabaseService

    public static void addOrUpdatePlayer(String playerName, int wins) {
        if (playerName == null || playerName.trim().isEmpty()) return;

        lock.lock();
        try {
            EntityManagerFactory factory = getEntityManagerFactory();
            if (factory == null) {
                System.err.println("Не удалось получить EntityManagerFactory");
                return;
            }

            EntityManager em = factory.createEntityManager();
            try {
                em.getTransaction().begin();

                TypedQuery<Player> query = em.createQuery(
                        "SELECT p FROM Player p WHERE p.playerName = :name", Player.class);
                query.setParameter("name", playerName);
                List<Player> results = query.getResultList();

                if (results.isEmpty()) {
                    Player player = new Player(playerName, wins);
                    em.persist(player);
                    System.out.println("Добавлен новый игрок: " + playerName + " с победами: " + wins);
                } else {
                    Player player = results.get(0);
                    player.setWins(wins);
                    em.merge(player);
                    System.out.println("Обновлён игрок: " + playerName + " теперь побед: " + wins);
                }
                em.getTransaction().commit();
            } catch (Exception e) {
                if (em.getTransaction().isActive()) {
                    em.getTransaction().rollback();
                }
                System.err.println("Ошибка при добавлении/обновлении игрока: " + e.getMessage());
                e.printStackTrace();
            } finally {
                em.close();
            }
        } finally {
            lock.unlock();
        }
    }

    public static void shutdown() {
        lock.lock();
        try {
            if (emf != null && emf.isOpen()) {
                emf.close();
                emf = null;
                System.out.println("EntityManagerFactory закрыт");
            }
        } finally {
            lock.unlock();
        }
    }
}