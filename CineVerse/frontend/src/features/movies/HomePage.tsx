import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useQueries, useQuery } from "@tanstack/react-query";
import { motion, AnimatePresence, useReducedMotion } from "framer-motion";
import {
  Play,
  ArrowUpRight,
  ChevronLeft,
  ChevronRight,
  Pause,
  Star,
  Ticket,
  Check,
  ArrowRight,
} from "lucide-react";
import { api, backdrop } from "../../lib/api";
import { useAuth } from "../../app/AuthProvider";
import { Button, Loading, ErrorState } from "../../components/ui";
import { FavoriteButton, MovieRow } from "../../components/MovieCard";
import type { Movie, Paged, Lookup } from "../../types";
const sections = [
  [
    "Đang chiếu tại CineVerse",
    "NOW SHOWING",
    "/movies?screening=now&size=12&sort=featured",
    "/phim-dang-chieu",
  ],
  [
    "Sắp chạm màn ảnh lớn",
    "COMING SOON",
    "/movies?screening=upcoming&size=12",
    "/phim-sap-chieu",
  ],
  ["Top 10 CineVerse", "THE AUDIENCE CHOICE", "/movies/top-rated", "/bang-xep-hang"],
  [
    "Chạm đến những vì sao",
    "SCI-FI ON THE BIG SCREEN",
    "/movies?genre=khoa-hoc-vien-tuong&size=12",
    "/the-loai/khoa-hoc-vien-tuong",
  ],
  [
    "Nhịp tim của hành động",
    "FEEL THE ADRENALINE",
    "/movies?genre=hanh-dong&size=12",
    "/the-loai/hanh-dong",
  ],
  [
    "Những điều ta gọi là yêu",
    "LOVE, IN EVERY FRAME",
    "/movies?genre=tinh-cam&size=12",
    "/the-loai/tinh-cam",
  ],
];
export default function HomePage() {
  const { user } = useAuth();
  const featured = useQuery({
    queryKey: ["featured"],
    queryFn: () => api<Paged<Movie>>("/movies/featured"),
  });
  const rows = useQueries({
    queries: sections.map(([, , url]) => ({
      queryKey: ["home", url],
      queryFn: () => api<Paged<Movie>>(url),
    })),
  });
  const recommendations = useQuery({
    queryKey: ["recommendations", user?.id],
    queryFn: () => api<Movie[]>("/users/me/recommendations"),
    enabled: !!user,
  });
  const genres = useQuery({
    queryKey: ["genres"],
    queryFn: () => api<Lookup[]>("/genres"),
  });
  useEffect(() => {
    document.title = "CineVerse — Your Universe of Cinema";
  }, []);
  if (featured.isLoading)
    return (
      <div className="page container">
        <Loading />
      </div>
    );
  if (featured.error)
    return (
      <div className="page container">
        <ErrorState error={featured.error} retry={() => void featured.refetch()} />
      </div>
    );
  return (
    <>
      <Hero movies={featured.data?.content ?? []} />
      <div className="discovery-strip container">
        <span>MỘT TÂM TRẠNG. MỘT CÂU CHUYỆN.</span>
        <div>
          {[
            ["Tất cả", "/kham-pha"],
            ["Khoa học viễn tưởng", "/the-loai/khoa-hoc-vien-tuong"],
            ["Hành động", "/the-loai/hanh-dong"],
            ["Tình cảm", "/the-loai/tinh-cam"],
            ["Phiêu lưu", "/the-loai/phieu-luu"],
            ["Hoạt hình", "/the-loai/hoat-hinh"],
          ].map(([t, url]) => (
            <Link key={url} to={url}>
              {t}
            </Link>
          ))}
        </div>
        <Link className="strip-arrow" to="/kham-pha" aria-label="Khám phá tất cả">
          <ArrowUpRight size={22} />
        </Link>
      </div>
      {sections.map(([title, eyebrow, , to], i) => (
        <div key={title}>
          {i === 2 && <PremiumBanner />}
          {rows[i].data && (
            <MovieRow
              title={title}
              eyebrow={eyebrow}
              movies={rows[i].data.content}
              to={to}
              ranked={i === 2}
            />
          )}{" "}
          {rows[i].error && (
            <div className="container">
              <ErrorState error={rows[i].error} retry={() => void rows[i].refetch()} />
            </div>
          )}
        </div>
      ))}
      {recommendations.data && (
        <MovieRow
          title="Dành riêng cho bạn"
          eyebrow="YOUR NEXT FAVORITE"
          movies={recommendations.data}
          to="/kham-pha"
        />
      )}
      <section className="container genre-section">
        <div className="section-heading">
          <div>
            <span className="eyebrow">FIND YOUR WORLD</span>
            <h2>
              Hôm nay, bạn muốn đi đâu<span className="section-dot">?</span>
            </h2>
          </div>
          <Link to="/the-loai" className="text-link">
            Mọi thể loại <ArrowUpRight size={15} />
          </Link>
        </div>
        <div className="genre-grid">
          {genres.data?.slice(0, 6).map((g, i) => (
            <Link
              key={g.id}
              to={`/the-loai/${g.slug}`}
              className={`genre-tile genre-${i}`}
            >
              <img src={`/art/scene-${i * 5 + 1}.svg`} alt="" />
              <span>0{i + 1}</span>
              <h3>{g.name}</h3>
              <ArrowUpRight size={23} />
            </Link>
          ))}
        </div>
      </section>
    </>
  );
}
function Hero({ movies }: { movies: Movie[] }) {
  const [active, setActive] = useState(0),
    [paused, setPaused] = useState(false),
    [interacting, setInteracting] = useState(false);
  const reduced = useReducedMotion();
  useEffect(() => {
    if (paused || interacting || reduced || movies.length < 2) return;
    const t = setInterval(() => setActive((a) => (a + 1) % movies.length), 12000);
    return () => clearInterval(t);
  }, [paused, interacting, reduced, movies.length]);
  if (!movies.length) return null;
  const m = movies[active];
  const move = (step: number) => {
    setActive((a) => (a + step + movies.length) % movies.length);
    setPaused(true);
  };
  return (
    <section
      className="hero"
      aria-roledescription="carousel"
      aria-label="Phim nổi bật"
      onMouseEnter={() => setInteracting(true)}
      onMouseLeave={() => setInteracting(false)}
      onFocusCapture={() => setInteracting(true)}
      onBlurCapture={(e) => {
        if (!e.currentTarget.contains(e.relatedTarget)) setInteracting(false);
      }}
    >
      <AnimatePresence mode="sync" initial={false}>
        <motion.div
          key={m.id}
          className="hero-image"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: reduced ? 0 : 1.2 }}
          style={{ backgroundImage: `url(${backdrop(m)})` }}
        />
      </AnimatePresence>
      <div className="hero-shade" />
      <div className="hero-content container">
        <AnimatePresence mode="wait" initial={false}>
          <motion.div
            key={m.id}
            className="hero-copy"
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            transition={{ duration: reduced ? 0 : 0.35 }}
          >
            <div className="hero-eyebrow">
              <span />
              CINEVERSE SPOTLIGHT <i /> NOW IN CINEMAS
            </div>
            <p className="hero-original">{m.originalTitle}</p>
            <h1>
              {m.id === 1 ? (
                <>
                  Phía bên kia
                  <br />
                  <em>chân trời.</em>
                </>
              ) : (
                m.title
              )}
            </h1>
            <div className="hero-meta">
              <span className="rating">
                <Star size={14} fill="currentColor" />{" "}
                {m.rating ? m.rating.toFixed(1) : "Mới"}
                <small>/ 5</small>
              </span>
              <i />
              <span>{m.releaseYear}</span>
              <span>{m.duration} phút</span>
              <span className="age-badge">{m.ageRating}</span>
              <span className="quality-badge">HD</span>
            </div>
            <div className="hero-genres">
              {m.genres.map((g) => (
                <Link to={`/the-loai/${g.slug}`} key={g.id}>
                  {g.name}
                </Link>
              ))}
            </div>
            <p className="hero-description">{m.shortDescription}</p>
            <div className="hero-cta">
              <Button asChild>
                <Link to={`/dat-ve/${m.id}`}>
                  <Ticket size={17} />
                  Đặt vé ngay
                </Link>
              </Button>
              <Button variant="secondary" asChild>
                <Link to={`/phim/${m.slug}`}>
                  <span>Khám phá bộ phim</span>
                  <ArrowUpRight size={17} />
                </Link>
              </Button>
              <FavoriteButton movie={m} className="hero-save icon-btn" />
            </div>
            <div className="hero-note">
              <span className="tiny-dot" />
              Chọn suất chiếu · Chọn ghế · Nhận vé điện tử
            </div>
          </motion.div>
        </AnimatePresence>
      </div>
      <div className="hero-bottom container">
        <div className="hero-pagination">
          <span className="current">{String(active + 1).padStart(2, "0")}</span>
          <div className="hero-indicators">
            {movies.map((x, i) => (
              <button
                key={x.id}
                className={i === active ? "active" : ""}
                onClick={() => {
                  setActive(i);
                  setPaused(true);
                }}
                aria-label={`Hiển thị ${x.title}`}
                aria-current={i === active}
              />
            ))}
          </div>
          <span>{String(movies.length).padStart(2, "0")}</span>
          <button
            className="hero-pause"
            onClick={() => setPaused(!paused)}
            aria-label={paused ? "Tự động chuyển phim" : "Tạm dừng tự động"}
          >
            {paused ? <Play size={12} /> : <Pause size={12} />}
          </button>
        </div>
        <div className="hero-next">
          <div>
            <span>TIẾP THEO TRONG VŨ TRỤ</span>
            <strong>{movies[(active + 1) % movies.length].title}</strong>
          </div>
          <button
            className="icon-btn"
            onClick={() => move(-1)}
            aria-label="Phim nổi bật trước"
          >
            <ChevronLeft size={20} />
          </button>
          <button
            className="icon-btn"
            onClick={() => move(1)}
            aria-label="Phim nổi bật tiếp theo"
          >
            <ChevronRight size={20} />
          </button>
        </div>
      </div>
      <span className="hero-side-label">CURATED STORIES. LIMITLESS WORLDS.</span>
    </section>
  );
}
export function PremiumBanner() {
  return (
    <section className="container">
      <div className="premium-banner">
        <div className="premium-icon">
          <Ticket size={30} />
          <span>CINEVERSE CINEMAS</span>
        </div>
        <div>
          <span className="eyebrow">A LITTLE MORE EXTRAORDINARY</span>
          <h2>Màn ảnh lớn. Cảm xúc trọn vẹn.</h2>
          <p>Chọn chiếc ghế yêu thích. Hẹn nhau tại CineVerse.</p>
          <div className="premium-benefits">
            <span>
              <Check size={13} />
              Chọn ghế trực quan
            </span>
            <span>
              <Check size={13} />
              Nhận vé điện tử
            </span>
            <span>
              <Check size={13} />
              Thanh toán an toàn
            </span>
          </div>
        </div>
        <Button asChild variant="outline">
          <Link to="/rap-chieu">
            Tìm suất chiếu <ArrowRight size={17} />
          </Link>
        </Button>
        <div className="premium-orbit" />
      </div>
    </section>
  );
}
