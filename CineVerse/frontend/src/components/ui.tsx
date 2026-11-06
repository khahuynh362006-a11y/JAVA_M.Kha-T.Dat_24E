import { forwardRef, type ButtonHTMLAttributes, type ReactNode, Component } from "react";
import { Slot } from "@radix-ui/react-slot";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import {
  X,
  Film,
  RefreshCw,
  LoaderCircle,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import { clsx } from "clsx";
import { twMerge } from "tailwind-merge";
export const cn = (...values: Parameters<typeof clsx>) => twMerge(clsx(...values));
export const Button = forwardRef<
  HTMLButtonElement,
  ButtonHTMLAttributes<HTMLButtonElement> & {
    variant?: "primary" | "secondary" | "ghost" | "outline";
    asChild?: boolean;
  }
>(function Button({ className, variant = "primary", asChild = false, ...props }, ref) {
  const C = asChild ? Slot : "button";
  return <C ref={ref} className={cn("btn", `btn-${variant}`, className)} {...props} />;
});
export function Modal({
  open,
  onOpenChange,
  title,
  description,
  children,
  className,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  title: string;
  description?: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <DialogPrimitive.Root open={open} onOpenChange={onOpenChange}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="dialog-overlay" />
        <DialogPrimitive.Content
          className={cn("dialog", className)}
          aria-describedby={description ? "modal-description" : undefined}
        >
          <div className="dialog-heading">
            <div>
              <DialogPrimitive.Title>{title}</DialogPrimitive.Title>
              {description && (
                <DialogPrimitive.Description id="modal-description">
                  {description}
                </DialogPrimitive.Description>
              )}
            </div>
            <DialogPrimitive.Close className="icon-btn" aria-label="Đóng">
              <X size={20} />
            </DialogPrimitive.Close>
          </div>
          {children}
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}
export function Loading({ cards = false }: { cards?: boolean }) {
  return cards ? (
    <div className="movie-grid">
      {Array.from({ length: 6 }, (_, i) => (
        <div key={i}>
          <div className="skeleton poster-skeleton" />
          <div className="skeleton line-skeleton" />
        </div>
      ))}
    </div>
  ) : (
    <div className="loading" role="status">
      <LoaderCircle className="spin" size={28} />
      <span>Đang tải CineVerse…</span>
    </div>
  );
}
export function Empty({
  title = "Chưa có nội dung",
  message = "Khám phá một câu chuyện mới trong thư viện CineVerse.",
  children,
}: {
  title?: string;
  message?: string;
  children?: ReactNode;
}) {
  return (
    <div className="empty">
      <div className="empty-icon">
        <Film />
      </div>
      <h2>{title}</h2>
      <p>{message}</p>
      {children}
    </div>
  );
}
export function ErrorState({
  error,
  retry,
}: {
  error: Error | null;
  retry?: () => void;
}) {
  return (
    <div className="empty" role="alert">
      <RefreshCw size={30} />
      <h2>Chưa thể tải nội dung</h2>
      <p>{error?.message ?? "Vui lòng kiểm tra kết nối và thử lại."}</p>
      {retry && (
        <Button variant="outline" onClick={retry}>
          Thử lại
        </Button>
      )}
    </div>
  );
}
export function Pagination({
  page,
  total,
  onChange,
}: {
  page: number;
  total: number;
  onChange: (p: number) => void;
}) {
  if (total < 2) return null;
  return (
    <nav className="pagination" aria-label="Phân trang">
      <Button
        variant="outline"
        disabled={page === 0}
        onClick={() => onChange(page - 1)}
        aria-label="Trang trước"
      >
        <ChevronLeft size={18} />
      </Button>
      <span>
        Trang {page + 1} / {total}
      </span>
      <Button
        variant="outline"
        disabled={page + 1 >= total}
        onClick={() => onChange(page + 1)}
        aria-label="Trang sau"
      >
        <ChevronRight size={18} />
      </Button>
    </nav>
  );
}
export class ErrorBoundary extends Component<
  { children: ReactNode },
  { error: Error | null }
> {
  state: { error: Error | null } = { error: null };
  static getDerivedStateFromError(error: Error) {
    return { error };
  }
  render() {
    return this.state.error ? (
      <main className="page">
        <ErrorState
          error={new Error("Ứng dụng gặp lỗi. Vui lòng tải lại trang.")}
          retry={() => window.location.reload()}
        />
      </main>
    ) : (
      this.props.children
    );
  }
}
