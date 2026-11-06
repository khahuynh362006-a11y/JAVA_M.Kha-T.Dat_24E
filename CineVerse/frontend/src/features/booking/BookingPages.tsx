import { useEffect, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Ticket,
  MapPin,
  Clock3,
  ArrowRight,
  ArrowLeft,
  Check,
  LockKeyhole,
  RefreshCw,
  ShieldCheck,
  X,
  CalendarDays,
  Armchair,
  Printer,
} from "lucide-react";
import { QRCodeSVG } from "qrcode.react";
import { toast } from "sonner";
import { api, date, money, send } from "../../lib/api";
import { Button, Loading, ErrorState, Empty, Modal } from "../../components/ui";
import type { Booking, Cinema, SeatMap, Showtime, Order } from "../../types";
const tz = "Asia/Ho_Chi_Minh";
export const showTime = (s: string) =>
  new Intl.DateTimeFormat("vi-VN", {
    hour: "2-digit",
    minute: "2-digit",
    timeZone: tz,
  }).format(new Date(s));
export const showDay = (s: string) =>
  new Intl.DateTimeFormat("vi-VN", {
    weekday: "long",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    timeZone: tz,
  }).format(new Date(s));
const localDate = (d: Date) =>
  new Intl.DateTimeFormat("en-CA", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    timeZone: tz,
  }).format(d);
function group<T, K>(items: T[], key: (x: T) => K) {
  const map = new Map<K, T[]>();
  items.forEach((x) => {
    const k = key(x);
    map.set(k, [...(map.get(k) ?? []), x]);
  });
  return Array.from(map.entries());
}
const days = () =>
  Array.from({ length: 7 }, (_, i) => {
    const d = new Date();
    d.setDate(d.getDate() + i);
    return {
      key: localDate(d),
      label:
        i === 0
          ? "Hôm nay"
          : i === 1
            ? "Ngày mai"
            : new Intl.DateTimeFormat("vi-VN", { weekday: "short", timeZone: tz }).format(
                d,
              ),
      day: new Intl.DateTimeFormat("vi-VN", {
        day: "2-digit",
        month: "2-digit",
        timeZone: tz,
      }).format(d),
    };
  });
export function CinemaPage() {
  const { movieId } = useParams();
  return (
    <div className="page container cinema-page">
      <div className="page-intro">
        <span className="eyebrow">THE BIG SCREEN IS CALLING</span>
        <h1>{movieId ? "Chọn một buổi hẹn điện ảnh." : "Rạp chiếu. Trải nghiệm."}</h1>
        <p>Chọn rạp, suất chiếu và chiếc ghế dành riêng cho bạn.</p>
      </div>
      <ShowtimePicker movieId={movieId ? Number(movieId) : undefined} />
      <div className="notice subtle cinema-demo">
        <MapPin size={16} />
        Rạp, phim và lịch chiếu demo là hư cấu. Vé không có giá trị tại rạp thương mại.
      </div>
    </div>
  );
}
export function ShowtimePicker({
  movieId,
  compact = false,
}: {
  movieId?: number;
  compact?: boolean;
}) {
  const [params, setParams] = useSearchParams();
  const dateKey = params.get("ngay") ?? days()[0].key,
    cinema = params.get("rap") ?? "";
  const cinemas = useQuery({
    queryKey: ["cinemas"],
    queryFn: () => api<Cinema[]>("/cinemas"),
  });
  const q = new URLSearchParams({ date: dateKey });
  if (movieId) q.set("movieId", String(movieId));
  if (cinema) q.set("cinemaId", cinema);
  const shows = useQuery({
    queryKey: ["showtimes", q.toString()],
    queryFn: () => api<Showtime[]>("/showtimes?" + q),
    refetchInterval: 30000,
  });
  const set = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    setParams(next, { preventScrollReset: true });
  };
  return (
    <section id="lich-chieu" className={`showtime-picker ${compact ? "compact" : ""}`}>
      <div className="section-heading">
        <div>
          <span className="eyebrow">MAKE IT A MOVIE NIGHT</span>
          <h2>
            Lịch chiếu & đặt vé<span className="section-dot">.</span>
          </h2>
        </div>
        <label className="cinema-select">
          <MapPin size={16} />
          <select
            aria-label="Chọn rạp chiếu"
            value={cinema}
            onChange={(e) => set("rap", e.target.value)}
          >
            <option value="">Tất cả rạp</option>
            {cinemas.data?.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
      </div>
      <div className="date-strip" role="group" aria-label="Chọn ngày xem phim">
        {days().map((d) => (
          <button
            key={d.key}
            onClick={() => set("ngay", d.key)}
            className={dateKey === d.key ? "active" : ""}
          >
            <span>{d.label}</span>
            <strong>{d.day}</strong>
          </button>
        ))}
      </div>
      {shows.isLoading ? (
        <Loading />
      ) : shows.error ? (
        <ErrorState error={shows.error} retry={() => void shows.refetch()} />
      ) : shows.data?.length ? (
        <div className="cinema-showtimes">
          {group(shows.data, (s) => s.cinemaId).map(([id, list]) => (
            <article className="cinema-schedule" key={id}>
              <header>
                <div className="cinema-mark">
                  <Ticket size={22} />
                </div>
                <div>
                  <h3>{list[0].cinemaName}</h3>
                  <p>
                    <MapPin size={12} />
                    {list[0].address}
                  </p>
                </div>
                <span className="cinema-city">{list[0].city}</span>
              </header>
              {group(list, (s) => s.movieId).map(([mid, movieShows]) => (
                <div className="showtime-movie" key={mid}>
                  {!compact && (
                    <Link
                      to={"/phim/" + movieShows[0].movieSlug}
                      className="schedule-poster"
                    >
                      <img src={movieShows[0].poster} alt="" />
                    </Link>
                  )}
                  <div>
                    {!compact && (
                      <div className="schedule-movie-title">
                        <Link to={"/phim/" + movieShows[0].movieSlug}>
                          {movieShows[0].movieTitle}
                        </Link>
                        <span>
                          {movieShows[0].duration} phút · {movieShows[0].ageRating}
                        </span>
                      </div>
                    )}
                    <div className="showtime-buttons">
                      {movieShows.map((s) => (
                        <Link
                          key={s.id}
                          className={`showtime-button ${s.availableSeats === 0 ? "sold-out" : ""}`}
                          aria-label={`Đặt vé suất ${showTime(s.startsAt)} tại ${s.cinemaName} ${s.auditoriumName}`}
                          to={"/dat-ve/suat/" + s.id}
                        >
                          <strong>{showTime(s.startsAt)}</strong>
                          <span>
                            {s.format} · {money(s.basePrice)}
                          </span>
                          <small>{s.availableSeats} ghế trống</small>
                        </Link>
                      ))}
                    </div>
                  </div>
                </div>
              ))}
            </article>
          ))}
        </div>
      ) : (
        <Empty
          title="Một buổi hẹn khác đang chờ"
          message="Chưa có suất chiếu phù hợp. Hãy chọn một ngày hoặc rạp khác."
        />
      )}
    </section>
  );
}
export function SeatSelectionPage() {
  const { showtimeId } = useParams();
  const navigate = useNavigate();
  const [selected, setSelected] = useState<number[]>([]),
    [busy, setBusy] = useState(false);
  const data = useQuery({
    queryKey: ["seatmap", showtimeId],
    queryFn: () => api<SeatMap>(`/showtimes/${showtimeId}/seats`),
    refetchInterval: 5000,
  });
  if (data.isLoading)
    return (
      <div className="page container">
        <Loading />
      </div>
    );
  if (data.error)
    return (
      <div className="page container">
        <ErrorState error={data.error} retry={() => void data.refetch()} />
      </div>
    );
  const map = data.data;
  if (!map) return null;
  const s = map.showtime,
    chosen = map.seats.filter((x) => selected.includes(x.id)),
    total = chosen.reduce((sum, x) => sum + x.price, 0);
  async function reserve() {
    setBusy(true);
    try {
      const b = await send<Booking>("/bookings", "POST", {
        showtimeId: Number(showtimeId),
        seatIds: selected,
      });
      navigate("/dat-ve/thanh-toan/" + b.id);
    } catch (e) {
      toast.error((e as Error).message);
      void data.refetch();
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="page container seat-page">
      <Link to={"/dat-ve/" + s.movieId} className="back-link">
        <ArrowLeft size={16} />
        Chọn suất khác
      </Link>
      <div className="booking-steps">
        <span className="done">
          <Check size={14} />
          Suất chiếu
        </span>
        <i />
        <span className="active">02 · Chọn ghế</span>
        <i />
        <span>03 · Thanh toán</span>
        <i />
        <span>04 · Nhận vé</span>
      </div>
      <div className="seat-layout">
        <section className="seat-theater">
          <div className="seat-heading">
            <div>
              <span className="eyebrow">YOUR SEAT. YOUR MOMENT.</span>
              <h1>Chọn góc nhìn của bạn.</h1>
            </div>
            <button
              className="icon-btn"
              aria-label="Làm mới sơ đồ ghế"
              onClick={() => void data.refetch()}
            >
              <RefreshCw size={17} />
            </button>
          </div>
          <div className="screen">
            <div />
            <span>MÀN HÌNH</span>
          </div>
          <div className="seat-map" role="group" aria-label="Sơ đồ ghế rạp phim">
            {group(map.seats, (x) => x.rowLabel).map(([row, seats]) => (
              <div className="seat-row" key={row}>
                <span>{row}</span>
                <div>
                  {seats.map((seat, i) => (
                    <button
                      key={seat.id}
                      className={`seat ${seat.kind.toLowerCase()} ${seat.status.toLowerCase()} ${selected.includes(seat.id) ? "selected" : ""} ${i === 4 ? "aisle" : ""}`}
                      aria-label={`Ghế ${seat.rowLabel}${seat.number}${seat.kind === "VIP" ? " VIP" : ""} ${seat.status === "AVAILABLE" ? money(seat.price) : seat.status === "HELD" ? "đang được giữ" : "đã bán"}`}
                      aria-pressed={selected.includes(seat.id)}
                      disabled={seat.status !== "AVAILABLE"}
                      onClick={() => {
                        if (selected.includes(seat.id))
                          setSelected((x) => x.filter((id) => id !== seat.id));
                        else if (selected.length < 8) setSelected((x) => [...x, seat.id]);
                        else toast.info("Mỗi lần chọn tối đa 8 ghế.");
                      }}
                    >
                      {seat.status === "BOOKED" ? (
                        <X size={12} />
                      ) : seat.status === "HELD" ? (
                        <LockKeyhole size={10} />
                      ) : (
                        seat.number
                      )}
                    </button>
                  ))}
                </div>
                <span>{row}</span>
              </div>
            ))}
          </div>
          <div className="seat-legend">
            <span>
              <i className="seat standard" />
              Ghế thường
            </span>
            <span>
              <i className="seat vip" />
              Ghế VIP
            </span>
            <span>
              <i className="seat selected" />
              Đang chọn
            </span>
            <span>
              <i className="seat booked" />
              Đã bán / đang giữ
            </span>
          </div>
          <p className="seat-note">
            Tối đa 8 ghế / đơn. Ghế được giữ 8 phút khi bạn tiếp tục.
          </p>
        </section>
        <aside className="booking-summary">
          <div className="booking-movie">
            <img src={s.poster} alt={s.movieTitle} />
            <div>
              <span className="eyebrow">CINEVERSE CINEMAS</span>
              <h2>{s.movieTitle}</h2>
              <span className="age-badge">{s.ageRating}</span>{" "}
              <span className="quality-badge">{s.format}</span>
              <small>{s.duration} phút</small>
            </div>
          </div>
          <dl>
            <div>
              <dt>Rạp chiếu</dt>
              <dd>{s.cinemaName}</dd>
            </div>
            <div>
              <dt>Phòng chiếu</dt>
              <dd>{s.auditoriumName}</dd>
            </div>
            <div>
              <dt>Ngày chiếu</dt>
              <dd>{showDay(s.startsAt)}</dd>
            </div>
            <div>
              <dt>Suất chiếu</dt>
              <dd>{showTime(s.startsAt)}</dd>
            </div>
            <div>
              <dt>Ghế đã chọn</dt>
              <dd className="selected-seat-labels">
                {chosen.length
                  ? chosen.map((x) => x.rowLabel + x.number).join(", ")
                  : "Chưa chọn ghế"}
              </dd>
            </div>
          </dl>
          <div className="booking-total">
            <span>Tổng tiền</span>
            <strong>{money(total)}</strong>
          </div>
          <Button disabled={!chosen.length || busy} onClick={() => void reserve()}>
            {busy ? "Đang giữ ghế…" : "Tiếp tục thanh toán"}
            <ArrowRight size={16} />
          </Button>
          <div className="booking-trust">
            <ShieldCheck size={14} />
            <span>Giá và tình trạng ghế được xác nhận tại server.</span>
          </div>
        </aside>
      </div>
    </div>
  );
}
function useCountdown(until: string | undefined) {
  const [seconds, setSeconds] = useState(0);
  useEffect(() => {
    const tick = () =>
      setSeconds(
        until
          ? Math.max(0, Math.floor((new Date(until).getTime() - Date.now()) / 1000))
          : 0,
      );
    tick();
    const timer = setInterval(tick, 1000);
    return () => clearInterval(timer);
  }, [until]);
  return seconds;
}
export function CheckoutPage() {
  const { bookingId } = useParams();
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const data = useQuery({
    queryKey: ["booking", bookingId],
    queryFn: () => api<Booking>("/bookings/" + bookingId),
    refetchInterval: 10000,
  });
  const config = useQuery({
    queryKey: ["payment-config"],
    queryFn: () =>
      api<{ mode: string; provider: string; ready: boolean; message: string }>(
        "/payment-config",
      ),
  });
  const seconds = useCountdown(data.data?.holdUntil);
  async function pay() {
    setBusy(true);
    try {
      const o = await send<Order>("/payments", "POST", { bookingId });
      if (o.payUrl) {
        if (o.provider === "SIMULATION") navigate(o.payUrl);
        else window.location.assign(o.payUrl);
      } else navigate("/thanh-toan/ket-qua?orderId=" + o.id);
    } catch (e) {
      toast.error((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  if (data.isLoading)
    return (
      <div className="page container">
        <Loading />
      </div>
    );
  if (data.error)
    return (
      <div className="page container">
        <ErrorState error={data.error} />
      </div>
    );
  const b = data.data;
  if (!b) return null;
  const expired = seconds === 0 || !["HELD", "PENDING"].includes(b.status);
  return (
    <div className="page container narrow checkout-page">
      <div className="booking-steps">
        <span className="done">
          <Check size={14} />
          Suất & ghế
        </span>
        <i />
        <span className="active">03 · Thanh toán</span>
        <i />
        <span>04 · Nhận vé</span>
      </div>
      <div className="page-intro">
        <span className="eyebrow">ONE STEP CLOSER TO THE BIG SCREEN</span>
        <h1>Buổi hẹn sắp bắt đầu.</h1>
        <p>Kiểm tra thông tin trước khi hoàn tất đơn vé.</p>
      </div>
      <div className={`hold-countdown ${expired ? "expired" : ""}`}>
        <Clock3 size={21} />
        <div>
          <strong>
            {expired ? "Thời gian giữ ghế đã kết thúc" : "Ghế đang được giữ cho bạn"}
          </strong>
          <p>
            {expired
              ? "Vui lòng chọn lại suất và ghế."
              : "Hoàn tất thanh toán trong thời gian còn lại."}
          </p>
        </div>
        <span>
          {Math.floor(seconds / 60)
            .toString()
            .padStart(2, "0")}
          :{String(seconds % 60).padStart(2, "0")}
        </span>
      </div>
      <BookingOverview booking={b} />
      <section className="checkout-payment">
        <span className="eyebrow">PAYMENT METHOD</span>
        <h2>
          {config.data?.mode === "simulation"
            ? "Thanh toán thử nghiệm"
            : config.data?.mode === "momo"
              ? "MoMo Sandbox"
              : config.data?.mode === "vnpay"
                ? "VNPAY Sandbox"
                : config.data?.mode === "payos"
                  ? "payOS · Chuyển khoản ngân hàng"
                  : "Phương thức thanh toán"}
        </h2>
        <div className="notice warning">
          {config.data?.mode === "simulation"
            ? "SIMULATED TEST PAYMENT · Mô phỏng trong development, không thu tiền thật."
            : (config.data?.message ?? "Đang kiểm tra cấu hình thanh toán…")}
        </div>
        {config.error && (
          <ErrorState error={config.error} retry={() => void config.refetch()} />
        )}
        <p>Vé được phát hành sau khi backend xác nhận thanh toán.</p>
        <div className="form-actions">
          {!expired ? (
            <>
              <Button disabled={busy || !config.data?.ready} onClick={() => void pay()}>
                {busy ? "Đang tạo giao dịch…" : "Thanh toán " + money(b.totalAmount)}
                <ArrowRight size={16} />
              </Button>
              <Button
                variant="ghost"
                onClick={() =>
                  void send(`/bookings/${b.id}/cancel`, "POST")
                    .then(() => navigate("/dat-ve/" + b.showtime.movieId))
                    .catch((e: Error) => toast.error(e.message))
                }
              >
                Hủy giữ ghế
              </Button>
            </>
          ) : (
            <Button asChild>
              <Link to={"/dat-ve/" + b.showtime.movieId}>Chọn lại ghế</Link>
            </Button>
          )}
        </div>
      </section>
    </div>
  );
}
export function BookingOverview({ booking: b }: { booking: Booking }) {
  return (
    <section className="booking-overview">
      <img src={b.showtime.poster} alt="" />
      <div>
        <span className="eyebrow">YOUR MOVIE NIGHT</span>
        <h2>{b.showtime.movieTitle}</h2>
        <p>
          <MapPin size={14} />
          {b.showtime.cinemaName} · {b.showtime.auditoriumName}
        </p>
        <p>
          <CalendarDays size={14} />
          {showDay(b.showtime.startsAt)} · {showTime(b.showtime.startsAt)}
        </p>
        <p>
          <Armchair size={14} />
          Ghế {b.items.map((x) => x.seatLabel).join(", ")} · {b.showtime.format}
        </p>
        <div className="booking-items">
          {b.items.map((x) => (
            <span key={x.seatId}>
              {x.seatLabel} <small>{x.seatKind === "VIP" ? "VIP" : "Thường"}</small>
              <b>{money(x.price)}</b>
            </span>
          ))}
        </div>
        <div className="booking-total">
          <span>Tổng thanh toán</span>
          <strong>{money(b.totalAmount)}</strong>
        </div>
      </div>
    </section>
  );
}
const labels: Record<string, string> = {
  HELD: "Đang giữ ghế",
  PENDING: "Chờ thanh toán",
  CONFIRMED: "Đã phát hành vé",
  EXPIRED: "Hết hạn giữ ghế",
  CANCELLED: "Đã hủy",
  PAYMENT_REVIEW: "Cần hỗ trợ thanh toán",
};
export function MyTicketsPage() {
  const { bookingId } = useParams();
  const [qr, setQr] = useState<{ code: string; seat: string } | null>(null);
  const cache = useQueryClient();
  const data = useQuery({
    queryKey: bookingId ? ["booking-tickets", bookingId] : ["my-bookings"],
    queryFn: () =>
      bookingId
        ? api<Booking>("/bookings/" + bookingId).then((x) => [x])
        : api<Booking[]>("/bookings/me"),
  });
  return (
    <div className="page container tickets-page">
      <div className="page-intro heading-with-action">
        <div>
          <span className="eyebrow">YOUR NEXT CINEMA MOMENT</span>
          <h1>{bookingId ? "Vé điện tử của bạn." : "Vé của tôi."}</h1>
          <p>Mọi buổi hẹn điện ảnh, trong một không gian riêng.</p>
        </div>
        <Button variant="outline" onClick={() => void data.refetch()}>
          <RefreshCw size={15} />
          Làm mới
        </Button>
      </div>
      {data.isLoading ? (
        <Loading />
      ) : data.error ? (
        <ErrorState error={data.error} retry={() => void data.refetch()} />
      ) : data.data?.length ? (
        <div className="booking-list">
          {data.data.map((b) => (
            <article
              key={b.id}
              className={`ticket-booking ${b.status === "CONFIRMED" ? "confirmed" : ""}`}
            >
              <div className="ticket-booking-top">
                <span className="mono">{b.id}</span>
                <span
                  className={
                    "status status-" +
                    (b.status === "CONFIRMED" ? "paid" : b.status.toLowerCase())
                  }
                >
                  {labels[b.status] ?? b.status}
                </span>
              </div>
              <BookingOverview booking={b} />
              {b.status === "PAYMENT_REVIEW" && (
                <p className="notice warning">
                  Thanh toán đến sau khi ghế hết hạn giữ. Chưa phát hành vé. Liên hệ rạp
                  để đối soát; hệ thống không lấy ghế của khách khác.
                </p>
              )}
              {b.tickets.length > 0 && (
                <div className="electronic-tickets">
                  {b.tickets.map((t) => (
                    <div className="e-ticket" key={t.id}>
                      <div>
                        <span>GHẾ</span>
                        <strong>{t.seatLabel}</strong>
                        <small>
                          {t.status === "USED" ? "Đã soát vé" : "Sẵn sàng vào rạp"}
                        </small>
                      </div>
                      <button
                        onClick={() => setQr({ code: t.code, seat: t.seatLabel })}
                        aria-label={"Xem mã QR ghế " + t.seatLabel}
                      >
                        <QRCodeSVG
                          value={"CINEVERSE:TICKET:" + t.code}
                          size={80}
                          bgColor="#ffffff"
                          fgColor="#0b0e14"
                          marginSize={1}
                        />
                      </button>
                      <span className="e-ticket-stub">
                        <Ticket size={22} />
                        <small>CINEVERSE</small>
                      </span>
                    </div>
                  ))}
                </div>
              )}
              <div className="ticket-booking-actions">
                {["HELD", "PENDING"].includes(b.status) && (
                  <>
                    <Button asChild>
                      <Link to={"/dat-ve/thanh-toan/" + b.id}>
                        Hoàn tất thanh toán <ArrowRight size={15} />
                      </Link>
                    </Button>
                    <Button
                      variant="ghost"
                      onClick={() =>
                        void send(`/bookings/${b.id}/cancel`, "POST")
                          .then(() => {
                            void cache.invalidateQueries({ queryKey: ["my-bookings"] });
                            void data.refetch();
                          })
                          .catch((e: Error) => toast.error(e.message))
                      }
                    >
                      Hủy giữ ghế
                    </Button>
                  </>
                )}
                {b.status === "CONFIRMED" && (
                  <>
                    <Button variant="outline" asChild>
                      <Link to={"/ve-cua-toi/" + b.id}>
                        Chi tiết vé <ArrowRight size={15} />
                      </Link>
                    </Button>
                    <Button variant="ghost" onClick={() => window.print()}>
                      <Printer size={15} />
                      In vé
                    </Button>
                  </>
                )}
                {["EXPIRED", "CANCELLED"].includes(b.status) && (
                  <Button variant="outline" asChild>
                    <Link to={"/dat-ve/" + b.showtime.movieId}>Đặt lại</Link>
                  </Button>
                )}
                <small>
                  Đơn tạo {date(b.createdAt)} · Vé demo không có giá trị tại rạp thương
                  mại.
                </small>
              </div>
            </article>
          ))}
        </div>
      ) : (
        <Empty
          title="Chiếc ghế của bạn vẫn đang chờ"
          message="Chọn một bộ phim cho buổi hẹn điện ảnh tiếp theo."
        >
          <Button asChild>
            <Link to="/rap-chieu">Khám phá lịch chiếu</Link>
          </Button>
        </Empty>
      )}
      <Modal
        open={!!qr}
        onOpenChange={(o) => {
          if (!o) setQr(null);
        }}
        title={"Vé điện tử · Ghế " + (qr?.seat ?? "")}
      >
        <div className="ticket-qr-modal">
          {qr && (
            <QRCodeSVG
              value={"CINEVERSE:TICKET:" + qr.code}
              size={230}
              bgColor="#fff"
              fgColor="#08090d"
              marginSize={3}
            />
          )}
          <p>
            Xuất trình mã QR cho nhân viên CineVerse.
            <br />
            Mỗi vé chỉ được soát một lần.
          </p>
          <small>Vé demo cho đồ án · Không có giá trị tại rạp thương mại</small>
        </div>
      </Modal>
    </div>
  );
}
