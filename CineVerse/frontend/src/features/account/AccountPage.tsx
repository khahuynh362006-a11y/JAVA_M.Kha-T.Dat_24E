import { useState } from "react";
import { Link, NavLink, useLocation, useNavigate } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  UserRound,
  ShieldCheck,
  Crown,
  Bookmark,
  History,
  Receipt,
  Bell,
  ArrowUpRight,
  Upload,
} from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "../../app/AuthProvider";
import { api, date, send } from "../../lib/api";
import { Button, Empty, ErrorState, Loading } from "../../components/ui";
import { MovieCard, useFavorites } from "../../components/MovieCard";
import type { Notification, Booking } from "../../types";
export const accountLinks = [
  ["/tai-khoan", "Tổng quan", UserRound],
  ["/tai-khoan/chinh-sua", "Thông tin cá nhân", UserRound],
  ["/tai-khoan/bao-mat", "Bảo mật", ShieldCheck],

  ["/danh-sach-cua-toi", "Danh sách của tôi", Bookmark],
  ["/ve-cua-toi", "Vé của tôi", History],
  ["/lich-su-thanh-toan", "Lịch sử thanh toán", Receipt],
  ["/thong-bao", "Thông báo", Bell],
] as const;
export default function AccountPage() {
  const { user, reload, logout } = useAuth();
  const path = useLocation().pathname;
  const [name, setName] = useState(user?.name ?? ""),
    [marketing, setMarketing] = useState(user?.marketing ?? false),
    [busy, setBusy] = useState(false),
    [currentPassword, setCurrentPassword] = useState(""),
    [password, setPassword] = useState(""),
    [confirm, setConfirm] = useState("");
  const navigate = useNavigate();
  const favorites = useFavorites();
  const history = useQuery({
    queryKey: ["history", user?.id],
    queryFn: () => api<Booking[]>("/bookings/me"),
  });
  if (!user) return null;
  async function profile(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      await send("/users/me", "PATCH", { name, marketing });
      await reload();
      toast.success("Đã cập nhật hồ sơ");
    } catch (e) {
      toast.error((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function security(e: React.FormEvent) {
    e.preventDefault();
    if (password !== confirm) {
      toast.error("Mật khẩu xác nhận chưa khớp");
      return;
    }
    setBusy(true);
    try {
      await send("/users/me/password", "PATCH", { currentPassword, password });
      toast.success("Đã đổi mật khẩu. Vui lòng đăng nhập lại.");
      await logout();
      navigate("/dang-nhap");
    } catch (e) {
      toast.error((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="page container account-page">
      <div className="page-intro">
        <span className="eyebrow">YOUR OWN LITTLE UNIVERSE</span>
        <h1>Không gian của bạn.</h1>
        <p>Những câu chuyện yêu thích và mọi thứ thuộc về bạn.</p>
      </div>
      <div className="account-layout">
        <aside className="account-sidebar">
          <div className="account-user">
            <span className="avatar large">
              {user.avatar ? <img src={user.avatar} alt="" /> : user.name.charAt(0)}
            </span>
            <strong>{user.name}</strong>
            <small>THÀNH VIÊN CINEVERSE</small>
          </div>
          <nav>
            {accountLinks.map(([url, title, Icon]) => (
              <NavLink end to={url} key={url}>
                <Icon size={17} />
                {title}
              </NavLink>
            ))}
          </nav>
        </aside>
        <section className="account-panel">
          {path === "/tai-khoan/chinh-sua" ? (
            <>
              <h2>Thông tin cá nhân</h2>
              <p className="muted">Một chút về người đứng sau những bộ phim yêu thích.</p>
              <div className="avatar-upload">
                <span className="avatar large">
                  {user.avatar ? <img src={user.avatar} alt="" /> : user.name.charAt(0)}
                </span>
                <label className="btn btn-outline">
                  <Upload size={16} />
                  Đổi ảnh đại diện
                  <input
                    type="file"
                    className="sr-only"
                    accept="image/jpeg,image/png,image/webp"
                    onChange={(e) => {
                      const file = e.target.files?.[0];
                      if (!file) return;
                      const body = new FormData();
                      body.set("file", file);
                      void api("/users/me/avatar", { method: "POST", body })
                        .then(() => reload())
                        .then(() => toast.success("Đã đổi ảnh đại diện"))
                        .catch((e: Error) => toast.error(e.message));
                    }}
                  />
                </label>
                <small>JPG, PNG hoặc WebP · Tối đa 8 MB</small>
              </div>
              <form className="settings-form" onSubmit={(e) => void profile(e)}>
                <label>
                  Họ và tên
                  <input
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    required
                    minLength={2}
                    maxLength={100}
                  />
                </label>
                <label>
                  Email
                  <input value={user.email} readOnly />
                  <small>Địa chỉ email dùng để đăng nhập tài khoản.</small>
                </label>
                <label className="checkbox-label">
                  <input
                    type="checkbox"
                    checked={marketing}
                    onChange={(e) => setMarketing(e.target.checked)}
                  />
                  Nhận email giới thiệu nội dung mới
                </label>
                <Button disabled={busy}>Lưu thay đổi</Button>
              </form>
              <EmailChangeForm />
            </>
          ) : path === "/tai-khoan/bao-mat" ? (
            <>
              <h2>Bảo mật tài khoản</h2>
              <p className="muted">
                Đổi mật khẩu sẽ thu hồi tất cả phiên đăng nhập hiện tại.
              </p>
              <form className="settings-form" onSubmit={(e) => void security(e)}>
                <label>
                  Mật khẩu hiện tại
                  <input
                    type="password"
                    autoComplete="current-password"
                    value={currentPassword}
                    onChange={(e) => setCurrentPassword(e.target.value)}
                    required
                    maxLength={72}
                  />
                </label>
                <label>
                  Mật khẩu mới
                  <input
                    type="password"
                    autoComplete="new-password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                    minLength={10}
                    maxLength={72}
                    pattern="(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{10,72}"
                  />
                  <small>
                    Tối thiểu 10 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt.
                  </small>
                </label>
                <label>
                  Xác nhận mật khẩu
                  <input
                    type="password"
                    autoComplete="new-password"
                    value={confirm}
                    onChange={(e) => setConfirm(e.target.value)}
                    required
                  />
                </label>
                <Button disabled={busy}>Đổi mật khẩu</Button>
              </form>
              <div className="security-session">
                <ShieldCheck size={24} />
                <div>
                  <h3>Đăng xuất khỏi mọi thiết bị</h3>
                  <p>Thu hồi tất cả phiên và đăng nhập lại trên thiết bị tin cậy.</p>
                </div>
                <Button
                  variant="outline"
                  onClick={() =>
                    void send("/users/me/logout-all", "POST")
                      .then(() => logout())
                      .then(() => navigate("/dang-nhap"))
                      .catch((e: Error) => toast.error(e.message))
                  }
                >
                  Thu hồi tất cả
                </Button>
              </div>
            </>
          ) : (
            <>
              <div className="panel-heading">
                <div>
                  <h2>Xin chào, {user.name.split(" ").at(-1)}.</h2>
                  <p className="muted">Rất vui được gặp lại bạn trong vũ trụ điện ảnh.</p>
                </div>
                <ShieldCheck className="green" />
              </div>
              <div className="account-metrics">
                <Link to="/danh-sach-cua-toi">
                  <Bookmark size={20} />
                  <strong>{favorites.data?.length ?? 0}</strong>
                  <span>Phim đã lưu</span>
                </Link>
                <Link to="/ve-cua-toi">
                  <History size={20} />
                  <strong>{history.data?.length ?? 0}</strong>
                  <span>Đơn đặt vé</span>
                </Link>
                <Link to="/ve-cua-toi">
                  <Crown size={20} />
                  <strong>
                    {history.data?.reduce((sum, b) => sum + b.tickets.length, 0) ?? 0}
                  </strong>
                  <span>Vé đã nhận</span>
                </Link>
              </div>
              <div className="profile-details">
                <div>
                  <span>Họ và tên</span>
                  <strong>{user.name}</strong>
                </div>
                <div>
                  <span>Email</span>
                  <strong>{user.email}</strong>
                </div>
                <div>
                  <span>Ngày tham gia</span>
                  <strong>{date(user.createdAt)}</strong>
                </div>
                <div>
                  <span>Trạng thái</span>
                  <strong>Đang hoạt động</strong>
                </div>
              </div>
              <Button variant="outline" asChild>
                <Link to="/tai-khoan/chinh-sua">
                  Chỉnh sửa hồ sơ <ArrowUpRight size={16} />
                </Link>
              </Button>
            </>
          )}
        </section>
      </div>
    </div>
  );
}
function EmailChangeForm() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  return (
    <section className="email-change-section">
      <h3>Đổi địa chỉ email</h3>
      <p className="muted">
        Xác nhận liên kết tại email mới để hoàn tất. Tất cả phiên cũ sẽ được thu hồi.
      </p>
      <form
        className="settings-form"
        onSubmit={(e) => {
          e.preventDefault();
          setBusy(true);
          void send<{ message: string }>("/users/me/email-change", "POST", {
            email,
            password,
          })
            .then((r) => {
              setMessage(r.message);
              setPassword("");
            })
            .catch((e: Error) => toast.error(e.message))
            .finally(() => setBusy(false));
        }}
      >
        <label>
          Email mới
          <input
            type="email"
            required
            maxLength={254}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
          />
        </label>
        <label>
          Xác nhận mật khẩu hiện tại
          <input
            type="password"
            required
            maxLength={72}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
          />
        </label>
        {message && (
          <p className="notice" role="status">
            {message}
          </p>
        )}
        <Button variant="outline" disabled={busy}>
          Gửi xác nhận email mới
        </Button>
      </form>
    </section>
  );
}
export function LibraryPage() {
  const favorites = useFavorites();
  return (
    <div className="page container">
      <div className="page-intro">
        <span className="eyebrow">SAVED FOR YOUR NEXT MOVIE NIGHT</span>
        <h1>Danh sách của tôi.</h1>
        <p>Lưu những bộ phim bạn muốn thưởng thức tại rạp.</p>
      </div>
      {favorites.isLoading ? (
        <Loading cards />
      ) : favorites.error ? (
        <ErrorState error={favorites.error} retry={() => void favorites.refetch()} />
      ) : favorites.data?.length ? (
        <div className="movie-grid">
          {favorites.data.map((m) => (
            <MovieCard key={m.id} movie={m} />
          ))}
        </div>
      ) : (
        <Empty
          title="Dành chỗ cho những câu chuyện hay"
          message="Bấm dấu cộng trên một bộ phim để thêm vào danh sách."
        >
          <Button asChild>
            <Link to="/kham-pha">Khám phá phim</Link>
          </Button>
        </Empty>
      )}
    </div>
  );
}
export function NotificationsPage() {
  const cache = useQueryClient();
  const { user } = useAuth();
  const notes = useQuery({
    queryKey: ["notifications", user?.id],
    queryFn: () => api<Notification[]>("/notifications"),
  });
  return (
    <div className="page container narrow">
      <div className="page-intro heading-with-action">
        <div>
          <span className="eyebrow">A NOTE FROM YOUR UNIVERSE</span>
          <h1>Thông báo.</h1>
          <p>Những cập nhật dành riêng cho bạn.</p>
        </div>
        {!!notes.data?.some((n) => !n.read_at) && (
          <Button
            variant="outline"
            onClick={() =>
              void send("/notifications/read-all", "POST")
                .then(() => cache.invalidateQueries({ queryKey: ["notifications"] }))
                .catch((e: Error) => toast.error(e.message))
            }
          >
            Đánh dấu đã đọc
          </Button>
        )}
      </div>
      {notes.isLoading ? (
        <Loading />
      ) : notes.error ? (
        <ErrorState error={notes.error} retry={() => void notes.refetch()} />
      ) : notes.data?.length ? (
        <div className="notification-list">
          {notes.data.map((n) => (
            <Link
              className={!n.read_at ? "unread" : ""}
              to={n.link}
              key={n.id}
              onClick={() =>
                void send(`/notifications/${n.id}/read`, "PATCH")
                  .then(() => cache.invalidateQueries({ queryKey: ["notifications"] }))
                  .catch((e: Error) => toast.error(e.message))
              }
            >
              <span className="notification-icon">
                {n.type === "VIP" ? <Crown /> : <Bell />}
              </span>
              <div>
                <h3>{n.title}</h3>
                <p>{n.content}</p>
                <small>{date(n.created_at)}</small>
              </div>
              <ArrowUpRight size={18} />
            </Link>
          ))}
        </div>
      ) : (
        <Empty title="Mọi thứ đã yên ổn" message="Thông báo mới sẽ xuất hiện tại đây." />
      )}
    </div>
  );
}
