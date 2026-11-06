import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ImagePlus, Upload, Check } from "lucide-react";
import { toast } from "sonner";
import { api, send } from "../../lib/api";
import { Button, Loading, ErrorState } from "../../components/ui";
import type { Lookup, MovieInput } from "../../types";
const initial: MovieInput = {
  title: "",
  originalTitle: "",
  slug: "",
  shortDescription: "",
  synopsis: "",
  type: "MOVIE",
  countryId: 0,
  releaseYear: 2026,
  duration: 90,
  ageRating: "T13",
  language: "Phụ đề Việt",
  director: "",
  castNames: "",
  tags: "",
  poster: "/art/fallback.svg",
  backdrop: "/art/scene-1.svg",
  status: "DRAFT",
  vip: false,
  featured: false,
  priority: 0,
  genreIds: [],
  videoFile: null,
  trailerFile: null,
  subtitleFile: null,
};
export default function MovieEditor({
  id,
  onSaved,
  onCancel,
  series = false,
}: {
  id: number | null;
  onSaved: () => void;
  onCancel: () => void;
  series?: boolean;
}) {
  const [value, setValue] = useState<MovieInput>({
      ...initial,
      type: series ? "SERIES" : "MOVIE",
    }),
    [tab, setTab] = useState("story"),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const data = useQuery({
    queryKey: ["admin-movie", id],
    queryFn: () => api<MovieInput>("/admin/movies/" + id),
    enabled: id !== null,
  });
  const genres = useQuery({
    queryKey: ["genres"],
    queryFn: () => api<Lookup[]>("/genres"),
  });
  const countries = useQuery({
    queryKey: ["countries"],
    queryFn: () => api<Lookup[]>("/countries"),
  });
  useEffect(() => {
    if (data.data) setValue(data.data);
  }, [data.data]);
  useEffect(() => {
    if (!id && countries.data?.length)
      setValue((v) => ({
        ...v,
        countryId: v.countryId || countries.data[0].id,
      }));
  }, [id, countries.data]);
  function set<K extends keyof MovieInput>(key: K, v: MovieInput[K]) {
    setValue((x) => ({ ...x, [key]: v }));
  }
  async function upload(
    file: File,
    key: "poster" | "backdrop" | "videoFile" | "trailerFile" | "subtitleFile",
  ) {
    setBusy(true);
    try {
      const body = new FormData();
      body.set("file", file);
      const result = await api<{ filename: string; url: string }>("/admin/uploads", {
        method: "POST",
        body,
      });
      set(key, key === "poster" || key === "backdrop" ? result.url : result.filename);
      toast.success("Đã tải tệp lên");
    } catch (e) {
      toast.error((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function save(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    const requiredGroups: [string, (keyof MovieInput)[]][] = [
      ["story", ["title", "originalTitle", "slug", "synopsis", "shortDescription"]],
      ["meta", ["director", "castNames", "language", "ageRating"]],
    ];
    for (const [section, keys] of requiredGroups) {
      if (keys.some((key) => !String(value[key]).trim())) {
        setError("Vui lòng điền đầy đủ thông tin bắt buộc trong thẻ này.");
        setTab(section);
        return;
      }
    }
    if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(value.slug)) {
      setError("Slug chỉ gồm chữ thường không dấu, số và dấu gạch nối.");
      setTab("story");
      return;
    }
    if (!value.genreIds.length) {
      setError("Vui lòng chọn ít nhất một thể loại.");
      setTab("meta");
      return;
    }
    setBusy(true);
    try {
      await send(
        id ? "/admin/movies/" + id : "/admin/movies",
        id ? "PUT" : "POST",
        value,
      );
      toast.success(id ? "Đã cập nhật phim" : "Đã tạo phim");
      onSaved();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  if (data.isLoading && id) return <Loading />;
  if (data.error) return <ErrorState error={data.error} />;
  const field = (
    key: keyof MovieInput,
    label: string,
    required = true,
    multiline = false,
  ) => {
    const val = value[key];
    return (
      <label>
        {label}
        {multiline ? (
          <textarea
            value={String(val ?? "")}
            required={required}
            maxLength={
              key === "synopsis" ? 12000 : key === "shortDescription" ? 600 : 1000
            }
            onChange={(e) => set(key, e.target.value as never)}
          />
        ) : (
          <input
            value={String(val ?? "")}
            required={required}
            maxLength={key === "slug" ? 220 : 500}
            onChange={(e) => set(key, e.target.value as never)}
          />
        )}
      </label>
    );
  };
  return (
    <form className="admin-form movie-editor" noValidate onSubmit={(e) => void save(e)}>
      <div className="editor-tabs">
        {[
          ["story", "Nội dung"],
          ["meta", "Thông tin phim"],
          ["media", "Hình ảnh & trailer"],
          ["publish", "Xuất bản"],
        ].map(([key, title]) => (
          <button
            type="button"
            key={key}
            className={tab === key ? "active" : ""}
            onClick={() => setTab(key)}
          >
            {title}
          </button>
        ))}
      </div>
      <div className="editor-section" hidden={tab !== "story"}>
        <div className="form-grid">
          {field("title", "Tên phim")}
          {field("originalTitle", "Tên gốc")}
          {field("slug", "Slug URL (chữ thường, dấu gạch nối)")}
          <label>
            Loại nội dung
            <select
              aria-label="Loại nội dung"
              value={value.type}
              onChange={(e) => set("type", e.target.value)}
            >
              <option value="MOVIE">Phim chiếu rạp</option>
            </select>
          </label>
        </div>
        {field("shortDescription", "Mô tả ngắn", true, true)}
        {field("synopsis", "Nội dung chi tiết", true, true)}
      </div>
      <div className="editor-section" hidden={tab !== "meta"}>
        <div className="form-grid">
          <label>
            Quốc gia
            <select
              aria-label="Quốc gia"
              value={value.countryId}
              required
              onChange={(e) => set("countryId", Number(e.target.value))}
            >
              <option value="">Chọn quốc gia</option>
              {countries.data?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Năm phát hành
            <input
              type="number"
              min="1888"
              max="2100"
              value={value.releaseYear}
              onChange={(e) => set("releaseYear", Number(e.target.value))}
            />
          </label>
          <label>
            Thời lượng (phút)
            <input
              type="number"
              min="1"
              max="1000"
              value={value.duration}
              onChange={(e) => set("duration", Number(e.target.value))}
            />
          </label>
          {field("ageRating", "Phân loại độ tuổi")}
          {field("language", "Ngôn ngữ")}
          {field("director", "Đạo diễn")}
          {field("castNames", "Diễn viên")}
          {field("tags", "Từ khóa", false)}
        </div>
        <label>Thể loại</label>
        <div className="genre-checkboxes">
          {genres.data?.map((g) => (
            <label key={g.id} className={value.genreIds.includes(g.id) ? "selected" : ""}>
              <input
                type="checkbox"
                checked={value.genreIds.includes(g.id)}
                onChange={(e) =>
                  set(
                    "genreIds",
                    e.target.checked
                      ? [...value.genreIds, g.id]
                      : value.genreIds.filter((x) => x !== g.id),
                  )
                }
              />
              {g.name}
            </label>
          ))}
        </div>
      </div>
      <div className="editor-section" hidden={tab !== "media"}>
        <p className="notice subtle">
          Chỉ tải media mà bạn có quyền sử dụng. MP4 tối đa 250 MB; ảnh tối đa 8 MB. Không
          nhập URL video bên ngoài.
        </p>
        <div className="editor-images">
          {(["poster", "backdrop"] as const).map((key) => (
            <div key={key}>
              <img
                src={value[key]}
                alt={key}
                onError={(e) => {
                  e.currentTarget.src = "/art/fallback.svg";
                }}
              />
              {field(key, key === "poster" ? "Đường dẫn poster" : "Đường dẫn backdrop")}
              <label className="btn btn-outline">
                <ImagePlus size={16} />
                Tải ảnh
                <input
                  type="file"
                  accept="image/png,image/jpeg,image/webp"
                  className="sr-only"
                  onChange={(e) => {
                    const f = e.target.files?.[0];
                    if (f) void upload(f, key);
                  }}
                />
              </label>
            </div>
          ))}
        </div>
        <div className="media-field">
          {field("trailerFile", "Tệp MP4 trailer", false)}
          <label className="btn btn-outline">
            <Upload size={16} />
            Tải lên
            <input
              className="sr-only"
              type="file"
              accept=".mp4"
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) void upload(f, "trailerFile");
              }}
            />
          </label>
        </div>
      </div>
      <div className="editor-section" hidden={tab !== "publish"}>
        <label>
          Trạng thái
          <select
            aria-label="Trạng thái"
            value={value.status}
            onChange={(e) => set("status", e.target.value)}
          >
            <option value="DRAFT">Bản nháp</option>
            <option value="PUBLISHED">Đã xuất bản</option>
            <option value="COMPLETED">Đã hoàn thành</option>
          </select>
        </label>
        <div className="form-grid">
          <label className="checkbox-label">
            <input
              type="checkbox"
              checked={value.featured}
              onChange={(e) => set("featured", e.target.checked)}
            />
            Đánh dấu phim nổi bật
          </label>
        </div>
        <label>
          Độ ưu tiên biên tập (0–100)
          <input
            type="number"
            min="0"
            max="100"
            value={value.priority}
            onChange={(e) => set("priority", Number(e.target.value))}
          />
        </label>
        <p className="muted">
          Bản nháp không xuất hiện công khai. Tạo suất chiếu và chọn Banner trong mục quản
          trị tương ứng.
        </p>
      </div>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      <div className="editor-footer">
        <Button type="button" variant="outline" onClick={onCancel}>
          Hủy
        </Button>
        <span>Thay đổi được lưu qua API Java</span>
        <Button disabled={busy}>
          <Check size={16} />
          {busy ? "Đang lưu…" : "Lưu phim"}
        </Button>
      </div>
    </form>
  );
}
