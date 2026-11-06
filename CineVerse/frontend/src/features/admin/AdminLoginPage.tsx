import { useState, type FormEvent } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { ShieldCheck, ArrowRight, Eye, EyeOff } from "lucide-react";
import { useAuth } from "../../app/AuthProvider";
import { Logo } from "../../components/Logo";
import { Button } from "../../components/ui";
import { api } from "../../lib/api";
import { customerUrl } from "../../lib/urls";
import type { User } from "../../types";

export default function AdminLoginPage() {
  const auth = useAuth(),
    nav = useNavigate();
  const [params] = useSearchParams();
  const [email, setEmail] = useState(""),
    [password, setPassword] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [show, setShow] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      await auth.login(email, password, false);
      const user = await api<User>("/users/me");
      if (user.role !== "ADMIN") {
        await auth.logout();
        setError("Tài khoản này không có quyền quản trị.");
        return;
      }
      const next = params.get("next");
      nav(next?.startsWith("/admin") ? next : "/admin", { replace: true });
    } catch (error) {
      setError((error as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="admin-login">
      <section className="admin-login-story">
        <Logo />
        <div>
          <span className="eyebrow">CINEVERSE STUDIO</span>
          <h1>
            Vận hành một
            <br />
            thế giới điện ảnh.
          </h1>
          <p>
            Phim, suất chiếu và trải nghiệm của khán giả — cùng một không gian quản trị.
          </p>
        </div>
        <small>QUẢN TRỊ RẠP CHIẾU · CINEVERSE</small>
      </section>
      <section className="admin-login-form">
        <ShieldCheck size={34} className="gold" />
        <span className="eyebrow">ADMIN WORKSPACE</span>
        <h2>Đăng nhập quản trị.</h2>
        <p>Dành cho tài khoản được cấp quyền quản trị.</p>
        <form onSubmit={(e) => void submit(e)}>
          <label>
            Email quản trị
            <input
              type="email"
              required
              maxLength={254}
              autoComplete="username"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="admin@cineverse.local"
            />
          </label>
          <label>
            Mật khẩu
            <div className="password-input">
              <input
                type={show ? "text" : "password"}
                required
                maxLength={72}
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
              <button
                type="button"
                className="icon-btn"
                aria-label={show ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                onClick={() => setShow(!show)}
              >
                {show ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
          </label>
          {error && (
            <p role="alert" className="form-error">
              {error}
            </p>
          )}
          <Button disabled={busy}>
            {busy ? "Đang đăng nhập…" : "Vào trang quản trị"}
            <ArrowRight size={17} />
          </Button>
        </form>
        <a className="text-link" href={customerUrl}>
          Mở trang người dùng ↗
        </a>
      </section>
    </main>
  );
}
