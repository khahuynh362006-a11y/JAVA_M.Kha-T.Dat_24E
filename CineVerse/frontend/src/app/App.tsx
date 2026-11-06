import { lazy, Suspense, useEffect } from "react";
import {
  BrowserRouter,
  Routes,
  Route,
  Outlet,
  Navigate,
  useLocation,
  Link,
} from "react-router-dom";
import { useAuth } from "./AuthProvider";
import RootLayout from "../layouts/RootLayout";
import { Button, Empty, Loading } from "../components/ui";
import { adminUrl } from "../lib/urls";
function AdminRedirect() {
  const location = useLocation();
  useEffect(() => {
    window.location.replace(
      adminUrl + location.pathname + location.search + location.hash,
    );
  }, [location]);
  return <Loading />;
}
const Home = lazy(() => import("../features/movies/HomePage"));
const Discover = lazy(() => import("../features/movies/DiscoverPage"));
const Detail = lazy(() => import("../features/movies/DetailPage"));
const Auth = lazy(() => import("../features/auth/AuthPage"));
const Account = lazy(() => import("../features/account/AccountPage"));
const Library = lazy(() =>
  import("../features/account/AccountPage").then((m) => ({ default: m.LibraryPage })),
);
const Notifications = lazy(() =>
  import("../features/account/AccountPage").then((m) => ({
    default: m.NotificationsPage,
  })),
);
const Cinema = lazy(() =>
  import("../features/booking/BookingPages").then((m) => ({ default: m.CinemaPage })),
);
const Seats = lazy(() =>
  import("../features/booking/BookingPages").then((m) => ({
    default: m.SeatSelectionPage,
  })),
);
const Checkout = lazy(() =>
  import("../features/booking/BookingPages").then((m) => ({ default: m.CheckoutPage })),
);
const Tickets = lazy(() =>
  import("../features/booking/BookingPages").then((m) => ({ default: m.MyTicketsPage })),
);
const Simulation = lazy(() =>
  import("../features/payments/PaymentPages").then((m) => ({
    default: m.SimulationPage,
  })),
);
const Result = lazy(() =>
  import("../features/payments/PaymentPages").then((m) => ({
    default: m.PaymentResultPage,
  })),
);
const Payments = lazy(() =>
  import("../features/payments/PaymentPages").then((m) => ({
    default: m.PaymentHistoryPage,
  })),
);
function Guard() {
  const { user, loading } = useAuth(),
    location = useLocation();
  if (loading) return <Loading />;
  if (!user)
    return (
      <Navigate
        replace
        to={"/dang-nhap?next=" + encodeURIComponent(location.pathname + location.search)}
      />
    );
  return <Outlet />;
}
function Legal() {
  const privacy = useLocation().pathname.includes("bao-mat");
  return (
    <article className="page container legal-page">
      <span className="eyebrow">CINEVERSE / TRUST & TRANSPARENCY</span>
      <h1>{privacy ? "Chính sách bảo mật." : "Điều khoản đặt vé."}</h1>
      <p>Bản đồ án bán vé xem phim CineVerse · 05/11/2026.</p>
      {privacy ? (
        <>
          <h2>Thông tin và phiên đăng nhập</h2>
          <p>
            Hệ thống lưu tên, email, mật khẩu băm BCrypt, danh sách yêu thích, đánh giá,
            đơn đặt ghế và vé điện tử. Không lưu mật khẩu ví hoặc dữ liệu thẻ. Access
            token nằm trong bộ nhớ, refresh token trong cookie HttpOnly; database chỉ lưu
            hash. Bạn có thể chỉnh sửa hồ sơ và thu hồi phiên tại trang tài khoản.
          </p>
          <h2>Email và mã vé</h2>
          <p>
            Email giao dịch dùng để xác thực, bảo mật và gửi thông tin vé. Mã QR chỉ cho
            chủ tài khoản và quản trị viên truy cập. Không chia sẻ mã; mỗi vé chỉ được
            soát một lần.
          </p>
        </>
      ) : (
        <>
          <h2>Phạm vi demo</h2>
          <p>
            Đây là đồ án Java full-stack. Phim, rạp và lịch chiếu là hư cấu. Vé demo không
            có giá trị tại rạp thương mại. Trailer minh họa dùng media nguồn mở có ghi
            nguồn.
          </p>
          <h2>Giữ ghế và thanh toán</h2>
          <p>
            Mỗi đơn chọn 1–8 ghế, được giữ trong 8 phút. Vé chỉ phát hành sau xác nhận
            thanh toán hợp lệ. Mô phỏng không thu tiền và không phải MoMo; MoMo Sandbox là
            môi trường thử nghiệm.
          </p>
          <h2>Hủy và đối soát</h2>
          <p>
            Bạn có thể hủy giữ ghế khi chưa thanh toán. Hệ thống chưa có hoàn tiền tự động
            cho vé đã phát hành. Thanh toán đến sau khi ghế hết hạn giữ được đưa vào trạng
            thái cần hỗ trợ; không lấy ghế của khách khác. Quản trị viên phải đối soát và
            hỗ trợ hoàn tiền bên ngoài nếu cần.
          </p>
          <h2>Soát vé</h2>
          <p>
            Kiểm tra độ tuổi trước khi đặt. Xuất trình QR tại rạp; mã chỉ chấp nhận một
            lần. Nhân viên xác minh điều kiện vào xem.
          </p>
        </>
      )}
      <h2>Vận hành</h2>
      <p>
        Triển khai công khai cần đơn vị vận hành rạp, nội dung hợp pháp, quy trình hỗ trợ
        và hoàn tiền thực tế trước khi tiếp nhận khách hoặc tiền thật.
      </p>
      <Button asChild variant="outline">
        <Link to="/">Trở về CineVerse</Link>
      </Button>
    </article>
  );
}
function NotFound() {
  return (
    <div className="page container">
      <Empty
        title="Câu chuyện này chưa tồn tại."
        message="Khám phá một bộ phim và buổi hẹn điện ảnh mới."
      >
        <Button asChild>
          <Link to="/">Trở về trang chủ</Link>
        </Button>
      </Empty>
    </div>
  );
}
export default function App() {
  return (
    <BrowserRouter>
      <Suspense fallback={<Loading />}>
        <Routes>
          {[
            "dang-nhap",
            "dang-ky",
            "quen-mat-khau",
            "dat-lai-mat-khau",
            "xac-thuc-email",
          ].map((p) => (
            <Route key={p} path={"/" + p} element={<Auth />} />
          ))}
          <Route element={<RootLayout />}>
            <Route index element={<Home />} />
            {[
              "kham-pha",
              "phim-dang-chieu",
              "phim-sap-chieu",
              "phim-moi",
              "thinh-hanh",
              "bang-xep-hang",
              "the-loai",
              "the-loai/:slug",
              "quoc-gia/:slug",
              "tim-kiem",
            ].map((p) => (
              <Route key={p} path={p} element={<Discover />} />
            ))}
            <Route path="phim/:slug" element={<Detail />} />
            <Route path="rap-chieu" element={<Cinema />} />
            <Route path="dat-ve/:movieId" element={<Cinema />} />
            <Route element={<Guard />}>
              <Route path="dat-ve/suat/:showtimeId" element={<Seats />} />
              <Route path="dat-ve/thanh-toan/:bookingId" element={<Checkout />} />
              <Route path="ve-cua-toi" element={<Tickets />} />
              <Route path="ve-cua-toi/:bookingId" element={<Tickets />} />
              {["tai-khoan", "tai-khoan/chinh-sua", "tai-khoan/bao-mat"].map((p) => (
                <Route key={p} path={p} element={<Account />} />
              ))}
              <Route path="danh-sach-cua-toi" element={<Library />} />
              <Route path="thong-bao" element={<Notifications />} />
              <Route path="lich-su-thanh-toan" element={<Payments />} />
              <Route path="thanh-toan/mo-phong/:id" element={<Simulation />} />
              <Route path="thanh-toan/ket-qua" element={<Result />} />
            </Route>
            {["vip", "phim-bo", "phim-le"].map((p) => (
              <Route key={p} path={p} element={<Navigate to="/kham-pha" replace />} />
            ))}
            {["goi-thanh-vien", "lich-su-xem"].map((p) => (
              <Route key={p} path={p} element={<Navigate to="/ve-cua-toi" replace />} />
            ))}
            <Route path="dieu-khoan" element={<Legal />} />
            <Route path="chinh-sach-bao-mat" element={<Legal />} />
            <Route path="admin/*" element={<AdminRedirect />} />
            <Route path="*" element={<NotFound />} />
          </Route>
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}
