import { useEffect, useState } from "react";
import { Link, useLocation, useParams, useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Search, SlidersHorizontal, X, ArrowUpRight } from "lucide-react";
import { api } from "../../lib/api";
import {
  Button,
  Loading,
  Empty,
  ErrorState,
  Pagination,
  Modal,
} from "../../components/ui";
import { MovieCard } from "../../components/MovieCard";
import type { Movie, Paged, Lookup } from "../../types";
const titles: Record<string, [string, string]> = {
  "/kham-pha": ["Khám phá điện ảnh", "Một thế giới câu chuyện đang chờ bạn."],
  "/phim-dang-chieu": [
    "Đang chiếu tại CineVerse.",
    "Chọn phim, tìm suất chiếu và đặt chiếc ghế dành riêng cho bạn.",
  ],
  "/phim-sap-chieu": [
    "Sắp chạm màn ảnh lớn.",
    "Những câu chuyện đang chờ được công bố lịch chiếu.",
  ],
  "/phim-le": [
    "Một bộ phim. Ngàn cảm xúc.",
    "Dành một tối cho những câu chuyện đáng nhớ.",
  ],
  "/phim-bo": [
    "Câu chuyện chưa dừng lại.",
    "Những thế giới khiến bạn muốn ở lại lâu hơn.",
  ],
  "/phim-moi": ["Những câu chuyện mới.", "Vừa cập bến thư viện CineVerse."],
  "/thinh-hanh": [
    "Tâm điểm hôm nay.",
    "Được sắp xếp theo lượt xem thực tế và ưu tiên biên tập.",
  ],
  "/bang-xep-hang": [
    "Được yêu thích nhất.",
    "Khám phá những phim có đánh giá cao từ khán giả.",
  ],
  "/tim-kiem": ["Tìm một câu chuyện.", "Điện ảnh luôn có một điều dành cho bạn."],
};
export default function DiscoverPage() {
  const location = useLocation();
  const params = useParams();
  const [search, setSearch] = useSearchParams();
  const [drawer, setDrawer] = useState(false),
    [text, setText] = useState(search.get("q") ?? "");
  const genres = useQuery({
    queryKey: ["genres"],
    queryFn: () => api<Lookup[]>("/genres"),
  });
  const countries = useQuery({
    queryKey: ["countries"],
    queryFn: () => api<Lookup[]>("/countries"),
  });
  const isGenres = location.pathname === "/the-loai";
  const q = new URLSearchParams(search);
  if (location.pathname === "/phim-dang-chieu") q.set("screening", "now");
  if (location.pathname === "/phim-sap-chieu") q.set("screening", "upcoming");
  if (location.pathname === "/phim-le") q.set("type", "MOVIE");
  if (location.pathname === "/phim-bo") q.set("type", "SERIES");
  if (location.pathname === "/thinh-hanh" && !q.has("sort")) q.set("sort", "popular");
  if (location.pathname === "/bang-xep-hang" && !q.has("sort")) q.set("sort", "rating");
  if (params.slug) {
    q.set(location.pathname.startsWith("/quoc-gia") ? "country" : "genre", params.slug);
  }
  q.set("size", "18");
  const movies = useQuery({
    queryKey: ["movies", q.toString()],
    queryFn: ({ signal }) => api<Paged<Movie>>("/movies?" + q, { signal }),
    enabled: !isGenres,
  });
  let [title, subtitle] = titles[location.pathname] ?? [
    "Khám phá thế giới điện ảnh.",
    "Chọn một thể loại, tìm một cảm hứng.",
  ];
  if (params.slug) {
    title =
      (location.pathname.startsWith("/quoc-gia") ? countries.data : genres.data)?.find(
        (g) => g.slug === params.slug,
      )?.name ?? "Bộ sưu tập điện ảnh";
    subtitle = "Những góc nhìn, những câu chuyện, một niềm đam mê.";
  }
  useEffect(() => {
    document.title = title + " — CineVerse";
  }, [title]);
  useEffect(() => {
    setText(search.get("q") ?? "");
  }, [search]);
  const change = (key: string, value: string) => {
    const next = new URLSearchParams(search);
    if (value) next.set(key, value);
    else next.delete(key);
    if (key !== "page") next.delete("page");
    setSearch(next);
  };
  const filters = (
    <div className="filter-fields">
      <label>
        Thể loại
        <select
          value={q.get("genre") ?? ""}
          onChange={(e) => change("genre", e.target.value)}
          disabled={!!params.slug && !location.pathname.startsWith("/quoc-gia")}
        >
          <option value="">Tất cả thể loại</option>
          {genres.data?.map((g) => (
            <option key={g.id} value={g.slug}>
              {g.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        Quốc gia
        <select
          value={q.get("country") ?? ""}
          onChange={(e) => change("country", e.target.value)}
          disabled={!!params.slug && location.pathname.startsWith("/quoc-gia")}
        >
          <option value="">Tất cả quốc gia</option>
          {countries.data?.map((g) => (
            <option key={g.id} value={g.slug}>
              {g.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        Năm phát hành
        <select
          value={q.get("year") ?? ""}
          onChange={(e) => change("year", e.target.value)}
        >
          <option value="">Mọi thời điểm</option>
          {Array.from({ length: 8 }, (_, i) => 2026 - i).map((y) => (
            <option key={y}>{y}</option>
          ))}
        </select>
      </label>
      <label>
        Lịch chiếu
        <select
          aria-label="Lọc lịch chiếu"
          value={q.get("screening") ?? ""}
          onChange={(e) => change("screening", e.target.value)}
          disabled={["/phim-dang-chieu", "/phim-sap-chieu"].includes(location.pathname)}
        >
          <option value="">Tất cả phim</option>
          <option value="now">Đang mở bán vé</option>
          <option value="upcoming">Sắp chiếu</option>
        </select>
      </label>
      <label>
        Đánh giá
        <select
          value={q.get("rating") ?? ""}
          onChange={(e) => change("rating", e.target.value)}
        >
          <option value="">Mọi đánh giá</option>
          <option value="4">Từ 4 sao</option>
          <option value="3">Từ 3 sao</option>
        </select>
      </label>
      <label>
        Trạng thái
        <select
          value={q.get("status") ?? ""}
          onChange={(e) => change("status", e.target.value)}
        >
          <option value="">Mọi trạng thái</option>
          <option value="COMPLETED">Đã hoàn thành</option>
          <option value="PUBLISHED">Đang cập nhật</option>
        </select>
      </label>
      <Button variant="ghost" onClick={() => setSearch({})}>
        <X size={15} />
        Xóa bộ lọc
      </Button>
    </div>
  );
  return (
    <div className="page container discover-page">
      <div className="page-intro">
        <span className="eyebrow">THE CINEVERSE COLLECTION</span>
        <h1>{isGenres ? "Mỗi thể loại, một thế giới." : title}</h1>
        <p>{subtitle}</p>
      </div>
      {isGenres ? (
        <div className="genre-directory">
          {genres.data?.map((g, i) => (
            <Link to={`/the-loai/${g.slug}`} className="genre-tile" key={g.id}>
              <img src={`/art/scene-${i * 3 + 1}.svg`} alt="" />
              <span>{String(i + 1).padStart(2, "0")}</span>
              <h2>{g.name}</h2>
              <ArrowUpRight />
            </Link>
          ))}
        </div>
      ) : (
        <>
          <div className="catalog-toolbar">
            <form
              className="catalog-search"
              onSubmit={(e) => {
                e.preventDefault();
                change("q", text);
              }}
            >
              <Search size={19} />
              <input
                aria-label="Tìm kiếm trong thư viện"
                placeholder="Tên phim, diễn viên, đạo diễn…"
                value={text}
                onChange={(e) => setText(e.target.value)}
              />
              <button type="submit">Tìm</button>
            </form>
            <Button
              variant="outline"
              className="mobile-filter"
              onClick={() => setDrawer(true)}
            >
              <SlidersHorizontal size={17} />
              Bộ lọc
            </Button>
            <label className="sort-select">
              <span>Sắp xếp</span>
              <select
                aria-label="Sắp xếp phim"
                value={q.get("sort") ?? "new"}
                onChange={(e) => change("sort", e.target.value)}
              >
                <option value="new">Mới cập nhật</option>
                <option value="popular">Phổ biến</option>
                <option value="rating">Đánh giá cao</option>
                <option value="az">Tên A – Z</option>
              </select>
            </label>
          </div>
          <div className="desktop-filters">{filters}</div>
          <div className="result-count">
            <span>{movies.data?.totalElements ?? "…"} câu chuyện</span>
            <span>BỘ SƯU TẬP DEMO HƯ CẤU</span>
          </div>
          {movies.isLoading ? (
            <Loading cards />
          ) : movies.error ? (
            <ErrorState error={movies.error} retry={() => void movies.refetch()} />
          ) : movies.data?.content.length ? (
            <>
              <div className="movie-grid">
                {movies.data.content.map((m) => (
                  <MovieCard key={m.id} movie={m} />
                ))}
              </div>
              <Pagination
                page={movies.data.page}
                total={movies.data.totalPages}
                onChange={(p) => change("page", String(p))}
              />
            </>
          ) : (
            <Empty
              title="Chưa tìm thấy câu chuyện của bạn"
              message="Thử một từ khóa khác hoặc mở rộng bộ lọc."
            >
              <Button variant="outline" onClick={() => setSearch({})}>
                Xóa bộ lọc
              </Button>
            </Empty>
          )}
          <Modal
            open={drawer}
            onOpenChange={setDrawer}
            title="Bộ lọc phim"
            className="filter-dialog"
          >
            {filters}
            <Button onClick={() => setDrawer(false)}>Xem kết quả</Button>
          </Modal>
        </>
      )}
    </div>
  );
}
