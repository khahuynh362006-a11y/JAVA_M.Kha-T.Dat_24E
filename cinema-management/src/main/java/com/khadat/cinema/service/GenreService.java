package com.khadat.cinema.service;

import com.khadat.cinema.dto.PageResponse;
import com.khadat.cinema.dto.genre.*;
import com.khadat.cinema.entity.Genre;
import com.khadat.cinema.exception.*;
import com.khadat.cinema.mapper.GenreMapper;
import com.khadat.cinema.repository.*;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly=true)
public class GenreService {
    private final GenreRepository genres;
    private final MovieGenreRepository links;
    public GenreService(GenreRepository genres, MovieGenreRepository links) {
        this.genres=genres; this.links=links;
    }
    public PageResponse<GenreResponse> list(String q, int page, int size, String sort, String direction) {
        String term=CatalogQuery.term(q);
        var pageable=CatalogQuery.page(page,size,sort,direction,Set.of("id","name"));
        var result=genres.findAll((root,query,cb) -> term.isEmpty() ? cb.conjunction()
            : cb.like(root.get("name"),CatalogQuery.contains(term),'!'),pageable);
        return PageResponse.from(result,result.getContent().stream().map(GenreMapper::response).toList());
    }
    public GenreResponse detail(Long id) {
        CatalogQuery.positiveId(id,"id");
        return GenreMapper.response(genres.findById(id).orElseThrow(ResourceNotFoundException::new));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public GenreResponse create(GenreRequest request) {
        if (genres.existsByName(request.name().strip())) throw duplicate();
        Genre genre=new Genre(); GenreMapper.apply(request,genre);
        return GenreMapper.response(genres.saveAndFlush(genre));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public GenreResponse update(Long id, GenreRequest request) {
        CatalogQuery.positiveId(id,"id");
        Genre genre=genres.findLocked(id).orElseThrow(ResourceNotFoundException::new);
        if (genres.existsByNameAndIdNot(request.name().strip(),id)) throw duplicate();
        GenreMapper.apply(request,genre);
        return GenreMapper.response(genres.saveAndFlush(genre));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void delete(Long id) {
        CatalogQuery.positiveId(id,"id");
        Genre genre=genres.findLocked(id).orElseThrow(ResourceNotFoundException::new);
        if (links.existsByIdGenreId(id)) throw new BusinessException("GENRE_IN_USE","Thể loại đang được phim sử dụng.");
        genres.delete(genre); genres.flush();
    }
    private FieldValidationException duplicate() {
        return new FieldValidationException(HttpStatus.CONFLICT,"GENRE_NAME_EXISTS","name","Tên thể loại đã tồn tại.");
    }
}
