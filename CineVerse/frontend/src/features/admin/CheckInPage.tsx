import { useState } from "react";
import { ScanLine, CheckCircle2 } from "lucide-react";
import { send } from "../../lib/api";
import { Button } from "../../components/ui";
import type { CinemaTicket } from "../../types";
export default function CheckInPage() {
  const [code, setCode] = useState(""),
    [ticket, setTicket] = useState<CinemaTicket | null>(null),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  return (
    <>
      <div className="admin-page-heading">
        <div>
          <span className="eyebrow">WELCOME TO THE BIG SCREEN</span>
          <h1>Soát vé điện tử.</h1>
          <p>Nhập nội dung QR từ máy quét hoặc mã vé. Mỗi vé chỉ dùng một lần.</p>
        </div>
      </div>
      <section className="admin-box settings-box">
        <ScanLine size={35} className="gold" />
        <form
          className="settings-form"
          onSubmit={(e) => {
            e.preventDefault();
            setBusy(true);
            setError("");
            setTicket(null);
            void send<CinemaTicket>("/admin/tickets/check-in", "POST", { code })
              .then(setTicket)
              .catch((e: Error) => setError(e.message))
              .finally(() => setBusy(false));
          }}
        >
          <label>
            Mã QR / nội dung vé
            <input
              autoFocus
              required
              value={code}
              onChange={(e) => setCode(e.target.value)}
              placeholder="CINEVERSE:TICKET:…"
            />
          </label>
          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}
          <Button disabled={busy}>Xác nhận soát vé</Button>
        </form>
        {ticket && (
          <div className="checkin-success" role="status">
            <CheckCircle2 size={32} />
            <div>
              <h2>Vé hợp lệ · Ghế {ticket.seatLabel}</h2>
              <p>Đã đánh dấu USED. Chúc quý khách xem phim vui vẻ.</p>
            </div>
          </div>
        )}
      </section>
    </>
  );
}
