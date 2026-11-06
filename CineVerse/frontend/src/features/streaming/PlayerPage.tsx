import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Play,
  Pause,
  Volume2,
  VolumeX,
  Maximize,
  Minimize,
  ArrowLeft,
  SkipForward,
  SkipBack,
  PictureInPicture2,
  RotateCcw,
  LoaderCircle,
  Settings2,
  Crown,
} from "lucide-react";
import { toast } from "sonner";
import { api, ApiError, send } from "../../lib/api";
import { Button, Loading } from "../../components/ui";
import type { Playback, History, Detail } from "../../types";
export default function PlayerPage() {
  const { movieId, episodeId } = useParams();
  const nav = useNavigate();
  const playback = useQuery({
    queryKey: ["playback", movieId, episodeId],
    queryFn: () =>
      api<Playback>(
        episodeId ? `/episodes/${episodeId}/playback` : `/movies/${movieId}/playback`,
      ),
    retry: false,
  });
  const detail = useQuery({
    queryKey: ["detail", playback.data?.slug],
    queryFn: () => api<Detail>("/movies/" + playback.data?.slug),
    enabled: !!playback.data,
  });
  const history = useQuery({
    queryKey: ["history"],
    queryFn: () => api<History[]>("/watch-history"),
  });
  const refreshPlayback = playback.refetch;
  const renew = useCallback(() => {
    void refreshPlayback();
  }, [refreshPlayback]);
  const all = detail.data?.seasons.flatMap((s) => s.episodes) ?? [];
  const index = all.findIndex((e) => e.id === Number(episodeId));
  const next = all[index + 1],
    previous = index > 0 ? all[index - 1] : undefined;
  if (playback.isLoading)
    return (
      <div className="page container">
        <Loading />
      </div>
    );
  if (playback.error) {
    const e = playback.error;
    return (
      <div className="page container">
        <div className="paywall">
          <div className="premium-icon">
            <Crown size={38} />
          </div>
          <span className="eyebrow">CINEVERSE VIP</span>
          <h1>
            {e instanceof ApiError && e.status === 402
              ? "Câu chuyện đặc biệt. Dành cho bạn."
              : "Chưa thể bắt đầu bộ phim"}
          </h1>
          <p>{e.message}</p>
          <div className="hero-cta">
            <Button asChild>
              <Link
                to={
                  e instanceof ApiError && e.status === 401
                    ? "/dang-nhap?next=" + encodeURIComponent(location.pathname)
                    : "/vip"
                }
              >
                {e instanceof ApiError && e.status === 401
                  ? "Đăng nhập"
                  : "Khám phá gói VIP"}
              </Link>
            </Button>
            <Button variant="outline" onClick={() => nav(-1)}>
              Quay lại
            </Button>
          </div>
        </div>
      </div>
    );
  }
  const p = playback.data;
  if (!p) return null;
  const saved = history.data?.find(
    (h) => h.movie.id === p.movieId && h.episodeId === p.episodeId && !h.completed,
  );
  return (
    <div className="player-page container">
      <Link to={`/phim/${p.slug}`} className="back-link">
        <ArrowLeft size={17} />
        Trở về bộ phim
      </Link>
      <div className="player-title">
        <div>
          <span className="eyebrow">NOW PLAYING ON CINEVERSE</span>
          <h1>{p.title}</h1>
        </div>
        <span className="quality-badge">HD · DEMO</span>
      </div>
      <VideoPlayer
        key={p.episodeId ?? p.movieId}
        playback={p}
        resume={saved?.positionSeconds ?? 0}
        next={next ? () => nav("/xem-tap/" + next.id) : undefined}
        previous={previous ? () => nav("/xem-tap/" + previous.id) : undefined}
        refresh={renew}
      />
      <div className="player-under">
        <div>
          <strong>Một trải nghiệm điện ảnh trọn vẹn.</strong>
          <p>{p.license}</p>
        </div>
        <span>SPACE Phát / Dừng · ← → Tua 10s · F Toàn màn hình · M Tắt tiếng</span>
      </div>
      {all.length > 0 && (
        <section className="episodes-section">
          <h2>Tiếp tục câu chuyện</h2>
          <div className="episode-pills">
            {all.map((e) => (
              <Link
                key={e.id}
                className={e.id === p.episodeId ? "active" : ""}
                to={`/xem-tap/${e.id}`}
              >
                Tập {e.number} · {e.title}
                {e.vip && <Crown size={13} />}
              </Link>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
const time = (n: number) => {
  n = Number.isFinite(n) ? Math.floor(n) : 0;
  return `${Math.floor(n / 60)}:${String(n % 60).padStart(2, "0")}`;
};
export function VideoPlayer({
  playback: p,
  trailer = false,
  resume = 0,
  next,
  previous,
  refresh,
}: {
  playback: Playback;
  trailer?: boolean;
  resume?: number;
  next?: () => void;
  previous?: () => void;
  refresh?: () => void;
}) {
  const video = useRef<HTMLVideoElement>(null),
    container = useRef<HTMLDivElement>(null),
    hideTimer = useRef<ReturnType<typeof setTimeout> | null>(null),
    lastSaved = useRef(0),
    didResume = useRef(false);
  const [playing, setPlaying] = useState(false),
    [position, setPosition] = useState(0),
    [duration, setDuration] = useState(0),
    [volume, setVolume] = useState(0.8),
    [muted, setMuted] = useState(false),
    [buffering, setBuffering] = useState(false),
    [error, setError] = useState(false),
    [visible, setVisible] = useState(true),
    [settings, setSettings] = useState(false),
    [speed, setSpeed] = useState(1),
    [subtitles, setSubtitles] = useState(false),
    [fullscreen, setFullscreen] = useState(false),
    [autoplay, setAutoplay] = useState(false);
  const cache = useQueryClient();
  const save = useCallback(() => {
    const v = video.current;
    if (
      trailer ||
      !v ||
      !Number.isFinite(v.duration) ||
      !v.duration ||
      v.currentTime < 0.1
    )
      return;
    lastSaved.current = Date.now();
    void send("/watch-history/progress", "PUT", {
      movieId: p.movieId,
      episodeId: p.episodeId,
      positionSeconds: v.currentTime,
      durationSeconds: v.duration,
    })
      .then(() => cache.invalidateQueries({ queryKey: ["history"] }))
      .catch((e: Error) => toast.error("Không lưu được tiến độ: " + e.message));
  }, [trailer, p.movieId, p.episodeId, cache]);
  const show = () => {
    setVisible(true);
    if (hideTimer.current) clearTimeout(hideTimer.current);
    hideTimer.current = setTimeout(() => {
      if (video.current && !video.current.paused) setVisible(false);
    }, 3000);
  };
  const toggle = useCallback(() => {
    const v = video.current;
    if (!v) return;
    if (v.paused) void v.play().catch(() => setError(true));
    else v.pause();
  }, []);
  const full = useCallback(() => {
    if (document.fullscreenElement)
      void document
        .exitFullscreen()
        .catch(() => toast.error("Không thể thoát toàn màn hình."));
    else
      void container.current
        ?.requestFullscreen()
        .catch(() => toast.error("Trình duyệt chưa hỗ trợ toàn màn hình."));
  }, []);
  useEffect(() => {
    const v = video.current;
    if (v) v.volume = 0.8;
    const key = (e: KeyboardEvent) => {
      if (
        e.target instanceof HTMLInputElement ||
        e.target instanceof HTMLSelectElement ||
        e.target instanceof HTMLTextAreaElement
      )
        return;
      const v = video.current;
      if (!v) return;
      if (
        [" ", "ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown", "f", "m"].includes(e.key)
      ) {
        e.preventDefault();
        setVisible(true);
      }
      switch (e.key) {
        case " ":
          toggle();
          break;
        case "ArrowLeft":
          v.currentTime = Math.max(0, v.currentTime - 10);
          break;
        case "ArrowRight":
          v.currentTime = Math.min(v.duration || 0, v.currentTime + 10);
          break;
        case "ArrowUp":
          v.volume = Math.min(1, v.volume + 0.1);
          break;
        case "ArrowDown":
          v.volume = Math.max(0, v.volume - 0.1);
          break;
        case "f":
          full();
          break;
        case "m":
          v.muted = !v.muted;
          break;
      }
    };
    const fs = () => setFullscreen(!!document.fullscreenElement);
    const visibility = () => {
      if (document.hidden) save();
    };
    document.addEventListener("keydown", key);
    document.addEventListener("fullscreenchange", fs);
    document.addEventListener("visibilitychange", visibility);
    return () => {
      save();
      document.removeEventListener("keydown", key);
      document.removeEventListener("fullscreenchange", fs);
      document.removeEventListener("visibilitychange", visibility);
      if (hideTimer.current) clearTimeout(hideTimer.current);
    };
  }, [toggle, full, save]);
  useEffect(() => {
    if (!refresh) return;
    const timer = setInterval(
      () => {
        save();
        refresh();
        didResume.current = false;
      },
      12 * 60 * 1000,
    );
    return () => clearInterval(timer);
  }, [refresh, save]);
  function ready() {
    const v = video.current;
    if (!v) return;
    setDuration(v.duration);
    setBuffering(false);
    if (!didResume.current && resume > 0 && resume < v.duration - 5) {
      v.currentTime = resume;
      toast.info("Tiếp tục từ " + time(resume));
    }
    didResume.current = true;
  }
  function subtitle(on: boolean) {
    setSubtitles(on);
    const tracks = video.current?.textTracks;
    if (tracks)
      for (let i = 0; i < tracks.length; i++)
        tracks[i].mode = on ? "showing" : "disabled";
  }
  return (
    <div
      ref={container}
      className={`video-player ${visible || !playing || settings ? "controls-visible" : ""}`}
      onMouseMove={show}
      onTouchStart={show}
      onFocusCapture={() => setVisible(true)}
    >
      <video
        ref={video}
        src={p.url}
        poster={p.poster}
        preload="metadata"
        playsInline
        onClick={toggle}
        onLoadedMetadata={ready}
        onCanPlay={() => setBuffering(false)}
        onWaiting={() => setBuffering(true)}
        onPlaying={() => {
          setBuffering(false);
          setPlaying(true);
          show();
        }}
        onPause={() => {
          setPlaying(false);
          save();
        }}
        onVolumeChange={() => {
          if (video.current) {
            setVolume(video.current.volume);
            setMuted(video.current.muted);
          }
        }}
        onTimeUpdate={() => {
          if (video.current) {
            setPosition(video.current.currentTime);
            if (Date.now() - lastSaved.current > 15000) save();
          }
        }}
        onEnded={() => {
          setPlaying(false);
          save();
          if (autoplay && next) next();
        }}
        onError={() => {
          setError(true);
          setBuffering(false);
        }}
      >
        {p.subtitleUrl && (
          <track kind="subtitles" src={p.subtitleUrl} srcLang="vi" label="Tiếng Việt" />
        )}
      </video>
      {error ? (
        <div className="player-state">
          <h3>Video tạm thời chưa phát được</h3>
          <p>Kiểm tra media hoặc làm mới quyền truy cập.</p>
          <Button
            variant="outline"
            onClick={() => {
              setError(false);
              if (refresh) refresh();
              else video.current?.load();
            }}
          >
            <RotateCcw size={16} />
            Thử lại
          </Button>
        </div>
      ) : buffering ? (
        <div className="player-center">
          <LoaderCircle size={40} className="spin" />
        </div>
      ) : (
        !playing && (
          <button className="player-big-play" onClick={toggle} aria-label="Phát video">
            <Play size={32} fill="currentColor" />
          </button>
        )
      )}
      <div className="player-top">
        <span>{p.title}</span>
        <small>VIDEO DEMO · NGUỒN MỞ</small>
      </div>
      <div className="video-controls">
        <label className="seek-label">
          <span className="sr-only">Tiến độ video</span>
          <input
            type="range"
            min="0"
            max={duration || 1}
            step=".1"
            value={position}
            onChange={(e) => {
              if (video.current) {
                video.current.currentTime = Number(e.target.value);
                setPosition(Number(e.target.value));
              }
            }}
            style={
              {
                "--progress": `${duration ? (position / duration) * 100 : 0}%`,
              } as React.CSSProperties
            }
          />
        </label>
        <div className="control-row">
          <div>
            <button aria-label={playing ? "Tạm dừng" : "Phát video"} onClick={toggle}>
              {playing ? (
                <Pause size={21} fill="currentColor" />
              ) : (
                <Play size={21} fill="currentColor" />
              )}
            </button>
            {previous && (
              <button
                aria-label="Tập trước"
                onClick={() => {
                  save();
                  previous();
                }}
              >
                <SkipBack size={19} />
              </button>
            )}
            {next && (
              <button
                aria-label="Tập tiếp theo"
                onClick={() => {
                  save();
                  next();
                }}
              >
                <SkipForward size={19} />
              </button>
            )}
            <button
              aria-label={muted ? "Bật tiếng" : "Tắt tiếng"}
              onClick={() => {
                if (video.current) video.current.muted = !muted;
              }}
            >
              {muted || volume === 0 ? <VolumeX size={21} /> : <Volume2 size={21} />}
            </button>
            <input
              className="volume-slider"
              aria-label="Âm lượng"
              type="range"
              min="0"
              max="1"
              step=".05"
              value={muted ? 0 : volume}
              onChange={(e) => {
                if (video.current) {
                  video.current.volume = Number(e.target.value);
                  video.current.muted = false;
                }
              }}
            />
            <span className="player-time">
              {time(position)} <i>/</i> {time(duration)}
            </span>
          </div>
          <div>
            <button
              aria-label="Cài đặt trình phát"
              aria-expanded={settings}
              onClick={() => setSettings(!settings)}
            >
              <Settings2 size={20} />
            </button>
            {document.pictureInPictureEnabled && (
              <button
                className="pip-btn"
                aria-label="Hình trong hình"
                onClick={() => {
                  const v = video.current;
                  if (v)
                    void (
                      document.pictureInPictureElement
                        ? document.exitPictureInPicture()
                        : v.requestPictureInPicture()
                    ).catch(() =>
                      toast.error("Hình trong hình chưa sẵn sàng. Hãy phát video trước."),
                    );
                }}
              >
                <PictureInPicture2 size={20} />
              </button>
            )}
            <button
              aria-label={fullscreen ? "Thoát toàn màn hình" : "Toàn màn hình"}
              onClick={full}
            >
              {fullscreen ? <Minimize size={20} /> : <Maximize size={20} />}
            </button>
          </div>
        </div>
      </div>
      {settings && (
        <div className="player-settings">
          <label>
            Tốc độ phát
            <select
              value={speed}
              onChange={(e) => {
                const s = Number(e.target.value);
                setSpeed(s);
                if (video.current) video.current.playbackRate = s;
              }}
            >
              {[0.5, 0.75, 1, 1.25, 1.5, 2].map((s) => (
                <option key={s} value={s}>
                  {s}× {s === 1 ? "Bình thường" : ""}
                </option>
              ))}
            </select>
          </label>
          {p.subtitleUrl && (
            <label>
              Phụ đề
              <select
                value={subtitles ? "vi" : "off"}
                onChange={(e) => subtitle(e.target.value === "vi")}
              >
                <option value="off">Tắt</option>
                <option value="vi">Tiếng Việt (demo)</option>
              </select>
            </label>
          )}
          {next && (
            <label className="checkbox-label">
              <input
                type="checkbox"
                checked={autoplay}
                onChange={(e) => setAutoplay(e.target.checked)}
              />
              Tự phát tập tiếp
            </label>
          )}
          <small>Chất lượng theo nguồn MP4 gốc.</small>
          <button className="text-link" onClick={() => setSettings(false)}>
            Đóng cài đặt
          </button>
        </div>
      )}
    </div>
  );
}
