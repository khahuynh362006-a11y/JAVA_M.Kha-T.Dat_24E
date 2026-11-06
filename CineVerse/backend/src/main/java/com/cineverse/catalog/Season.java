package com.cineverse.catalog;

import jakarta.persistence.*;

import java.util.*;

@Entity
@Table(name = "seasons")
public class Season {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public Long movieId;
    public int number;
    public String title;
}
