package com.khadat.cinema.dto.genre;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenreRequest(@NotBlank @Size(max=100) String name, @Size(max=500) String description) { }
