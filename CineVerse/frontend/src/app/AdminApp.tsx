import { lazy, Suspense } from "react";
import {
  BrowserRouter,
  Routes,
  Route,
  Navigate,
  Outlet,
  useLocation,
  Link,
} from "react-router-dom";
import { useAuth } from "./AuthProvider";
import { Loading, Empty, Button } from "../components/ui";
const Login = lazy(() => import("../features/admin/AdminLoginPage"));
const Admin = lazy(() => import("../features/admin/AdminLayout"));
const Dashboard = lazy(() => import("../features/admin/DashboardPage"));
const Resource = lazy(() => import("../features/admin/ResourcePage"));
const Settings = lazy(() =>
  import("../features/admin/ResourcePage").then((m) => ({ default: m.SettingsPage })),
);
const CheckIn = lazy(() => import("../features/admin/CheckInPage"));

function Guard() {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <Loading />;
  if (!user || user.role !== "ADMIN")
    return (
      <Navigate
        replace
        to={"/dang-nhap?next=" + encodeURIComponent(location.pathname + location.search)}
      />
    );
  return <Outlet />;
}
function NotFound() {
  return (
    <Empty
      title="Không tìm thấy trang quản trị"
      message="Chọn một mục trong thanh điều hướng."
    >
      <Button asChild>
        <Link to="/admin">Về tổng quan</Link>
      </Button>
    </Empty>
  );
}
export default function AdminApp() {
  return (
    <BrowserRouter>
      <Suspense fallback={<Loading />}>
        <Routes>
          <Route path="/dang-nhap" element={<Login />} />
          <Route path="/" element={<Navigate to="/admin" replace />} />{" "}
          <Route element={<Guard />}>
            <Route path="/admin" element={<Admin />}>
              <Route index element={<Dashboard />} />
              <Route path="analytics" element={<Dashboard />} />
              <Route path="settings" element={<Settings />} />
              <Route path="check-in" element={<CheckIn />} />
              {[
                "movies",
                "genres",
                "countries",
                "users",
                "cinemas",
                "auditoriums",
                "seats",
                "showtimes",
                "bookings",
                "tickets",
                "payments",
                "comments",
                "banners",
                "notifications",
                "email-logs",
                "audit-logs",
              ].map((p) => (
                <Route key={p} path={p} element={<Resource />} />
              ))}
              <Route path="*" element={<NotFound />} />
            </Route>
          </Route>
          <Route path="*" element={<NotFound />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}
