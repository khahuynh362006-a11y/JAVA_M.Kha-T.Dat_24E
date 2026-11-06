import { useState } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  BarChart,
  Bar,
} from "recharts";
import { Users, Film, Eye, Crown, ArrowUpRight, Receipt, RefreshCw } from "lucide-react";
import { api, money, date } from "../../lib/api";
import { Button, Loading, ErrorState } from "../../components/ui";
import type { Dashboard } from "../../types";
export default function DashboardPage() {
  const [days, setDays] = useState(30);
  const data = useQuery({
    queryKey: ["dashboard", days],
    queryFn: () => api<Dashboard>(`/admin/dashboard?days=${days}`),
  });
  if (data.isLoading) return <Loading />;
  if (data.error)
    return <ErrorState error={data.error} retry={() => void data.refetch()} />;
  const d = data.data;
  if (!d) return null;
  return (
    <>
      <div className="admin-page-heading">
        <div>
          <span className="eyebrow">A BIG PICTURE OF YOUR UNIVERSE</span>
          <h1>
            Tổng quan hệ thống<span className="section-dot">.</span>
          </h1>
          <p>Mọi con số được tổng hợp từ hoạt động trong database.</p>
        </div>
        <div>
          <select
            aria-label="Khoảng thời gian phân tích"
            value={days}
            onChange={(e) => setDays(Number(e.target.value))}
          >
            <option value={7}>7 ngày qua</option>
            <option value={30}>30 ngày qua</option>
            <option value={90}>90 ngày qua</option>
          </select>
          <Button variant="outline" onClick={() => void data.refetch()}>
            <RefreshCw size={15} />
            Làm mới
          </Button>
        </div>
      </div>
      <div className="admin-metrics">
        {[
          [Users, "Tổng thành viên", d.users, "users"],
          [Crown, "Suất chiếu mở bán", d.showtimes, "showtimes"],
          [Film, "Phim trong thư viện", d.movies + d.series, "movies"],
          [Eye, "Vé đã phát hành", d.ticketsSold, "tickets"],
        ].map(([Icon, title, value, url]) => {
          const I = Icon as typeof Users;
          return (
            <Link to={"/admin/" + url} className="metric-card" key={String(title)}>
              <div>
                <span>{String(title)}</span>
                <I size={18} />
              </div>
              <strong>{Number(value).toLocaleString("vi-VN")}</strong>
              <span className="metric-caption">
                Xem chi tiết <ArrowUpRight size={14} />
              </span>
            </Link>
          );
        })}
      </div>
      <div className="admin-revenue-summary">
        <div>
          <Receipt size={22} />
          <div>
            <span>THANH TOÁN ONLINE · PAYOS</span>
            <strong>{money(d.payosRevenue)}</strong>
            <small>Đơn đã đối soát đủ tiền từ payOS</small>
          </div>
        </div>
        <div>
          <Receipt size={22} />
          <div>
            <span>THANH TOÁN VÉ SANDBOX</span>
            <strong>{money(d.sandboxRevenue)}</strong>
            <small>VNPAY/MoMo Test · Không phải doanh thu tiền thật</small>
          </div>
        </div>
        <div>
          <Crown size={22} />
          <div>
            <span>GIÁ TRỊ THANH TOÁN MÔ PHỎNG</span>
            <strong>{money(d.simulationRevenue)}</strong>
            <small>SIMULATION · Không phải doanh thu tiền thật</small>
          </div>
        </div>
        <div>
          <span>TRẠNG THÁI ĐƠN HÀNG</span>
          <strong>
            {d.paidOrders} <small>đã xác nhận</small>
          </strong>
          <small>
            {d.failedOrders} thất bại · {d.ticketsSold} vé điện tử
          </small>
        </div>
      </div>
      <div className="dashboard-charts">
        <section className="admin-box revenue-chart">
          <div className="box-title">
            <div>
              <h2>Dòng giao dịch theo thời gian</h2>
              <p>payOS, Sandbox và dữ liệu mô phỏng được thống kê riêng.</p>
            </div>
            <span className="chart-legend">
              <i />
              Mô phỏng <b />
              Sandbox · payOS màu xanh
            </span>
          </div>
          <div className="chart-container">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={d.chart}>
                <defs>
                  <linearGradient id="simulation" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0" stopColor="#8b5cf6" stopOpacity={0.28} />
                    <stop offset="1" stopColor="#8b5cf6" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid stroke="#ffffff08" vertical={false} />
                <XAxis
                  dataKey="day"
                  tickFormatter={(v) => String(v).slice(5)}
                  stroke="#727887"
                  fontSize={10}
                  tickLine={false}
                  axisLine={false}
                  minTickGap={35}
                />
                <YAxis
                  stroke="#727887"
                  fontSize={10}
                  tickFormatter={(v) => `${Number(v) / 1000}k`}
                  tickLine={false}
                  axisLine={false}
                  width={40}
                />
                <Tooltip
                  contentStyle={{
                    background: "#151821",
                    border: "1px solid #ffffff15",
                    borderRadius: 10,
                    color: "#f7f8fa",
                  }}
                  formatter={(value, name) => [
                    money(Number(value)),
                    name === "simulation"
                      ? "Mô phỏng"
                      : name === "payos"
                        ? "payOS"
                        : "Sandbox",
                  ]}
                />
                <Area
                  type="monotone"
                  dataKey="simulation"
                  stroke="#9a7ae9"
                  strokeWidth={2}
                  fill="url(#simulation)"
                />
                <Area
                  type="monotone"
                  dataKey="revenue"
                  stroke="#eabf78"
                  strokeWidth={2}
                  fill="none"
                />
                <Area
                  type="monotone"
                  dataKey="payos"
                  stroke="#4cd4a1"
                  strokeWidth={2}
                  fill="none"
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </section>
        <section className="admin-box">
          <div className="box-title">
            <div>
              <h2>Những phim được đặt vé</h2>
              <p>Số vé đã được phát hành</p>
            </div>
            <Eye size={19} />
          </div>
          <div className="top-movies">
            {d.topMovies.map((m, i) => (
              <div key={String(m.id)}>
                <span>{String(i + 1).padStart(2, "0")}</span>
                <div>
                  <strong>{String(m.title)}</strong>
                  <progress
                    max={Math.max(1, ...d.topMovies.map((x) => Number(x.views)))}
                    value={Number(m.views)}
                  />
                </div>
                <b>{Number(m.views)}</b>
              </div>
            ))}
          </div>
        </section>
      </div>
      <div className="dashboard-bottom">
        <section className="admin-box">
          <div className="box-title">
            <div>
              <h2>Giao dịch gần đây</h2>
              <p>Dữ liệu được cập nhật khi backend xác nhận.</p>
            </div>
            <Link className="text-link" to="/admin/payments">
              Tất cả <ArrowUpRight size={14} />
            </Link>
          </div>
          {d.recentOrders.length ? (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Phim / Thời gian</th>
                    <th>Giá trị</th>
                    <th>Nguồn</th>
                    <th>Trạng thái</th>
                  </tr>
                </thead>
                <tbody>
                  {d.recentOrders.map((o) => (
                    <tr key={String(o.id)}>
                      <td>
                        <strong>{String(o.plan_name)}</strong>
                        <small>{date(String(o.created_at))}</small>
                      </td>
                      <td>{money(Number(o.amount))}</td>
                      <td>
                        <span className="simulation-tag">
                          {o.provider === "PAYOS"
                            ? "payOS"
                            : o.provider === "SIMULATION"
                              ? "MÔ PHỎNG"
                              : "SANDBOX"}
                        </span>
                      </td>
                      <td>
                        <span
                          className={"status status-" + String(o.status).toLowerCase()}
                        >
                          {String(o.status)}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="admin-empty">
              Chưa có giao dịch. Dữ liệu sẽ xuất hiện sau lần thanh toán đầu tiên.
            </div>
          )}
        </section>
        <section className="admin-box">
          <div className="box-title">
            <div>
              <h2>Thành viên mới</h2>
              <p>Số tài khoản đăng ký trong kỳ</p>
            </div>
            <Users size={18} />
          </div>
          <div className="chart-container small">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={d.chart}>
                <CartesianGrid stroke="#ffffff08" vertical={false} />
                <XAxis
                  dataKey="day"
                  tickFormatter={(v) => String(v).slice(5)}
                  fontSize={10}
                  stroke="#727887"
                  axisLine={false}
                  tickLine={false}
                  minTickGap={35}
                />
                <YAxis
                  allowDecimals={false}
                  fontSize={10}
                  stroke="#727887"
                  axisLine={false}
                  tickLine={false}
                  width={22}
                />
                <Tooltip
                  contentStyle={{
                    background: "#151821",
                    border: "1px solid #ffffff15",
                    borderRadius: 10,
                  }}
                />
                <Bar
                  dataKey="registrations"
                  name="Thành viên mới"
                  fill="#59677e"
                  radius={[3, 3, 0, 0]}
                />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </section>
      </div>
    </>
  );
}
