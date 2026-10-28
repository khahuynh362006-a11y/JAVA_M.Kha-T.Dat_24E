package com.khadat.cinema.service;

import com.khadat.cinema.dto.PageResponse;
import com.khadat.cinema.dto.genre.GenreResponse;
import com.khadat.cinema.dto.movie.*;
import com.khadat.cinema.entity.*;
import com.khadat.cinema.exception.*;
import com.khadat.cinema.mapper.*;
import com.khadat.cinema.repository.*;
import jakarta.persistence.criteria.Predicate;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly=true)
public class MovieService {
    private final MovieRepository movies;
    private final GenreRepository genres;
    private final MovieGenreRepository links;
    private final Clock clock;
    public MovieService(MovieRepository movies, GenreRepository genres, MovieGenreRepository links, Clock clock) {
        this.movies=movies; this.genres=genres; this.links=links; this.clock=clock;
    }
    public PageResponse<MovieResponse> list(String q, String status, Long genreId, int page, int size, String sort, String direction) {
        String term=CatalogQuery.term(q);
        if (status != null && !Set.of("ACTIVE","INACTIVE").contains(status))
            throw CatalogQuery.invalid("status","Trạng thái phải là ACTIVE hoặc INACTIVE.");
        if (genreId != null) CatalogQuery.positiveId(genreId,"genreId");
        var pageable=CatalogQuery.page(page,size,sort,direction,Set.of("id","code","title","durationMinutes","minimumAge","status","createdAt","updatedAt"));
        var result=movies.findAll((root,query,cb) -> {
            List<Predicate> filters=new ArrayList<>();
            if (!term.isEmpty()) filters.add(cb.or(cb.like(root.get("title"),CatalogQuery.contains(term),'!'),
                cb.like(root.get("code"),CatalogQuery.contains(term),'!')));
            if (status != null) filters.add(cb.equal(root.get("status"),status));
            if (genreId != null) {
                var sub=query.subquery(Long.class); var link=sub.from(MovieGenre.class);
                sub.select(link.get("id").get("movieId")).where(cb.equal(link.get("id").get("movieId"),root.get("id")),
                    cb.equal(link.get("id").get("genreId"),genreId));
                filters.add(cb.exists(sub));
            }
            return cb.and(filters.toArray(Predicate[]::new));
        },pageable);
        return PageResponse.from(result,responses(result.getContent()));
    }
    public MovieResponse detail(Long id) {
        CatalogQuery.positiveId(id,"id");
        return responses(List.of(movies.findById(id).orElseThrow(ResourceNotFoundException::new))).getFirst();
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public MovieResponse create(MovieRequest request) {
        validateContent(request);
        List<Long> ids=validateGenres(request.genreIds());
        String code=request.code().strip().toUpperCase(Locale.ROOT);
        if (movies.existsByCode(code)) throw duplicate();
        Movie movie=new Movie(); MovieMapper.apply(request,movie); movies.saveAndFlush(movie);
        replaceGenres(movie.getId(),ids);
        return responses(List.of(movie)).getFirst();
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public MovieResponse update(Long id, MovieRequest request) {
        CatalogQuery.positiveId(id,"id");
        Movie movie=movies.findLocked(id).orElseThrow(ResourceNotFoundException::new);
        validateContent(request);
        // D-04 is still pending: conservatively freeze duration/age for ANY showtime history.
        if ((!movie.getDurationMinutes().equals(request.durationMinutes()) || !movie.getMinimumAge().equals(request.minimumAge()))
            && movies.countShowtimes(id)>0)
            throw new BusinessException("MOVIE_SCHEDULE_FIELDS_LOCKED","Phim đã có suất chiếu; chưa được đổi thời lượng hoặc giới hạn tuổi.");
        if ("INACTIVE".equals(request.status()) && movies.countUnfinishedShowtimes(id,LocalDateTime.now(clock))>0)
            throw new BusinessException("MOVIE_HAS_UNFINISHED_SHOWTIMES","Phim còn suất chưa hủy chưa kết thúc.");
        List<Long> ids=validateGenres(request.genreIds());
        if (movies.existsByCodeAndIdNot(request.code().strip().toUpperCase(Locale.ROOT),id)) throw duplicate();
        MovieMapper.apply(request,movie); movies.saveAndFlush(movie);
        replaceGenres(id,ids);
        return responses(List.of(movie)).getFirst();
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void delete(Long id) {
        CatalogQuery.positiveId(id,"id");
        Movie movie=movies.findLocked(id).orElseThrow(ResourceNotFoundException::new);
        if (movies.countShowtimes(id)>0) throw new BusinessException("MOVIE_HAS_SHOWTIMES","Không thể xóa phim đã có suất chiếu.");
        links.deleteForMovie(id); movies.delete(movie); movies.flush();
    }
    private List<Long> validateGenres(List<Long> requested) {
        if (requested == null || requested.isEmpty()) throw CatalogQuery.invalid("genreIds","Phải chọn ít nhất một thể loại.");
        requested.forEach(id -> CatalogQuery.positiveId(id,"genreIds"));
        List<Long> ids=requested.stream().distinct().sorted().toList();
        if (genres.findAllLocked(ids).size()!=ids.size())
            throw new FieldValidationException(HttpStatus.BAD_REQUEST,"UNKNOWN_GENRES","genreIds","Có thể loại không tồn tại; hãy tải lại danh sách thể loại.");
        return ids;
    }
    private void replaceGenres(Long movieId, List<Long> genreIds) {
        links.deleteForMovie(movieId);
        for (Long genreId:genreIds) {
            MovieGenre link=new MovieGenre(); link.setId(new MovieGenreId(movieId,genreId)); links.save(link);
        }
        links.flush();
    }
    private void validateContent(MovieRequest request) {
        if (request.description()!=null && request.description().getBytes(StandardCharsets.UTF_8).length>65535)
            throw CatalogQuery.invalid("description","Mô tả không được vượt 65535 byte UTF-8.");
        validateUrl("posterUrl",request.posterUrl()); validateUrl("trailerUrl",request.trailerUrl());
    }
    private void validateUrl(String field, String value) {
        if (value==null || value.isBlank()) return;
        try {
            URI uri=new URI(value.strip());
            if (uri.getScheme()==null || !(uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https"))
                || uri.getHost()==null || uri.getUserInfo()!=null || uri.getPort()==0 || uri.getPort()>65535)
                throw new URISyntaxException("","unsupported URL");
        } catch (URISyntaxException ex) { throw CatalogQuery.invalid(field,"URL phải là HTTP/HTTPS tuyệt đối, có host và không chứa thông tin đăng nhập."); }
    }
    private FieldValidationException duplicate() {
        return new FieldValidationException(HttpStatus.CONFLICT,"MOVIE_CODE_EXISTS","code","Mã phim đã tồn tại.");
    }
    private List<MovieResponse> responses(List<Movie> page) {
        if (page.isEmpty()) return List.of();
        Map<Long,List<GenreResponse>> byMovie=new HashMap<>();
        for (MovieGenreView link:links.findForMovies(page.stream().map(Movie::getId).toList()))
            byMovie.computeIfAbsent(link.movieId(),ignored -> new ArrayList<>())
                .add(new GenreResponse(link.genreId(),link.name(),link.description()));
        return page.stream().map(movie -> MovieMapper.response(movie,byMovie.getOrDefault(movie.getId(),List.of()))).toList();
    }
}
