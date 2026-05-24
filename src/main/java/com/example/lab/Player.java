package com.example.lab;

import jakarta.persistence.*;

@Entity
@Table(name = "players")
@NamedQuery(name = "Player.findByName", query = "SELECT p FROM Player p WHERE p.playerName = :name")
public class Player {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String playerName;

    @Column(nullable = false)
    private int wins;

    public Player() {
    }

    public Player(String playerName, int wins) {
        this.playerName = playerName;
        this.wins = wins;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public int getWins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = wins;
    }
}