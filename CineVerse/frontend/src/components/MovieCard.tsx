import { Link, useNavigate } from "react-router-dom";
import {
  Play,
  Plus,
  Check,
  Star,
  ArrowUpRight,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import { useRef, type ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useAuth } from "../app/AuthProvider";
import { api, send } from "../lib/api";
import type { Movie } from "../types";
export function useFavorites() {
  const { user } = useAuth();
  return useQuery({
    queryKey: ["favorites", user?.id],
    queryFn: () => api<Movie[]>("/favorites"),
    enabled: !!user,
  });
}
export function FavoriteButton({
  movie,
  className = "icon-btn",
}: {
  movie: Movie;
  className?: string;
}) {
  const { user } = useAuth();
  const nav = useNavigate();
  const query = useFavorites();
  const cache = useQueryClient();
  const saved = query.data?.some((m) => m.id === movie.id) ?? false;
  const mutation = useMutation({
    mutationFn: () =>
      saved
        ? send(`/favorites/${movie.id}`, "DELETE")
        : send("/favorites", "POST", { movieId: movie.id }),
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: ["favorites"] });
      toast.success(saved ? "Đã xóa khỏi danh sách" : "Đã thêm vào danh sách");
    },
    onError: (e: Error) => toast.error(e.message),
  });
  return (
    <button
      className={className}
      aria-label={saved ? "Xóa khỏi danh sách" : "Thêm vào danh sách"}
      title={saved ? "Đã lưu trong danh sách" : "Thêm vào danh sách"}
      disabled={mutation.isPending}
      onClick={() =>
        user
          ? mutation.mutate()
          : nav("/dang-nhap?next=" + encodeURIComponent("/phim/" + movie.slug))
      }
    >
      {saved ? <Check size={20} /> : <Plus size={20} />}
    </button>
  );
}
export function MovieCard({ movie, rank }: { movie: Movie; rank?: number }) {
  return (
    <article className={`movie-card ${rank ? "ranked-card" : ""}`}>
      {rank && (
        <span className="rank-number" aria-hidden="true">
          {rank.toString().padStart(2, "0")}
        </span>
      )}
      <div className="poster-wrap">
        <Link to={`/phim/${movie.slug}`} aria-label={`Chi tiết ${movie.title}`}>
          <img
            src={movie.poster}
            alt={`Poster ${movie.title}`}
            loading="lazy"
            onError={(e) => {
              e.currentTarget.onerror = null;
              e.currentTarget.src = "/art/fallback.svg";
            }}
          />
        </Link>
        <span className="poster-quality">2D</span>

        <div className="poster-overlay">
          <Link
            className="play-circle"
            to={`/phim/${movie.slug}`}
            aria-label={`Khám phá ${movie.title}`}
          >
            <Play size={23} fill="currentColor" />
          </Link>
          <FavoriteButton movie={movie} />
        </div>
      </div>
      <div className="movie-card-info">
        <Link to={`/phim/${movie.slug}`} title={movie.title}>
          {movie.title}
        </Link>
        <div className="card-meta">
          <span>{movie.releaseYear}</span>
          <i />
          <span>{movie.type === "SERIES" ? "Phim bộ" : `${movie.duration} phút`}</span>
          <span className="card-rating">
            <Star size={11} fill="currentColor" />
            {movie.rating ? movie.rating.toFixed(1) : "Mới"}
          </span>
        </div>
      </div>
    </article>
  );
}
export function MovieRow({
  title,
  eyebrow,
  movies,
  to,
  ranked = false,
  children,
}: {
  title: string;
  eyebrow?: string;
  movies: Movie[];
  to?: string;
  ranked?: boolean;
  children?: ReactNode;
}) {
  const row = useRef<HTMLDivElement>(null);
  if (!movies.length) return null;
  return (
    <section className="movie-section container">
      <div className="section-heading">
        <div>
          {eyebrow && <span className="eyebrow">{eyebrow}</span>}
          <h2>
            {title}
            <span className="section-dot">.</span>
          </h2>
        </div>
        <div className="row-tools">
          {to && (
            <Link to={to} className="text-link">
              Xem tất cả <ArrowUpRight size={15} />
            </Link>
          )}
          <button
            className="icon-btn small"
            aria-label={`Cuộn trái ${title}`}
            onClick={() => row.current?.scrollBy({ left: -600, behavior: "smooth" })}
          >
            <ChevronLeft size={18} />
          </button>
          <button
            className="icon-btn small"
            aria-label={`Cuộn phải ${title}`}
            onClick={() => row.current?.scrollBy({ left: 600, behavior: "smooth" })}
          >
            <ChevronRight size={18} />
          </button>
        </div>
      </div>
      <div ref={row} className="movie-row">
        {movies.map((m, i) => (
          <MovieCard key={m.id} movie={m} rank={ranked ? i + 1 : undefined} />
        ))}
      </div>
      {children}
    </section>
  );
}
