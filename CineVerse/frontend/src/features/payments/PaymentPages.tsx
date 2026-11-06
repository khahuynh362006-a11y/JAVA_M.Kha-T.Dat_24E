import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Ticket,
  CheckCircle2,
  Clock3,
  XCircle,
  ArrowRight,
  ArrowUpRight,
  AlertTriangle,
} from "lucide-react";
import { toast } from "sonner";
import { api, date, money, send } from "../../lib/api";
import { Button, Loading, ErrorState, Empty } from "../../components/ui";
import type { Order, Booking } from "../../types";
const labels: Record<string, string> = {
  PAID: "Đã xác nhận",
  PENDING: "Chờ xác nhận",
  FAILED: "Thất bại",
  CANCELLED: "Đã hủy",
  EXPIRED: "Hết hạn",
};
export function SimulationPage() {
  const { id } = useParams(),
    navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const configuration = useQuery({
    queryKey: ["payment-config"],
    queryFn: () => api<{ mode: string }>("/payment-config"),
  });
  const data = useQuery({
    queryKey: ["order", id],
    queryFn: () => api<Order>("/payments/" + id),
  });
  async function simulate(outcome: string) {
    setBusy(true);
    try {
      await send(`/payments/${id}/simulate`, "POST", { outcome });
      navigate("/thanh-toan/ket-qua?orderId=" + id);
    } catch (e) {
      toast.error((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  if (configuration.isLoading)
    return (
      <div className="page container">
        <Loading />
      </div>
    );
  if (configuration.error)
    return (
      <div className="page container">
        <ErrorState
          error={configuration.error}
          retry={() => void configuration.refetch()}
        />
      </div>
    );
  if (configuration.data?.mode !== "simulation")
    return (
      <div className="page container">
        <Empty
          title="Phương thức thanh toán đã thay đổi"
          message="Vui lòng tạo đơn vé mới để dùng cổng thanh toán hiện tại."
        >
          <Button asChild>
            <Link to="/ve-cua-toi">Vé và đơn của tôi</Link>
          </Button>
        </Empty>
      </div>
    );
  return (
    <div className="page container narrow payment-page">
      <div className="simulation-banner">
        <AlertTriangle size={20} />
        <div>
          <strong>SIMULATED TEST PAYMENT</strong>
          <p>Thanh toán mô phỏng. Không phải MoMo Sandbox và không thu tiền thật.</p>
        </div>
      </div>
      {data.isLoading ? (
        <Loading />
      ) : data.error ? (
        <ErrorState error={data.error} />
      ) : (
        data.data && (
          <div className="payment-card">
            <Ticket size={36} className="gold" />
            <h1>Xác nhận thanh toán vé.</h1>
            <p>Chọn kết quả để kiểm tra xử lý đơn vé, ghế và email.</p>
            <OrderDetails order={data.data} />
            {data.data.provider === "SIMULATION" && data.data.status === "PENDING" ? (
              <div className="simulation-actions">
                <Button disabled={busy} onClick={() => void simulate("success")}>
                  <CheckCircle2 size={17} />
                  Mô phỏng thành công
                </Button>
                <Button
                  disabled={busy}
                  variant="outline"
                  onClick={() => void simulate("failure")}
                >
                  Mô phỏng thất bại
                </Button>
                <Button
                  disabled={busy}
                  variant="ghost"
                  onClick={() => void simulate("cancel")}
                >
                  Hủy thử nghiệm
                </Button>
              </div>
            ) : (
              <Button asChild>
                <Link to={"/thanh-toan/ket-qua?orderId=" + id}>Xem trạng thái đơn</Link>
              </Button>
            )}
          </div>
        )
      )}
    </div>
  );
}
export function PaymentResultPage() {
  const cache = useQueryClient();
  const attempted = useRef<string | null>(null);
  const [params] = useSearchParams(),
    id = params.get("orderId");
  const count = useRef(0);
  const data = useQuery({
    queryKey: ["order", id],
    queryFn: () => api<Order>("/payments/" + encodeURIComponent(id ?? "")),
    enabled: !!id,
    refetchInterval: (q) =>
      q.state.data?.status === "PENDING" && count.current++ < 30 ? 2000 : false,
  });
  const booking = useQuery({
    queryKey: ["paid-booking", data.data?.bookingId],
    queryFn: () => api<Booking>("/bookings/" + data.data?.bookingId),
    enabled: !!data.data?.bookingId && data.data.status === "PAID",
  });
  const reconciliation = useMutation({
    mutationFn: () =>
      send<Order>(`/payments/${encodeURIComponent(id ?? "")}/reconcile`, "POST"),
    onSuccess: (order) => cache.setQueryData(["order", id], order),
    onError: (error: Error) => toast.error(error.message),
  });
  const reconcile = reconciliation.mutate;
  const orderProvider = data.data?.provider;
  const orderStatus = data.data?.status;
  useEffect(() => {
    if (
      id &&
      ["VNPAY_SANDBOX", "PAYOS"].includes(orderProvider ?? "") &&
      orderStatus === "PENDING" &&
      attempted.current !== id
    ) {
      attempted.current = id;
      reconcile();
    }
  }, [id, orderProvider, orderStatus, reconcile]);
  if (!id)
    return (
      <div className="page container">
        <Empty
          title="Chưa xác định được giao dịch"
          message="Chọn đơn từ lịch sử thanh toán."
        >
          <Button asChild>
            <Link to="/lich-su-thanh-toan">Lịch sử thanh toán</Link>
          </Button>
        </Empty>
      </div>
    );
  const o = data.data,
    confirmed = booking.data?.status === "CONFIRMED",
    review = booking.data?.status === "PAYMENT_REVIEW";
  return (
    <div className="page container narrow payment-page">
      {data.isLoading ? (
        <Loading />
      ) : data.error ? (
        <ErrorState error={data.error} retry={() => void data.refetch()} />
      ) : (
        o && (
          <div className="payment-card">
            <span
              className={
                "payment-result-icon " + (confirmed ? "paid" : o.status.toLowerCase())
              }
            >
              {confirmed ? (
                <CheckCircle2 size={42} />
              ) : o.status === "PENDING" || o.status === "PAID" ? (
                <Clock3 size={42} />
              ) : (
                <XCircle size={42} />
              )}
            </span>
            <span className="eyebrow">YOUR NEXT CINEMA MOMENT</span>
            <h1>
              {confirmed
                ? "Hẹn gặp bạn tại rạp."
                : review
                  ? "Đơn vé cần được hỗ trợ."
                  : o.status === "PENDING"
                    ? "Đang chờ xác nhận."
                    : o.status === "PAID"
                      ? "Đang kiểm tra vé."
                      : "Hẹn một buổi chiếu khác."}
            </h1>
            <p>
              {confirmed
                ? "Vé điện tử đã sẵn sàng. Biên nhận được gửi qua hệ thống email."
                : review
                  ? "Thanh toán đến sau khi giữ ghế hết hạn. Chưa phát hành vé; hãy liên hệ rạp để đối soát."
                  : o.status === "PENDING"
                    ? "Backend đang chờ kết quả. Không thanh toán lại nếu bạn đã hoàn tất."
                    : "Xem đơn đặt vé để kiểm tra hoặc chọn lại suất chiếu."}
            </p>
            {o.provider === "SIMULATION" && (
              <div className="notice warning">
                SIMULATED TEST PAYMENT · Không thu tiền thật
              </div>
            )}
            <OrderDetails order={o} />
            <div className="form-actions">
              <Button asChild>
                <Link to={o.bookingId ? "/ve-cua-toi/" + o.bookingId : "/rap-chieu"}>
                  {confirmed ? "Xem vé điện tử" : "Xem đơn đặt vé"}
                  <ArrowRight size={16} />
                </Link>
              </Button>
              <Button variant="outline" asChild>
                <Link to="/lich-su-thanh-toan">Lịch sử thanh toán</Link>
              </Button>
              {o.status === "PENDING" && (
                <Button
                  variant="ghost"
                  disabled={reconciliation.isPending}
                  onClick={() =>
                    ["VNPAY_SANDBOX", "PAYOS"].includes(data.data?.provider ?? "")
                      ? reconcile()
                      : void data.refetch()
                  }
                >
                  {reconciliation.isPending ? "Đang đối soát…" : "Kiểm tra lại"}
                </Button>
              )}
            </div>
          </div>
        )
      )}
    </div>
  );
}
function OrderDetails({ order: o }: { order: Order }) {
  return (
    <dl className="order-details">
      <div>
        <dt>Mã giao dịch</dt>
        <dd className="mono">{o.id}</dd>
      </div>
      <div>
        <dt>Phim</dt>
        <dd>{o.planName}</dd>
      </div>
      <div>
        <dt>Mã đơn vé</dt>
        <dd className="mono">{o.bookingId ?? "—"}</dd>
      </div>
      <div>
        <dt>Số tiền</dt>
        <dd>{money(o.amount)}</dd>
      </div>
      <div>
        <dt>Phương thức</dt>
        <dd>
          {o.provider === "PAYOS"
            ? "payOS · Online"
            : o.provider === "SIMULATION"
              ? "Mô phỏng development"
              : o.provider === "VNPAY_SANDBOX"
                ? "VNPAY Sandbox"
                : "MoMo Sandbox"}
        </dd>
      </div>
      <div>
        <dt>Trạng thái</dt>
        <dd>
          <span className={"status status-" + o.status.toLowerCase()}>
            {labels[o.status] ?? o.status}
          </span>
        </dd>
      </div>
      <div>
        <dt>Thời gian</dt>
        <dd>{date(o.paidAt ?? o.createdAt)}</dd>
      </div>
      {o.failureReason && (
        <div>
          <dt>Thông tin</dt>
          <dd>{o.failureReason}</dd>
        </div>
      )}
    </dl>
  );
}
export function PaymentHistoryPage() {
  const data = useQuery({
    queryKey: ["payments"],
    queryFn: () => api<Order[]>("/users/me/payments"),
  });
  return (
    <div className="page container">
      <div className="page-intro">
        <span className="eyebrow">YOUR CINEMA JOURNEY</span>
        <h1>Lịch sử thanh toán.</h1>
        <p>Trạng thái giao dịch vé được xác nhận từ backend.</p>
      </div>
      {data.isLoading ? (
        <Loading />
      ) : data.error ? (
        <ErrorState error={data.error} retry={() => void data.refetch()} />
      ) : data.data?.length ? (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Mã / Thời gian</th>
                <th>Phim</th>
                <th>Số tiền</th>
                <th>Phương thức</th>
                <th>Trạng thái</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {data.data.map((o) => (
                <tr key={o.id}>
                  <td>
                    <strong className="mono">{o.id.slice(0, 16)}…</strong>
                    <small>{date(o.createdAt)}</small>
                  </td>
                  <td>{o.planName}</td>
                  <td>{money(o.amount)}</td>
                  <td>
                    <span className="simulation-tag">
                      {o.provider === "PAYOS"
                        ? "payOS · Online"
                        : o.provider === "SIMULATION"
                          ? "MÔ PHỎNG"
                          : o.provider === "VNPAY_SANDBOX"
                            ? "VNPAY Sandbox"
                            : "MoMo Sandbox"}
                    </span>
                  </td>
                  <td>
                    <span className={"status status-" + o.status.toLowerCase()}>
                      {labels[o.status] ?? o.status}
                    </span>
                  </td>
                  <td>
                    <Link
                      to={"/thanh-toan/ket-qua?orderId=" + o.id}
                      aria-label={"Xem giao dịch " + o.id}
                    >
                      <ArrowUpRight size={20} />
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <Empty title="Chưa có giao dịch vé" message="Chọn phim cho buổi chiếu tiếp theo.">
          <Button asChild>
            <Link to="/rap-chieu">Tìm suất chiếu</Link>
          </Button>
        </Empty>
      )}
    </div>
  );
}
