import React from "react";
import ReactDOM from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Toaster } from "sonner";
import { AuthProvider } from "./AuthProvider";
import type { ComponentType } from "react";
import { ErrorBoundary } from "../components/ui";
import { ApiError } from "../lib/api";
import "@fontsource/be-vietnam-pro/400.css";
import "@fontsource/be-vietnam-pro/500.css";
import "@fontsource/be-vietnam-pro/600.css";
import "@fontsource/be-vietnam-pro/700.css";
import "../styles/global.css";
import "../styles/booking.css";
const client = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 10000,
      refetchInterval: (query) =>
        [
          "home",
          "featured",
          "movies",
          "detail",
          "cinemas",
          "showtimes",
          "genres",
          "countries",
        ].includes(String(query.queryKey[0]))
          ? 30000
          : false,
      retry: (count, error) =>
        count < 1 && (!(error instanceof ApiError) || error.status >= 500),
      refetchOnWindowFocus: true,
    },
  },
});
export function mountApp(App: ComponentType) {
  ReactDOM.createRoot(document.getElementById("root")!).render(
    <React.StrictMode>
      <ErrorBoundary>
        <QueryClientProvider client={client}>
          <AuthProvider>
            <App />
            <Toaster theme="dark" position="bottom-right" richColors closeButton />
          </AuthProvider>
        </QueryClientProvider>
      </ErrorBoundary>
    </React.StrictMode>,
  );
}
