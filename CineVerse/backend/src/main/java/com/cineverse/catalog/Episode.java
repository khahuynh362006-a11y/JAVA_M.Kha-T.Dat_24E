package com.cineverse.catalog;

import jakarta.persistence.*;

import java.util.*;

@Entity
@Table(name = "episodes")
public class Episode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public Long seasonId;
    public int number;
    public String title;
    public String description;
    public int duration;
    public String videoFile;
    public String subtitleFile;
    public boolean published = true;
    public boolean vip;
}
