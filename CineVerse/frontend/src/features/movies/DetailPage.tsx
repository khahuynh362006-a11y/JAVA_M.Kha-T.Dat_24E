import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Play, Star, Ticket, ArrowLeft, Flag, Trash2, Check } from "lucide-react";
import { toast } from "sonner";
import { api, backdrop, date, send } from "../../lib/api";
import { useAuth } from "../../app/AuthProvider";
import {
  Button,
  Loading,
  ErrorState,
  Modal,
  Pagination,
  Empty,
} from "../../components/ui";
import { FavoriteButton, MovieRow } from "../../components/MovieCard";
import type { Detail, Paged, Review, Playback } from "../../types";
import { ShowtimePicker } from "../booking/BookingPages";
import { VideoPlayer } from "../streaming/PlayerPage";
export default function DetailPage() {
  const { slug } = useParams();
  const [trailer, setTrailer] = useState(false);
  const data = useQuery({
    queryKey: ["detail", slug],
    queryFn: () => api<Detail>("/movies/" + slug),
  });
  const trailerData = useQuery({
    queryKey: ["trailer", data.data?.movie.id],
    queryFn: () => api<Playback>(`/movies/${data.data?.movie.id}/trailer`),
    enabled: trailer && !!data.data,
  });
  if (data.isLoading)
    return (
      <div className="page container">
        <Loading />
      </div>
    );
  if (data.error)
    return (
      <div className="page container">
        <ErrorState error={data.error} retry={() => void data.refetch()} />
      </div>
    );
  if (!data.data) return null;
  const { movie: m, related } = data.data;
  const playUrl = "/dat-ve/" + m.id;
  return (
    <>
      <section className="detail-hero">
        <img className="detail-backdrop" src={backdrop(m)} alt="" />
        <div className="detail-shade" />
        <div className="container detail-main">
          <Link to="/kham-pha" className="back-link">
            <ArrowLeft size={16} />
            Trở lại khám phá
          </Link>
          <div className="detail-layout">
            <img className="detail-poster" src={m.poster} alt={m.title} />
            <div className="detail-copy">
              <span className="eyebrow">CINEVERSE CINEMAS</span>
              <h1>{m.title}</h1>
              <p className="original-title">{m.originalTitle}</p>
              <div className="hero-meta">
                <span className="rating">
                  <Star size={15} fill="currentColor" />
                  {m.rating ? m.rating.toFixed(1) : "Chưa có"} <small>/5</small>
                </span>
                <i />
                <span>{m.releaseYear}</span>
                <span>{m.duration} phút</span>
                <span className="age-badge">{m.ageRating}</span>
                <span className="quality-badge">HD</span>
              </div>
              <div className="genre-tags">
                {m.genres.map((g) => (
                  <Link key={g.id} to={`/the-loai/${g.slug}`}>
                    {g.name}
                  </Link>
                ))}
              </div>
              <p className="detail-description">{m.shortDescription}</p>
              <div className="hero-cta">
                <Button asChild>
                  <Link to={playUrl}>
                    <Ticket size={18} />
                    Đặt vé ngay
                  </Link>
                </Button>
                <Button variant="secondary" onClick={() => setTrailer(true)}>
                  <Play size={16} />
                  Xem trailer
                </Button>
                <FavoriteButton movie={m} className="hero-save icon-btn" />
              </div>
              <div className="detail-credits">
                <span>
                  Đạo diễn <strong>{m.director}</strong>
                </span>
                <span>
                  Diễn viên <strong>{m.castNames}</strong>
                </span>
                <span>
                  Ngôn ngữ <strong>{m.language}</strong>
                </span>
                <span>
                  Định dạng <strong>2D · Dolby 2D</strong>
                </span>
              </div>
            </div>
          </div>
        </div>
      </section>
      <div className="container detail-body">
        <section className="synopsis">
          <span className="eyebrow">THE STORY</span>
          <h2>Về bộ phim</h2>
          <p>{m.synopsis}</p>
          <div className="notice subtle">
            <Check size={17} />
            <span>
              Phim và rạp trong dữ liệu demo là hư cấu. Trailer minh họa có ghi nguồn.
            </span>
          </div>
        </section>
        <ShowtimePicker movieId={m.id} compact />
        <Reviews movieId={m.id} />
      </div>
      <MovieRow
        title="Câu chuyện tiếp theo của bạn"
        eyebrow="MORE TO DISCOVER"
        movies={related}
      />
      <Modal
        open={trailer}
        onOpenChange={setTrailer}
        title={`Trailer · ${m.title}`}
        className="trailer-dialog"
      >
        {trailerData.isLoading ? (
          <Loading />
        ) : trailerData.error ? (
          <ErrorState
            error={trailerData.error}
            retry={() => void trailerData.refetch()}
          />
        ) : (
          trailerData.data && (
            <>
              <VideoPlayer playback={trailerData.data} trailer />
              <p className="muted">
                Trailer minh họa Sintel · © Blender Foundation |{" "}
                <a href="https://durian.blender.org" target="_blank" rel="noreferrer">
                  durian.blender.org
                </a>{" "}
                ·{" "}
                <a
                  href="https://creativecommons.org/licenses/by/3.0/"
                  target="_blank"
                  rel="noreferrer"
                >
                  CC BY 3.0
                </a>
              </p>
            </>
          )
        )}
      </Modal>
    </>
  );
}
function Reviews({ movieId }: { movieId: number }) {
  const { user } = useAuth();
  const [page, setPage] = useState(0),
    [stars, setStars] = useState(5),
    [content, setContent] = useState(""),
    [editing, setEditing] = useState<number | null>(null),
    [reportId, setReportId] = useState<number | null>(null),
    [reason, setReason] = useState("");
  const cache = useQueryClient();
  const data = useQuery({
    queryKey: ["reviews", movieId, page],
    queryFn: () => api<Paged<Review>>(`/movies/${movieId}/reviews?page=${page}`),
  });
  const save = useMutation({
    mutationFn: () =>
      editing
        ? send(`/reviews/${editing}`, "PATCH", { stars, content })
        : send(`/movies/${movieId}/reviews`, "POST", { stars, content }),
    onSuccess: () => {
      setContent("");
      setEditing(null);
      void cache.invalidateQueries({ queryKey: ["reviews", movieId] });
      void cache.invalidateQueries({ queryKey: ["detail"] });
      toast.success("Đã lưu đánh giá của bạn");
    },
    onError: (e: Error) => toast.error(e.message),
  });
  async function remove(id: number) {
    try {
      await send(`/reviews/${id}`, "DELETE");
      await cache.invalidateQueries({ queryKey: ["reviews", movieId] });
      await cache.invalidateQueries({ queryKey: ["detail"] });
      toast.success("Đã xóa đánh giá");
    } catch (e) {
      toast.error((e as Error).message);
    }
  }
  return (
    <section className="reviews-section">
      <div className="section-heading">
        <div>
          <span className="eyebrow">AFTER THE CREDITS</span>
          <h2>
            Góc nhìn khán giả<span className="section-dot">.</span>
          </h2>
        </div>
        <span className="muted">{data.data?.totalElements ?? 0} đánh giá</span>
      </div>
      {user ? (
        <form
          className="review-form"
          onSubmit={(e) => {
            e.preventDefault();
            save.mutate();
          }}
        >
          <div className="star-selector" role="group" aria-label="Chọn điểm đánh giá">
            {[1, 2, 3, 4, 5].map((s) => (
              <button
                type="button"
                key={s}
                aria-label={`${s} sao`}
                aria-pressed={stars === s}
                onClick={() => setStars(s)}
              >
                <Star size={23} fill={stars >= s ? "currentColor" : "none"} />
              </button>
            ))}
            <span>{stars}/5 · Chia sẻ cảm nhận của bạn</span>
          </div>
          <textarea
            aria-label="Nội dung đánh giá"
            required
            minLength={1}
            maxLength={2000}
            placeholder="Điều gì khiến bạn nhớ về bộ phim này?"
            value={content}
            onChange={(e) => setContent(e.target.value)}
          />
          <div>
            <small>Mỗi tài khoản có một đánh giá / phim. Gửi lại để cập nhật.</small>
            <Button disabled={save.isPending}>
              {editing ? "Cập nhật đánh giá" : "Gửi đánh giá"}
            </Button>
            {editing && (
              <Button
                type="button"
                variant="ghost"
                onClick={() => {
                  setEditing(null);
                  setContent("");
                }}
              >
                Hủy
              </Button>
            )}
          </div>
        </form>
      ) : (
        <div className="notice">
          <Link to="/dang-nhap">Đăng nhập</Link> để đánh giá và chia sẻ cảm nhận về bộ
          phim.
        </div>
      )}
      {data.isLoading ? (
        <Loading />
      ) : data.error ? (
        <ErrorState error={data.error} retry={() => void data.refetch()} />
      ) : data.data?.content.length ? (
        <div className="review-list">
          {data.data.content.map((r) => (
            <article key={r.id} className="review-item">
              <span className="avatar">
                {r.avatar ? <img src={r.avatar} alt="" /> : r.name.charAt(0)}
              </span>
              <div>
                <header>
                  <strong>{r.name}</strong>
                  <span className="rating">
                    <Star size={12} fill="currentColor" />
                    {r.stars}/5
                  </span>
                  <time>{date(r.createdAt)}</time>
                </header>
                <p>{r.content}</p>
                <div className="review-actions">
                  {r.userId === user?.id ? (
                    <>
                      <button
                        onClick={() => {
                          setEditing(r.id);
                          setContent(r.content);
                          setStars(r.stars);
                        }}
                      >
                        Chỉnh sửa
                      </button>
                      <button onClick={() => void remove(r.id)}>
                        <Trash2 size={13} />
                        Xóa
                      </button>
                    </>
                  ) : (
                    user && (
                      <button onClick={() => setReportId(r.id)}>
                        <Flag size={12} />
                        Báo cáo
                      </button>
                    )
                  )}
                </div>
              </div>
            </article>
          ))}
        </div>
      ) : (
        <Empty
          title="Hãy là người mở lời"
          message="Chia sẻ đánh giá đầu tiên về câu chuyện này."
        />
      )}
      <Pagination page={page} total={data.data?.totalPages ?? 0} onChange={setPage} />
      <Modal
        open={reportId !== null}
        onOpenChange={(o) => {
          if (!o) setReportId(null);
        }}
        title="Báo cáo đánh giá"
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            void send(`/reviews/${reportId}/report`, "POST", { reason })
              .then(() => {
                toast.success("Đã gửi báo cáo");
                setReportId(null);
                setReason("");
              })
              .catch((e: Error) => toast.error(e.message));
          }}
        >
          <label>
            Lý do báo cáo
            <textarea
              required
              maxLength={500}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </label>
          <Button>Gửi báo cáo</Button>
        </form>
      </Modal>
    </section>
  );
}
