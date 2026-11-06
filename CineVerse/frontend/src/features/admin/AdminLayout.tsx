import { useState } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import {
  LayoutDashboard,
  Film,
  Building2,
  Armchair,
  CalendarDays,
  Tags,
  Users,
  Ticket,
  ScanLine,
  Receipt,
  MessageSquare,
  Image,
  BarChart3,
  Mail,
  ScrollText,
  Settings,
  ArrowUpRight,
  Menu,
  X,
  Bell,
  Globe2,
  LogOut,
} from "lucide-react";
import { Logo } from "../../components/Logo";
import { customerUrl } from "../../lib/urls";
import { useAuth } from "../../app/AuthProvider";
import { toast } from "sonner";
export const adminLinks = [
  ["", "Tổng quan", LayoutDashboard],
  ["movies", "Thư viện phim", Film],
  ["cinemas", "Rạp chiếu", Building2],
  ["auditoriums", "Phòng chiếu", Armchair],
  ["seats", "Ghế ngồi", Armchair],
  ["showtimes", "Suất chiếu", CalendarDays],
  ["bookings", "Đơn đặt vé", Ticket],
  ["tickets", "Vé điện tử", Ticket],
  ["check-in", "Soát vé", ScanLine],
  ["genres", "Thể loại", Tags],
  ["countries", "Quốc gia", Globe2],
  ["users", "Thành viên", Users],
  ["payments", "Giao dịch", Receipt],
  ["comments", "Đánh giá", MessageSquare],
  ["banners", "Banner trang chủ", Image],
  ["notifications", "Thông báo", Bell],
  ["analytics", "Phân tích", BarChart3],
  ["email-logs", "Email outbox", Mail],
  ["audit-logs", "Nhật ký hệ thống", ScrollText],
  ["settings", "Thiết lập", Settings],
] as const;
export default function AdminLayout() {
  const { user, logout } = useAuth();
  const location = useLocation(),
    nav = useNavigate();
  const [open, setOpen] = useState(false);
  const current =
    adminLinks.find(
      ([path]) => location.pathname === "/admin" + (path ? "/" + path : ""),
    )?.[1] ?? "Quản lý nội dung";
  return (
    <div className="admin-shell">
      <aside className={`admin-sidebar ${open ? "open" : ""}`}>
        <div className="admin-brand">
          <Logo />
          <span>STUDIO / ADMIN</span>
          <button
            className="icon-btn admin-close"
            onClick={() => setOpen(false)}
            aria-label="Đóng thanh bên"
          >
            <X size={18} />
          </button>
        </div>
        <span className="admin-nav-label">WORKSPACE</span>
        <nav>
          {adminLinks.map(([path, title, Icon], i) => (
            <NavLink
              end
              key={path}
              className={i === 6 || i === 13 ? "nav-divider" : ""}
              to={"/admin" + (path ? "/" + path : "")}
              onClick={() => setOpen(false)}
            >
              <Icon size={17} />
              <span>{title}</span>
            </NavLink>
          ))}
        </nav>
        <a className="admin-back" href={customerUrl}>
          Xem website <ArrowUpRight size={17} />
        </a>
        <div className="admin-sidebar-user">
          <span className="avatar">{user?.name.charAt(0)}</span>
          <div>
            <strong>{user?.name}</strong>
            <small>Quản trị viên</small>
          </div>
          <button
            className="icon-btn"
            aria-label="Đăng xuất admin"
            onClick={() =>
              void logout()
                .then(() => nav("/dang-nhap"))
                .catch((e: Error) => toast.error(e.message))
            }
          >
            <LogOut size={16} />
          </button>
        </div>
      </aside>
      {open && (
        <button
          className="admin-scrim"
          aria-label="Đóng menu"
          onClick={() => setOpen(false)}
        />
      )}
      <div className="admin-main">
        <header className="admin-topbar">
          <button
            className="icon-btn admin-menu"
            onClick={() => setOpen(true)}
            aria-label="Mở thanh bên"
          >
            <Menu size={21} />
          </button>
          <div>
            <span>Workspace</span>
            <i>/</i>
            <strong>{current}</strong>
          </div>
          <a href={customerUrl} className="text-link">
            CineVerse <ArrowUpRight size={15} />
          </a>
          <span className="admin-live">
            <span />
            Dữ liệu từ database
          </span>
        </header>
        <main className="admin-content">
          <Outlet />
        </main>
        <footer className="admin-footer">
          CINEVERSE STUDIO <span>Java 21 · Spring Boot · MySQL</span>
        </footer>
      </div>
    </div>
  );
}
