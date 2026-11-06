import { useState } from "react";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import {
  Eye,
  EyeOff,
  ArrowRight,
  ArrowLeft,
  CheckCircle2,
  Mail,
  ShieldCheck,
} from "lucide-react";
import { Button } from "../../components/ui";
import { useAuth } from "../../app/AuthProvider";
import { send, ApiError } from "../../lib/api";
import { Logo } from "../../layouts/RootLayout";
const password = z
  .string()
  .min(10, "Ít nhất 10 ký tự")
  .max(72, "Tối đa 72 ký tự")
  .regex(/[A-Z]/, "Cần có chữ hoa")
  .regex(/[a-z]/, "Cần có chữ thường")
  .regex(/[0-9]/, "Cần có chữ số")
  .regex(/[^A-Za-z0-9]/, "Cần có ký tự đặc biệt");
const schema = z.object({
  name: z.string(),
  email: z.string(),
  password: z.string(),
  confirm: z.string(),
  terms: z.boolean(),
  marketing: z.boolean(),
  remember: z.boolean(),
});
type Values = z.infer<typeof schema>;
export default function AuthPage() {
  const path = useLocation().pathname;
  const nav = useNavigate();
  const [params] = useSearchParams();
  const auth = useAuth();
  const [show, setShow] = useState(false),
    [message, setMessage] = useState(""),
    [error, setError] = useState("");
  const registerMode = path === "/dang-ky",
    forgot = path === "/quen-mat-khau",
    reset = path === "/dat-lai-mat-khau",
    verify = path === "/xac-thuc-email",
    login = path === "/dang-nhap";
  const validation = schema.superRefine((v, ctx) => {
    if (!verify && !reset && !z.email().safeParse(v.email).success)
      ctx.addIssue({
        code: "custom",
        path: ["email"],
        message: "Email chưa đúng định dạng",
      });
    if (registerMode) {
      if (v.name.trim().length < 2 || v.name.length > 100)
        ctx.addIssue({
          code: "custom",
          path: ["name"],
          message: "Họ tên cần từ 2 đến 100 ký tự",
        });
      if (!v.terms)
        ctx.addIssue({
          code: "custom",
          path: ["terms"],
          message: "Vui lòng đồng ý điều khoản",
        });
    }
    if (registerMode || reset) {
      const parsed = password.safeParse(v.password);
      if (!parsed.success)
        ctx.addIssue({
          code: "custom",
          path: ["password"],
          message: parsed.error.issues[0].message,
        });
      if (v.confirm !== v.password)
        ctx.addIssue({
          code: "custom",
          path: ["confirm"],
          message: "Mật khẩu xác nhận chưa khớp",
        });
    }
    if (login && !v.password)
      ctx.addIssue({
        code: "custom",
        path: ["password"],
        message: "Nhập mật khẩu của bạn",
      });
  });
  const form = useForm<Values>({
    resolver: zodResolver(validation),
    defaultValues: {
      name: "",
      email: "",
      password: "",
      confirm: "",
      terms: false,
      marketing: false,
      remember: false,
    },
  });
  const title = registerMode
    ? "Bắt đầu câu chuyện của bạn."
    : forgot
      ? "Tìm lại lối vào."
      : reset
        ? "Một khởi đầu an toàn."
        : verify
          ? "Xác thực email."
          : "Chào mừng trở lại.";
  async function submit(v: Values) {
    setError("");
    try {
      if (login) {
        await auth.login(v.email, v.password, v.remember);
        const next = params.get("next");
        nav(next?.startsWith("/") && !next.startsWith("//") ? next : "/");
        return;
      }
      if (registerMode) {
        const res = await send<{ message: string }>("/auth/register", "POST", v);
        setMessage(res.message);
      } else if (forgot) {
        const res = await send<{ message: string }>("/auth/forgot-password", "POST", {
          email: v.email,
        });
        setMessage(res.message);
      } else if (reset) {
        const res = await send<{ message: string }>("/auth/reset-password", "POST", {
          token: params.get("token") ?? "",
          password: v.password,
        });
        setMessage(res.message);
      } else if (verify) {
        const res = await send<{ message: string }>(
          params.get("change") === "1"
            ? "/auth/confirm-email-change"
            : "/auth/verify-email",
          "POST",
          { token: params.get("token") ?? "" },
        );
        setMessage(res.message);
      }
    } catch (e) {
      setError((e as Error).message);
      if (e instanceof ApiError)
        for (const [key, value] of Object.entries(e.fields))
          if (key in form.getValues())
            form.setError(key as keyof Values, { message: value });
    }
  }
  const field = (
    key: "name" | "email" | "password" | "confirm",
    label: string,
    type = "text",
    placeholder = "",
  ) => (
    <label className="form-field">
      {label}
      <div className={key === "password" ? "password-input" : ""}>
        <input
          {...form.register(key)}
          type={key === "password" && show ? "text" : type}
          autoComplete={
            key === "password"
              ? login
                ? "current-password"
                : "new-password"
              : key === "confirm"
                ? "new-password"
                : key === "name"
                  ? "name"
                  : "email"
          }
          placeholder={placeholder}
          aria-invalid={!!form.formState.errors[key]}
        />
        {key === "password" && (
          <button
            type="button"
            aria-label={show ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
            onClick={() => setShow(!show)}
          >
            {show ? <EyeOff size={17} /> : <Eye size={17} />}
          </button>
        )}
      </div>
      {form.formState.errors[key] && (
        <span className="field-error" role="alert">
          {form.formState.errors[key]?.message}
        </span>
      )}
    </label>
  );
  return (
    <div className="auth-page">
      <div className="auth-art">
        <img src="/art/hero-horizon.png" alt="Khung cảnh hành tinh hư cấu" />
        <div />
        <Logo />
        <div className="auth-art-copy">
          <span className="eyebrow">YOUR UNIVERSE OF CINEMA</span>
          <h2>
            Những câu chuyện
            <br />
            đáng để <em>đắm chìm.</em>
          </h2>
          <p>Một thế giới điện ảnh, được chọn lọc dành cho bạn.</p>
          <span className="auth-art-line" />
        </div>
        <small>CINEVERSE · BỘ SƯU TẬP DEMO HƯ CẤU</small>
      </div>
      <div className="auth-form-side">
        <Link to="/" className="back-link">
          <ArrowLeft size={15} />
          Trở về CineVerse
        </Link>
        <div className="auth-form">
          <div className="auth-mobile-logo">
            <Logo />
          </div>
          <span className="eyebrow">
            {registerMode ? "JOIN THE UNIVERSE" : "YOUR NEXT STORY AWAITS"}
          </span>
          <h1>{title}</h1>
          <p>
            {registerMode
              ? "Tạo tài khoản để chọn suất chiếu, đặt vé và lưu những bộ phim yêu thích."
              : forgot
                ? "Nhập email của bạn. Chúng tôi sẽ gửi liên kết đặt lại mật khẩu."
                : reset
                  ? "Chọn mật khẩu có chữ hoa, chữ thường, số và ký tự đặc biệt."
                  : verify
                    ? "Bấm nút bên dưới để xác nhận địa chỉ email và mở tài khoản."
                    : "Đăng nhập và tiếp tục những khoảnh khắc điện ảnh."}
          </p>
          {message ? (
            <div className="auth-success" role="status">
              <CheckCircle2 size={36} />
              <h2>
                {verify
                  ? "Tài khoản đã sẵn sàng"
                  : registerMode
                    ? "Đăng ký thành công"
                    : "Yêu cầu đã được xử lý"}
              </h2>
              <p>{message}</p>
              <Button asChild>
                <Link to="/dang-nhap">
                  Đến trang đăng nhập <ArrowRight size={16} />
                </Link>
              </Button>
            </div>
          ) : (
            <form onSubmit={form.handleSubmit(submit)} noValidate>
              {registerMode && field("name", "Họ và tên", "text", "Tên của bạn")}
              {!verify &&
                !reset &&
                field("email", "Địa chỉ email", "email", "ban@example.com")}
              {(login || registerMode || reset) &&
                field(
                  "password",
                  "Mật khẩu",
                  "password",
                  login ? "Nhập mật khẩu của bạn" : "Ít nhất 10 ký tự",
                )}
              {(registerMode || reset) &&
                field("confirm", "Xác nhận mật khẩu", "password", "Nhập lại mật khẩu")}
              {login && (
                <div className="form-between">
                  <label className="checkbox-label">
                    <input type="checkbox" {...form.register("remember")} />
                    Ghi nhớ đăng nhập
                  </label>
                  <Link to="/quen-mat-khau">Quên mật khẩu?</Link>
                </div>
              )}
              {registerMode && (
                <div className="auth-consents">
                  <label className="checkbox-label">
                    <input type="checkbox" {...form.register("terms")} />
                    <span>
                      Tôi đồng ý với <Link to="/dieu-khoan">Điều khoản</Link> và{" "}
                      <Link to="/chinh-sach-bao-mat">Chính sách bảo mật</Link>.
                    </span>
                  </label>
                  {form.formState.errors.terms && (
                    <span className="field-error">
                      {form.formState.errors.terms.message}
                    </span>
                  )}
                  <label className="checkbox-label">
                    <input type="checkbox" {...form.register("marketing")} />
                    Nhận tin về nội dung mới (tùy chọn)
                  </label>
                </div>
              )}
              {error && (
                <p className="form-error" role="alert">
                  {error}
                </p>
              )}
              <Button className="auth-submit" disabled={form.formState.isSubmitting}>
                {form.formState.isSubmitting
                  ? "Đang xử lý…"
                  : registerMode
                    ? "Tạo tài khoản"
                    : forgot
                      ? "Gửi liên kết đặt lại"
                      : reset
                        ? "Đặt lại mật khẩu"
                        : verify
                          ? "Xác thực email"
                          : "Đăng nhập"}
                {forgot ? (
                  <Mail size={17} />
                ) : verify ? (
                  <ShieldCheck size={17} />
                ) : (
                  <ArrowRight size={17} />
                )}
              </Button>
            </form>
          )}
          {!message && (login || registerMode) && (
            <p className="auth-switch">
              {registerMode ? "Đã có tài khoản?" : "Chưa có tài khoản?"}{" "}
              <Link to={registerMode ? "/dang-nhap" : "/dang-ky"}>
                {registerMode ? "Đăng nhập" : "Đăng ký miễn phí"} <ArrowRight size={13} />
              </Link>
            </p>
          )}
          <div className="auth-trust">
            <ShieldCheck size={15} />
            <span>
              Thông tin của bạn được bảo vệ và chỉ dùng cho trải nghiệm CineVerse.
            </span>
          </div>
        </div>
        <small className="auth-copyright">
          © 2026 CineVerse · Your Universe of Cinema
        </small>
      </div>
    </div>
  );
}
