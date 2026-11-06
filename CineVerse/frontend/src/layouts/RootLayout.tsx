import { useEffect, useState } from "react";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import {
  Search,
  Ticket,
  Menu,
  X,
  Bell,
  ChevronDown,
  UserRound,
  LogOut,
  Bookmark,
  History,
  LayoutDashboard,
  ArrowUpRight,
} from "lucide-react";
import * as Dropdown from "@radix-ui/react-dropdown-menu";
import { useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { api, send } from "../lib/api";
import { useAuth } from "../app/AuthProvider";
import { Button, Modal, Loading, ErrorState } from "../components/ui";
import type { Movie, Notification, Paged } from "../types";
import { Logo } from "../components/Logo";
export { Logo } from "../components/Logo";
const nav = [
  ["/", "Trang chủ"],
  ["/kham-pha", "Khám phá"],
  ["/phim-dang-chieu", "Đang chiếu"],
  ["/phim-sap-chieu", "Sắp chiếu"],
  ["/the-loai", "Thể loại"],
  ["/rap-chieu", "Rạp & lịch chiếu"],
];
export default function RootLayout() {
  const { user, logout } = useAuth();
  const [scrolled, setScrolled] = useState(false),
    [menu, setMenu] = useState(false),
    [search, setSearch] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();
  const settings = useQuery({
    queryKey: ["settings"],
    queryFn: () => api<Record<string, string>>("/settings"),
  });
  const notes = useQuery({
    queryKey: ["notifications", user?.id],
    queryFn: () => api<Notification[]>("/notifications"),
    enabled: !!user,
    refetchInterval: 60000,
  });
  useEffect(() => {
    const scroll = () => setScrolled(window.scrollY > 20);
    scroll();
    window.addEventListener("scroll", scroll, { passive: true });
    const keys = (e: KeyboardEvent) => {
      if (
        e.key === "/" &&
        !(e.target instanceof HTMLInputElement) &&
        !(e.target instanceof HTMLTextAreaElement)
      ) {
        e.preventDefault();
        setSearch(true);
      }
    };
    window.addEventListener("keydown", keys);
    return () => {
      window.removeEventListener("scroll", scroll);
      window.removeEventListener("keydown", keys);
    };
  }, []);
  useEffect(() => {
    setMenu(false);
    window.scrollTo(0, 0);
  }, [location.pathname]);
  return (
    <>
      <a href="#main" className="skip-link">
        Đến nội dung chính
      </a>
      <header
        className={`site-header ${scrolled || location.pathname !== "/" ? "solid" : ""}`}
      >
        <div className="header-inner">
          <Logo />
          <nav className="desktop-nav" aria-label="Điều hướng chính">
            {nav.map(([path, title]) => (
              <NavLink key={path} to={path} end={path === "/"}>
                {title}
              </NavLink>
            ))}
          </nav>
          <div className="header-actions">
            <button
              className="icon-btn"
              aria-label="Tìm kiếm phim"
              onClick={() => setSearch(true)}
            >
              <Search size={21} />
            </button>
            <Link className="vip-link" to="/rap-chieu">
              <Ticket size={15} /> <span>Đặt vé ngay</span>
            </Link>
            {user ? (
              <>
                <Dropdown.Root>
                  <Dropdown.Trigger asChild>
                    <button className="icon-btn notification-bell" aria-label="Thông báo">
                      <Bell size={19} />
                      {notes.data?.some((n) => !n.read_at) && <span />}
                    </button>
                  </Dropdown.Trigger>
                  <Dropdown.Portal>
                    <Dropdown.Content
                      className="dropdown notification-dropdown"
                      align="end"
                      sideOffset={12}
                    >
                      <strong>Thông báo của bạn</strong>
                      {notes.data?.slice(0, 4).map((n) => (
                        <Dropdown.Item
                          key={n.id}
                          onSelect={() => {
                            void send(`/notifications/${n.id}/read`, "PATCH").then(() =>
                              notes.refetch(),
                            );
                            navigate(n.link);
                          }}
                        >
                          <span className={!n.read_at ? "unread" : ""}>{n.title}</span>
                        </Dropdown.Item>
                      ))}
                      {!notes.data?.length && <p>Bạn chưa có thông báo.</p>}
                      <Dropdown.Item asChild>
                        <Link to="/thong-bao">
                          Xem tất cả <ArrowUpRight size={14} />
                        </Link>
                      </Dropdown.Item>
                    </Dropdown.Content>
                  </Dropdown.Portal>
                </Dropdown.Root>
                <Dropdown.Root>
                  <Dropdown.Trigger
                    className="avatar-trigger"
                    aria-label="Mở menu tài khoản"
                  >
                    <span className="avatar">
                      {user.avatar ? (
                        <img src={user.avatar} alt="" />
                      ) : (
                        user.name.charAt(0)
                      )}
                    </span>
                    <ChevronDown size={12} />
                  </Dropdown.Trigger>
                  <Dropdown.Portal>
                    <Dropdown.Content className="dropdown" align="end" sideOffset={12}>
                      <div className="dropdown-user">
                        <strong>{user.name}</strong>
                        <small>Thành viên CineVerse</small>
                      </div>
                      {[
                        ["/tai-khoan", "Tài khoản", UserRound],
                        ["/danh-sach-cua-toi", "Danh sách của tôi", Bookmark],
                        ["/ve-cua-toi", "Vé của tôi", Ticket],
                        ["/lich-su-thanh-toan", "Lịch sử thanh toán", History],
                        ...(user.role === "ADMIN"
                          ? [["/admin", "Quản trị", LayoutDashboard]]
                          : []),
                      ].map(([url, title, Icon]) => {
                        const I = Icon as typeof UserRound;
                        return (
                          <Dropdown.Item key={String(url)} asChild>
                            <Link to={String(url)}>
                              <I size={16} />
                              {String(title)}
                            </Link>
                          </Dropdown.Item>
                        );
                      })}
                      <Dropdown.Separator />
                      <Dropdown.Item
                        onSelect={() =>
                          void logout().catch((e: Error) => toast.error(e.message))
                        }
                      >
                        <LogOut size={16} />
                        Đăng xuất
                      </Dropdown.Item>
                    </Dropdown.Content>
                  </Dropdown.Portal>
                </Dropdown.Root>
              </>
            ) : (
              <Link to="/dang-nhap" className="login-link">
                Đăng nhập <ArrowUpRight size={14} />
              </Link>
            )}
            <button
              className="icon-btn mobile-menu-btn"
              aria-label={menu ? "Đóng menu" : "Mở menu"}
              onClick={() => setMenu(!menu)}
            >
              {menu ? <X /> : <Menu />}
            </button>
          </div>
        </div>
        {menu && (
          <nav className="mobile-nav" aria-label="Điều hướng di động">
            {nav.map(([p, t]) => (
              <NavLink key={p} to={p}>
                {t}
              </NavLink>
            ))}
            <Link to="/danh-sach-cua-toi">Danh sách của tôi</Link>
            <Link to="/rap-chieu">Đặt vé ngay</Link>
            {!user && <Link to="/dang-ky">Tạo tài khoản</Link>}
          </nav>
        )}
      </header>
      <main id="main">
        {settings.data?.maintenanceMessage && (
          <div className="maintenance-banner" role="status">
            {settings.data.maintenanceMessage}
          </div>
        )}
        <Outlet />
      </main>
      <footer className="site-footer container">
        <div className="footer-top">
          <div>
            <Logo />
            <p>
              Mỗi câu chuyện, một vũ trụ.
              <br />
              {settings.data?.description ?? "Khám phá điện ảnh theo cách của bạn."}
            </p>
          </div>
          <div className="footer-links">
            <div>
              <h3>Khám phá</h3>
              <Link to="/phim-moi">Phim mới</Link>
              <Link to="/bang-xep-hang">Bảng xếp hạng</Link>
              <Link to="/the-loai">Thể loại</Link>
            </div>
            <div>
              <h3>CineVerse</h3>
              <Link to="/rap-chieu">Rạp & lịch chiếu</Link>
              <Link to="/dieu-khoan">Điều khoản</Link>
              <Link to="/chinh-sach-bao-mat">Quyền riêng tư</Link>
              {settings.data?.contactEmail && (
                <a href={"mailto:" + settings.data.contactEmail}>Liên hệ CineVerse</a>
              )}
            </div>
          </div>
          <div className="footer-signoff">
            YOUR UNIVERSE
            <br />
            <span>OF CINEMA.</span>
          </div>
        </div>
        <div className="footer-bottom">
          <span>
            © 2026 {settings.data?.siteName ?? "CineVerse"}. Đồ án Java full-stack.
          </span>
          <span>Phim & rạp demo hư cấu · Thanh toán thử nghiệm</span>
          <span>
            Được tạo cho những người yêu điện ảnh <span className="red">↗</span>
          </span>
        </div>
      </footer>
      <SearchDialog open={search} setOpen={setSearch} />
    </>
  );
}
function SearchDialog({
  open,
  setOpen,
}: {
  open: boolean;
  setOpen: (b: boolean) => void;
}) {
  const [text, setText] = useState(""),
    [query, setQuery] = useState("");
  const navigate = useNavigate();
  useEffect(() => {
    const timer = setTimeout(() => setQuery(text), 320);
    return () => clearTimeout(timer);
  }, [text]);
  const results = useQuery({
    queryKey: ["search", query],
    queryFn: ({ signal }) =>
      api<Paged<Movie>>("/movies/search?size=5&q=" + encodeURIComponent(query), {
        signal,
      }),
    enabled: open && query.trim().length > 0,
  });
  return (
    <Modal
      open={open}
      onOpenChange={setOpen}
      title="Bạn muốn xem gì hôm nay?"
      description="Tìm theo tên phim, diễn viên, đạo diễn hoặc thể loại."
      className="search-dialog"
    >
      <form
        onSubmit={(e) => {
          e.preventDefault();
          setOpen(false);
          navigate("/tim-kiem?q=" + encodeURIComponent(text));
        }}
      >
        <div className="search-input">
          <Search size={21} />
          <input
            autoFocus
            placeholder="Khám phá một câu chuyện…"
            aria-label="Từ khóa tìm phim"
            value={text}
            onChange={(e) => setText(e.target.value)}
          />
          <kbd>↵</kbd>
        </div>
      </form>
      {!query ? (
        <div className="search-hint">
          <span>THỬ TÌM KIẾM</span>
          {["Chân trời", "Phiêu lưu", "Mùa hè"].map((s) => (
            <button key={s} onClick={() => setText(s)}>
              {s}
              <ArrowUpRight size={14} />
            </button>
          ))}
        </div>
      ) : results.isLoading ? (
        <Loading />
      ) : results.error ? (
        <ErrorState error={results.error} retry={() => void results.refetch()} />
      ) : (
        <div className="search-results">
          {results.data?.content.map((m) => (
            <Link key={m.id} to={`/phim/${m.slug}`} onClick={() => setOpen(false)}>
              <img src={m.poster} alt="" />
              <div>
                <strong>{m.title}</strong>
                <small>
                  {m.originalTitle} · {m.releaseYear} · Phim chiếu rạp
                </small>
              </div>
              <ArrowUpRight size={18} />
            </Link>
          ))}
          {results.data?.content.length === 0 && (
            <p>Không tìm thấy phim phù hợp. Hãy thử từ khóa khác.</p>
          )}
          <Button
            variant="outline"
            onClick={() => {
              setOpen(false);
              navigate("/tim-kiem?q=" + encodeURIComponent(text));
            }}
          >
            Xem tất cả kết quả <ArrowUpRight size={16} />
          </Button>
        </div>
      )}
    </Modal>
  );
}
