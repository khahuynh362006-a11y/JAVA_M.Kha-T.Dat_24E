import { useEffect, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Plus,
  Search,
  Pencil,
  Trash2,
  ArrowUpRight,
  Download,
  RotateCcw,
  Eye,
  Check,
} from "lucide-react";
import { toast } from "sonner";
import { api, date, download, money, send } from "../../lib/api";
import {
  Button,
  Empty,
  ErrorState,
  Loading,
  Modal,
  Pagination,
} from "../../components/ui";
import { adminLinks } from "./AdminLayout";
import MovieEditor from "./MovieEditor";
import type { Movie, Paged, Row } from "../../types";
interface Field {
  key: string;
  label: string;
  type?:
    | "text"
    | "number"
    | "checkbox"
    | "textarea"
    | "select"
    | "movie"
    | "season"
    | "cinema"
    | "room"
    | "datetime-local";
  required?: boolean;
  options?: [string, string][];
  min?: number;
  max?: number;
  default?: string | number | boolean;
}
const fields: Record<string, Field[]> = {
  cinemas: [
    { key: "name", label: "Tên rạp", required: true },
    { key: "city", label: "Thành phố", required: true },
    { key: "address", label: "Địa chỉ", required: true },
    { key: "active", label: "Hoạt động", type: "checkbox", default: true },
  ],
  auditoriums: [
    { key: "cinemaId", label: "Rạp chiếu", type: "cinema", required: true },
    { key: "name", label: "Tên phòng", required: true },
    {
      key: "format",
      label: "Định dạng",
      type: "select",
      options: [
        ["2D", "2D"],
        ["DOLBY 2D", "Dolby 2D"],
        ["3D", "3D"],
        ["IMAX", "IMAX"],
      ],
      default: "2D",
    },
    {
      key: "rows",
      label: "Số hàng ghế (chỉ dùng khi tạo mới)",
      type: "number",
      min: 3,
      max: 12,
      default: 8,
    },
    {
      key: "columns",
      label: "Số ghế mỗi hàng (chỉ khi tạo mới)",
      type: "number",
      min: 4,
      max: 16,
      default: 10,
    },
    { key: "active", label: "Hoạt động", type: "checkbox", default: true },
  ],
  seats: [
    {
      key: "kind",
      label: "Loại ghế",
      type: "select",
      options: [
        ["STANDARD", "Ghế thường"],
        ["VIP", "Ghế VIP (+20.000đ)"],
      ],
    },
    { key: "active", label: "Có thể bán", type: "checkbox", default: true },
  ],
  showtimes: [
    { key: "movieId", label: "Phim", type: "movie", required: true },
    { key: "auditoriumId", label: "Phòng chiếu", type: "room", required: true },
    {
      key: "startsAt",
      label: "Bắt đầu (giờ Việt Nam)",
      type: "datetime-local",
      required: true,
    },
    {
      key: "basePrice",
      label: "Giá ghế thường (VND)",
      type: "number",
      min: 1000,
      max: 2000000,
      default: 75000,
    },
    { key: "active", label: "Mở bán", type: "checkbox", default: true },
  ],
  genres: [
    { key: "name", label: "Tên thể loại", required: true },
    { key: "slug", label: "Slug URL", required: true },
    { key: "active", label: "Đang hoạt động", type: "checkbox", default: true },
  ],
  countries: [
    { key: "name", label: "Tên quốc gia", required: true },
    { key: "slug", label: "Slug URL", required: true },
    { key: "active", label: "Đang hoạt động", type: "checkbox", default: true },
  ],
  seasons: [
    { key: "movieId", label: "Phim bộ", type: "movie", required: true },
    {
      key: "number",
      label: "Số mùa",
      type: "number",
      min: 1,
      max: 100,
      default: 1,
    },
    { key: "title", label: "Tên mùa", required: true },
  ],
  episodes: [
    { key: "seasonId", label: "Mùa phim", type: "season", required: true },
    {
      key: "number",
      label: "Số tập",
      type: "number",
      min: 1,
      max: 10000,
      default: 1,
    },
    { key: "title", label: "Tên tập", required: true },
    { key: "description", label: "Mô tả", type: "textarea", required: true },
    {
      key: "duration",
      label: "Thời lượng (phút)",
      type: "number",
      min: 1,
      max: 1000,
      default: 25,
    },
    {
      key: "videoFile",
      label: "Tên tệp MP4 (tải lên trong trang phim)",
      default: "sintel.mp4",
    },
    { key: "subtitleFile", label: "Tên tệp WebVTT", default: "demo-vi.vtt" },
    { key: "published", label: "Xuất bản", type: "checkbox", default: true },
    { key: "vip", label: "Yêu cầu VIP", type: "checkbox" },
  ],
  "subscription-plans": [
    { key: "name", label: "Tên gói", required: true },
    {
      key: "price",
      label: "Giá VND",
      type: "number",
      min: 1000,
      max: 50000000,
      default: 49000,
    },
    {
      key: "durationDays",
      label: "Số ngày",
      type: "number",
      min: 1,
      max: 3650,
      default: 30,
    },
    {
      key: "description",
      label: "Quyền lợi",
      type: "textarea",
      required: true,
    },
    { key: "active", label: "Đang bán", type: "checkbox", default: true },
    { key: "recommended", label: "Gói đề xuất", type: "checkbox" },
    { key: "sortOrder", label: "Thứ tự", type: "number", min: 0, default: 0 },
  ],
  banners: [
    { key: "movieId", label: "Phim hiển thị", type: "movie", required: true },
    {
      key: "eyebrow",
      label: "Nhãn giới thiệu",
      required: true,
      default: "CINEVERSE SPOTLIGHT",
    },
    { key: "sortOrder", label: "Thứ tự", type: "number", min: 0, default: 0 },
    { key: "active", label: "Hiển thị", type: "checkbox", default: true },
  ],
  users: [
    {
      key: "role",
      label: "Vai trò",
      type: "select",
      options: [
        ["USER", "Thành viên"],
        ["ADMIN", "Quản trị viên"],
      ],
    },
    { key: "locked", label: "Khóa tài khoản", type: "checkbox" },
    {
      key: "reason",
      label: "Lý do thay đổi (ghi audit)",
      type: "textarea",
      required: true,
    },
  ],
  comments: [
    { key: "hidden", label: "Ẩn đánh giá", type: "checkbox" },
    {
      key: "reason",
      label: "Lý do kiểm duyệt (ghi audit)",
      type: "textarea",
      required: true,
    },
  ],
  notifications: [
    {
      key: "userId",
      label: "ID người nhận (để trống để gửi tất cả)",
      type: "number",
      min: 1,
    },
    { key: "title", label: "Tiêu đề", required: true },
    {
      key: "content",
      label: "Nội dung thông báo",
      type: "textarea",
      required: true,
    },
  ],
};
const columns: Record<string, [string, string][]> = {
  cinemas: [
    ["name", "Rạp"],
    ["city", "Thành phố"],
    ["address", "Địa chỉ"],
    ["active", "Hoạt động"],
  ],
  auditoriums: [
    ["cinema_name", "Rạp"],
    ["name", "Phòng"],
    ["format", "Định dạng"],
    ["capacity", "Số ghế"],
    ["active", "Hoạt động"],
  ],
  seats: [
    ["cinema_name", "Rạp"],
    ["auditorium_name", "Phòng"],
    ["row_label", "Hàng"],
    ["seat_number", "Ghế"],
    ["kind", "Loại"],
    ["active", "Mở bán"],
  ],
  showtimes: [
    ["movie_title", "Phim"],
    ["cinema_name", "Rạp"],
    ["auditorium_name", "Phòng"],
    ["starts_at", "Bắt đầu"],
    ["ends_at", "Kết thúc"],
    ["base_price", "Giá ghế thường"],
    ["active", "Mở bán"],
  ],
  bookings: [
    ["id", "Mã đơn"],
    ["email", "Khách hàng"],
    ["movie_title", "Phim"],
    ["seat_count", "Số ghế"],
    ["total_amount", "Tổng tiền"],
    ["status", "Trạng thái"],
    ["hold_until", "Giữ đến"],
  ],
  tickets: [
    ["id", "Mã vé"],
    ["email", "Khách hàng"],
    ["seat_label", "Ghế"],
    ["status", "Trạng thái"],
    ["issued_at", "Phát hành"],
    ["used_at", "Đã soát"],
  ],
  users: [
    ["name", "Thành viên"],
    ["email", "Email"],
    ["role", "Vai trò"],
    ["verified", "Xác thực"],
    ["locked", "Khóa"],
    ["vip_until", "VIP đến"],
    ["created_at", "Tham gia"],
  ],
  genres: [
    ["name", "Thể loại"],
    ["slug", "Slug"],
    ["movie_count", "Số phim"],
    ["active", "Hoạt động"],
  ],
  countries: [
    ["name", "Quốc gia"],
    ["slug", "Slug"],
    ["movie_count", "Số phim"],
    ["active", "Hoạt động"],
  ],
  seasons: [
    ["movie_title", "Phim bộ"],
    ["number", "Mùa"],
    ["title", "Tên mùa"],
  ],
  episodes: [
    ["movie_title", "Phim bộ"],
    ["season_number", "Mùa"],
    ["number", "Tập"],
    ["title", "Tên tập"],
    ["duration", "Phút"],
    ["published", "Xuất bản"],
    ["vip", "VIP"],
  ],
  "subscription-plans": [
    ["name", "Gói VIP"],
    ["price", "Giá VND"],
    ["duration_days", "Số ngày"],
    ["active", "Đang bán"],
    ["recommended", "Đề xuất"],
  ],
  subscriptions: [
    ["email", "Thành viên"],
    ["order_id", "Đơn hàng"],
    ["start_at", "Bắt đầu"],
    ["end_at", "Kết thúc"],
  ],
  payments: [
    ["id", "Mã đơn"],
    ["email", "Thành viên"],
    ["plan_name", "Gói"],
    ["amount", "Giá trị"],
    ["provider", "Nguồn"],
    ["status", "Trạng thái"],
    ["created_at", "Thời gian"],
  ],
  comments: [
    ["name", "Thành viên"],
    ["movie_title", "Phim"],
    ["stars", "Sao"],
    ["content", "Nội dung"],
    ["reports", "Báo cáo"],
    ["hidden", "Ẩn"],
  ],
  banners: [
    ["title", "Phim"],
    ["eyebrow", "Nhãn"],
    ["sort_order", "Thứ tự"],
    ["active", "Hiển thị"],
  ],
  notifications: [
    ["email", "Người nhận"],
    ["title", "Tiêu đề"],
    ["content", "Nội dung"],
    ["read_at", "Đã đọc"],
    ["created_at", "Thời gian"],
  ],
  "email-logs": [
    ["recipient", "Người nhận"],
    ["subject", "Tiêu đề"],
    ["template", "Mẫu"],
    ["status", "Trạng thái"],
    ["attempts", "Số lần thử"],
    ["sent_at", "Đã gửi"],
    ["last_error", "Lỗi gần nhất"],
  ],
  "audit-logs": [
    ["actor_email", "Người thực hiện"],
    ["action", "Thao tác"],
    ["entity_type", "Đối tượng"],
    ["entity_id", "ID"],
    ["detail", "Nội dung"],
    ["created_at", "Thời gian"],
  ],
};
const camelToSnake = (s: string) => s.replace(/[A-Z]/g, (c) => "_" + c.toLowerCase());
export default function ResourcePage() {
  const location = useLocation();
  const resource = location.pathname.split("/")[2] ?? "movies";
  const isMovie = resource === "movies" || resource === "series";
  const title = adminLinks.find(([key]) => key === resource)?.[1] ?? "Dữ liệu";
  const [page, setPage] = useState(0),
    [search, setSearch] = useState(""),
    [q, setQ] = useState(""),
    [status, setStatus] = useState(""),
    [editor, setEditor] = useState<Row | "new" | null>(null),
    [deleting, setDeleting] = useState<Row | null>(null),
    [viewing, setViewing] = useState<Row | null>(null);
  const cache = useQueryClient();
  useEffect(() => {
    const params = new URLSearchParams(location.search);
    setPage(Number(params.get("page") ?? 0));
    setSearch(params.get("q") ?? "");
    setQ(params.get("q") ?? "");
    setEditor(null);
    setStatus(params.get("status") ?? "");
  }, [resource, location.search]);
  const query = new URLSearchParams({ page: String(page), size: "15", q });
  if (resource === "series") query.set("type", "SERIES");
  if (status) query.set("status", status);
  const data = useQuery({
    queryKey: ["admin", resource, query.toString()],
    queryFn: () => api<Paged<Row>>(`/admin/${resource}?${query}`),
  });
  const canCreate =
    isMovie || (!!fields[resource] && !["users", "comments", "seats"].includes(resource));
  const editable = isMovie || (!!fields[resource] && resource !== "notifications");
  const canDelete =
    isMovie ||
    [
      "genres",
      "countries",
      "seasons",
      "episodes",
      "subscription-plans",
      "banners",
      "cinemas",
      "auditoriums",
      "showtimes",
    ].includes(resource);
  const refresh = () => {
    void cache.invalidateQueries({ queryKey: ["admin"] });
    void cache.invalidateQueries({ queryKey: ["home"] });
    void cache.invalidateQueries({ queryKey: ["movies"] });
    void cache.invalidateQueries({ queryKey: ["detail"] });
    void cache.invalidateQueries({ queryKey: ["genres"] });
    void cache.invalidateQueries({ queryKey: ["countries"] });
    void cache.invalidateQueries({ queryKey: ["featured"] });
    void cache.invalidateQueries({ queryKey: ["plans"] });
  };
  async function remove() {
    if (!deleting) return;
    try {
      await send(`/admin/${resource}/${deleting.id}`, "DELETE");
      toast.success("Đã xóa vĩnh viễn dữ liệu");
      setDeleting(null);
      refresh();
    } catch (e) {
      toast.error((e as Error).message);
    }
  }
  function cell(row: Row, key: string) {
    const val = row[key];
    if (val === null || val === undefined) return <span className="muted">—</span>;
    if (["price", "amount", "base_price", "total_amount"].includes(key))
      return money(Number(val));
    if (key.endsWith("_at") || key === "vip_until" || key === "hold_until")
      return <span className="nowrap">{date(String(val))}</span>;
    if (typeof val === "boolean")
      return (
        <span className={`boolean ${val ? "yes" : ""}`}>
          {val ? <Check size={14} /> : "—"}
        </span>
      );
    if (key === "provider")
      return (
        <span className="simulation-tag">
          {val === "SIMULATION"
            ? "MÔ PHỎNG"
            : val === "PAYOS"
              ? "payOS · Online"
              : val === "VNPAY_SANDBOX"
                ? "VNPAY Sandbox"
                : "MoMo Sandbox"}
        </span>
      );
    if (key === "status")
      return (
        <span className={"status status-" + String(val).toLowerCase()}>
          {String(val)}
        </span>
      );
    return (
      <span
        title={String(val)}
        className={
          ["content", "detail", "last_error"].includes(key) ? "table-truncate" : ""
        }
      >
        {key === "id" && String(val).length > 20
          ? String(val).slice(0, 15) + "…"
          : String(val)}
      </span>
    );
  }
  return (
    <>
      <div className="admin-page-heading">
        <div>
          <span className="eyebrow">CINEVERSE CONTENT STUDIO</span>
          <h1>
            {title}
            <span className="section-dot">.</span>
          </h1>
          <p>
            {resource === "payments"
              ? "Đơn hàng chỉ chuyển sang PAID qua xác nhận gateway hợp lệ."
              : resource === "email-logs"
                ? "Outbox giao dịch, trạng thái SMTP và lịch sử thử lại."
                : resource === "subscriptions"
                  ? "Mỗi đơn thanh toán chỉ kích hoạt một lần, có lịch sử bắt đầu và kết thúc."
                  : "Quản lý dữ liệu thật, thay đổi được lưu qua backend Java."}
          </p>
        </div>
        <div>
          {resource === "payments" && (
            <Button
              variant="outline"
              onClick={() =>
                void download("/admin/payments/export", "cineverse-payments.csv").catch(
                  (e: Error) => toast.error(e.message),
                )
              }
            >
              <Download size={16} />
              Xuất CSV
            </Button>
          )}
          {canCreate && (
            <Button onClick={() => setEditor("new")}>
              <Plus size={17} />
              {resource === "notifications" ? "Gửi thông báo" : "Thêm mới"}
            </Button>
          )}
        </div>
      </div>
      <div className="admin-box admin-data-box">
        <div className="admin-table-toolbar">
          <form
            className="table-search"
            onSubmit={(e) => {
              e.preventDefault();
              setQ(search);
              setPage(0);
            }}
          >
            <Search size={17} />
            <input
              placeholder="Tìm kiếm dữ liệu…"
              aria-label="Tìm kiếm dữ liệu quản trị"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            <button type="submit">Tìm</button>
          </form>
          <span>{data.data?.totalElements ?? 0} bản ghi</span>
          {resource === "payments" && (
            <select
              aria-label="Lọc trạng thái đơn"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value);
                setPage(0);
              }}
            >
              <option value="">Mọi trạng thái</option>
              {["PENDING", "PAID", "FAILED", "CANCELLED", "EXPIRED"].map((s) => (
                <option key={s}>{s}</option>
              ))}
            </select>
          )}
          <Button variant="ghost" onClick={() => void data.refetch()}>
            <RotateCcw size={15} />
            Làm mới
          </Button>
        </div>
        {data.isLoading ? (
          <Loading />
        ) : data.error ? (
          <ErrorState error={data.error} retry={() => void data.refetch()} />
        ) : data.data?.content.length ? (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  {isMovie ? (
                    <>
                      <th>Phim / Tên gốc</th>
                      <th>Loại</th>
                      <th>Năm</th>
                      <th>Trạng thái</th>
                      <th>Ưu tiên</th>
                    </>
                  ) : (
                    columns[resource]?.map(([key, label]) => <th key={key}>{label}</th>)
                  )}
                  <th className="align-right">Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {data.data.content.map((row) => (
                  <tr key={String(row.id)}>
                    {isMovie ? (
                      <>
                        <td>
                          <div className="admin-movie-cell">
                            <img src={String(row.poster)} alt="" />
                            <div>
                              <strong>{String(row.title)}</strong>
                              <small>{String(row.originalTitle)}</small>
                            </div>
                          </div>
                        </td>
                        <td>Chiếu rạp</td>
                        <td>{String(row.releaseYear)}</td>
                        <td>
                          <span
                            className={
                              "status status-" + String(row.status).toLowerCase()
                            }
                          >
                            {String(row.status)}
                          </span>
                        </td>
                        <td>{Number(row.priority)}</td>
                      </>
                    ) : (
                      columns[resource]?.map(([key]) => (
                        <td key={key}>{cell(row, key)}</td>
                      ))
                    )}
                    <td>
                      <div className="table-actions">
                        {isMovie && (
                          <Link
                            className="icon-btn small"
                            to={"/phim/" + row.slug}
                            aria-label={"Xem phim " + row.title}
                          >
                            <ArrowUpRight size={16} />
                          </Link>
                        )}
                        {editable && (
                          <button
                            className="icon-btn small"
                            aria-label={"Chỉnh sửa " + row.id}
                            onClick={() => setEditor(row)}
                          >
                            <Pencil size={15} />
                          </button>
                        )}
                        {canDelete && (
                          <button
                            className="icon-btn small"
                            aria-label={"Xóa vĩnh viễn " + row.id}
                            onClick={() => setDeleting(row)}
                          >
                            <Trash2 size={15} />
                          </button>
                        )}
                        {!isMovie && !editable && (
                          <button
                            className="icon-btn small"
                            aria-label={"Chi tiết " + row.id}
                            onClick={() => setViewing(row)}
                          >
                            <Eye size={16} />
                          </button>
                        )}
                        {resource === "email-logs" && row.status === "FAILED" && (
                          <button
                            className="icon-btn small"
                            aria-label="Gửi lại email"
                            onClick={() =>
                              void send(`/admin/email-logs/${row.id}/retry`, "POST")
                                .then(() => {
                                  toast.success("Đã đưa email vào hàng đợi");
                                  refresh();
                                })
                                .catch((e: Error) => toast.error(e.message))
                            }
                          >
                            <RotateCcw size={15} />
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <Empty
            title="Chưa có dữ liệu phù hợp"
            message="Thử từ khóa khác hoặc thêm dữ liệu mới."
          />
        )}
        <Pagination page={page} total={data.data?.totalPages ?? 0} onChange={setPage} />
      </div>
      <Modal
        open={editor !== null}
        onOpenChange={(open) => {
          if (!open) setEditor(null);
        }}
        title={(editor === "new" ? "Tạo mới · " : "Chỉnh sửa · ") + title}
        className={isMovie ? "admin-editor-dialog" : "admin-resource-dialog"}
      >
        {editor !== null &&
          (isMovie ? (
            <MovieEditor
              id={editor === "new" ? null : Number(editor.id)}
              series={resource === "series"}
              onSaved={() => {
                setEditor(null);
                refresh();
              }}
              onCancel={() => setEditor(null)}
            />
          ) : (
            <ResourceForm
              resource={resource}
              row={editor === "new" ? null : editor}
              onSaved={() => {
                setEditor(null);
                refresh();
              }}
            />
          ))}
      </Modal>
      <Modal
        open={!!deleting}
        onOpenChange={(v) => {
          if (!v) setDeleting(null);
        }}
        title="Xác nhận xóa vĩnh viễn"
        description="Bản ghi và dữ liệu phụ thuộc sẽ bị xóa khỏi web và CSDL, không thể khôi phục từ giao diện. Dữ liệu có lịch sử đặt vé, thanh toán hoặc liên kết bắt buộc sẽ bị chặn và có thông báo cụ thể."
      >
        <p>{String(deleting?.title ?? deleting?.name ?? deleting?.id ?? "")}</p>
        <div className="form-actions">
          <Button variant="outline" onClick={() => setDeleting(null)}>
            Giữ lại
          </Button>
          <Button onClick={() => void remove()}>Xác nhận</Button>
        </div>
      </Modal>
      <Modal
        open={!!viewing}
        onOpenChange={(v) => {
          if (!v) setViewing(null);
        }}
        title="Chi tiết bản ghi"
      >
        {viewing && <RecordDetails resource={resource} row={viewing} />}
      </Modal>
    </>
  );
}
function ResourceForm({
  resource,
  row,
  onSaved,
}: {
  resource: string;
  row: Row | null;
  onSaved: () => void;
}) {
  const defs = fields[resource] ?? [];
  const [values, setValues] = useState<Record<string, string | number | boolean | null>>(
    () =>
      Object.fromEntries(
        defs.map((f) => [
          f.key,
          row?.[f.key] ??
            row?.[camelToSnake(f.key)] ??
            f.default ??
            (f.type === "checkbox" ? false : ""),
        ]),
      ),
  );
  const [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const movies = useQuery({
    queryKey: ["admin-options-movies", resource],
    queryFn: () =>
      api<Paged<Movie>>(
        "/admin/movies?size=48" + (resource === "seasons" ? "&type=SERIES" : ""),
      ),
    enabled: defs.some((f) => f.type === "movie"),
  });
  const seasons = useQuery({
    queryKey: ["admin-options-seasons"],
    queryFn: () => api<Paged<Row>>("/admin/seasons?size=100"),
    enabled: defs.some((f) => f.type === "season"),
  });
  const cinemaOptions = useQuery({
    queryKey: ["admin-cinema-options"],
    queryFn: () => api<Paged<Row>>("/admin/cinemas?size=100"),
    enabled: defs.some((f) => f.type === "cinema"),
  });
  const roomOptions = useQuery({
    queryKey: ["admin-room-options"],
    queryFn: () => api<Paged<Row>>("/admin/auditoriums?size=100"),
    enabled: defs.some((f) => f.type === "room"),
  });
  useEffect(() => {
    if (row?.starts_at)
      setValues((v) => ({
        ...v,
        startsAt: new Intl.DateTimeFormat("sv-SE", {
          timeZone: "Asia/Ho_Chi_Minh",
          year: "numeric",
          month: "2-digit",
          day: "2-digit",
          hour: "2-digit",
          minute: "2-digit",
        })
          .format(new Date(String(row.starts_at)))
          .replace(" ", "T"),
      }));
  }, [row]);
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const body: Record<string, unknown> = { ...values };
      for (const f of defs)
        if (["number", "movie", "season", "cinema", "room"].includes(f.type ?? ""))
          body[f.key] = values[f.key] === "" ? null : Number(values[f.key]);
      if (values.startsAt)
        body.startsAt = new Date(String(values.startsAt) + ":00+07:00").toISOString();
      await send(
        `/admin/${resource}${row ? "/" + row.id : ""}`,
        row ? (["users", "comments"].includes(resource) ? "PATCH" : "PUT") : "POST",
        body,
      );
      toast.success(
        resource === "notifications"
          ? "Đã gửi thông báo trong ứng dụng"
          : "Đã lưu dữ liệu",
      );
      onSaved();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <form className="admin-form" onSubmit={(e) => void submit(e)}>
      {defs.map((f) => (
        <label key={f.key} className={f.type === "checkbox" ? "checkbox-label" : ""}>
          {f.type !== "checkbox" && f.label}
          {f.type === "checkbox" ? (
            <>
              <input
                type="checkbox"
                checked={Boolean(values[f.key])}
                onChange={(e) => setValues((v) => ({ ...v, [f.key]: e.target.checked }))}
              />
              {f.label}
            </>
          ) : f.type === "textarea" ? (
            <textarea
              required={f.required}
              maxLength={f.key === "content" ? 1500 : f.key === "description" ? 600 : 500}
              value={String(values[f.key] ?? "")}
              onChange={(e) => setValues((v) => ({ ...v, [f.key]: e.target.value }))}
            />
          ) : f.type === "select" ||
            f.type === "movie" ||
            f.type === "season" ||
            f.type === "cinema" ||
            f.type === "room" ? (
            <select
              aria-label={f.label}
              value={String(values[f.key] ?? "")}
              required={f.required}
              onChange={(e) => setValues((v) => ({ ...v, [f.key]: e.target.value }))}
            >
              <option value="">Chọn {f.label.toLowerCase()}</option>
              {f.options?.map(([value, title]) => (
                <option key={value} value={value}>
                  {title}
                </option>
              ))}
              {f.type === "movie" &&
                movies.data?.content.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.title} (#{m.id})
                  </option>
                ))}
              {f.type === "season" &&
                seasons.data?.content.map((s) => (
                  <option key={String(s.id)} value={String(s.id)}>
                    {String(s.movie_title)} · {String(s.title)}
                  </option>
                ))}
              {f.type === "cinema" &&
                cinemaOptions.data?.content.map((c) => (
                  <option key={String(c.id)} value={String(c.id)}>
                    {String(c.name)}
                  </option>
                ))}
              {f.type === "room" &&
                roomOptions.data?.content.map((r) => (
                  <option key={String(r.id)} value={String(r.id)}>
                    {String(r.cinema_name)} · {String(r.name)} ({String(r.format)})
                  </option>
                ))}
            </select>
          ) : (
            <input
              type={f.type ?? "text"}
              required={f.required || (f.type === "number" && f.key !== "userId")}
              min={f.min}
              max={f.max}
              maxLength={f.key === "slug" ? 100 : 200}
              pattern={f.key === "slug" ? "[a-z0-9]+(?:-[a-z0-9]+)*" : undefined}
              value={String(values[f.key] ?? "")}
              onChange={(e) => setValues((v) => ({ ...v, [f.key]: e.target.value }))}
            />
          )}
        </label>
      ))}
      {error && (
        <p role="alert" className="form-error">
          {error}
        </p>
      )}
      <Button disabled={busy}>
        <Check size={16} />
        {busy
          ? "Đang lưu…"
          : resource === "notifications"
            ? "Gửi thông báo"
            : "Lưu thay đổi"}
      </Button>
    </form>
  );
}
function RecordDetails({ resource, row }: { resource: string; row: Row }) {
  const detail = useQuery({
    queryKey: ["admin-payment-detail", row.id],
    queryFn: () => api<{ order: Row; events: Row[] }>("/admin/payments/" + row.id),
    enabled: resource === "payments",
  });
  return (
    <div>
      <dl className="record-details">
        {Object.entries(row).map(([k, v]) => (
          <div key={k}>
            <dt>{k}</dt>
            <dd>{String(v ?? "—")}</dd>
          </div>
        ))}
      </dl>
      {detail.data && (
        <>
          <h3>Lịch sử xử lý giao dịch</h3>
          <div className="event-list">
            {detail.data.events.map((e, i) => (
              <div key={i}>
                <strong>{String(e.kind)}</strong>
                <p>{String(e.detail)}</p>
                <small>{date(String(e.created_at))}</small>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
export function SettingsPage() {
  const cache = useQueryClient();
  const data = useQuery({
    queryKey: ["settings"],
    queryFn: () => api<Record<string, string>>("/settings"),
  });
  const [values, setValues] = useState<Record<string, string>>({}),
    [busy, setBusy] = useState(false);
  useEffect(() => {
    if (data.data) setValues(data.data);
  }, [data.data]);
  return (
    <>
      <div className="admin-page-heading">
        <div>
          <span className="eyebrow">THE DETAILS MATTER</span>
          <h1>Thiết lập website.</h1>
          <p>Chỉ lưu cấu hình công khai. Secret được quản lý qua biến môi trường.</p>
        </div>
      </div>
      <section className="admin-box settings-box">
        {data.isLoading ? (
          <Loading />
        ) : data.error ? (
          <ErrorState error={data.error} />
        ) : (
          <form
            className="settings-form"
            onSubmit={(e) => {
              e.preventDefault();
              setBusy(true);
              void send("/admin/settings", "PATCH", values)
                .then(() => {
                  toast.success("Đã lưu thiết lập");
                  return cache.invalidateQueries({ queryKey: ["settings"] });
                })
                .catch((e: Error) => toast.error(e.message))
                .finally(() => setBusy(false));
            }}
          >
            {[
              ["siteName", "Tên website"],
              ["description", "Giới thiệu"],
              ["contactEmail", "Email liên hệ"],
              ["maintenanceMessage", "Thông báo bảo trì (để trống nếu không có)"],
            ].map(([key, title]) => (
              <label key={key}>
                {title}
                <input
                  value={values[key] ?? ""}
                  type={key === "contactEmail" ? "email" : "text"}
                  maxLength={key === "description" ? 1500 : 200}
                  required={key !== "maintenanceMessage"}
                  onChange={(e) => setValues((v) => ({ ...v, [key]: e.target.value }))}
                />
              </label>
            ))}
            <Button disabled={busy}>Lưu thiết lập</Button>
          </form>
        )}
      </section>
    </>
  );
}
