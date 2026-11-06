package com.cineverse.catalog;

import jakarta.persistence.*;

import java.util.*;

@Entity
@Table(name = "countries")
public class Country {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String name;

    @Column(unique = true)
    public String slug;

    public boolean active = true;
}
