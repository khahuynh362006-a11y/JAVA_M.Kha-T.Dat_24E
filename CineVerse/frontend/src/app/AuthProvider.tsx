import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { api, listenSession, refreshSession, send, setSession } from "../lib/api";
import type { Session, User } from "../types";
import { useQueryClient } from "@tanstack/react-query";
interface Auth {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string, remember: boolean) => Promise<void>;
  logout: () => Promise<void>;
  reload: () => Promise<void>;
}
const Context = createContext<Auth | null>(null);
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null),
    [loading, setLoading] = useState(true);
  const cache = useQueryClient();
  useEffect(() => {
    let live = true;
    listenSession((s) => {
      if (live) setUser(s?.user ?? null);
    });
    void refreshSession().finally(() => {
      if (live) setLoading(false);
    });
    return () => {
      live = false;
      listenSession(null);
    };
  }, []);
  const value: Auth = {
    user,
    loading,
    async login(email, password, remember) {
      const s = await send<Session>("/auth/login", "POST", {
        email,
        password,
        remember,
      });
      setSession(s);
      cache.clear();
    },
    async logout() {
      await send("/auth/logout", "POST");
      setSession(null);
      cache.clear();
    },
    async reload() {
      setUser(await api<User>("/users/me"));
    },
  };
  return <Context.Provider value={value}>{children}</Context.Provider>;
}
export function useAuth() {
  const c = useContext(Context);
  if (!c) throw new Error("AuthProvider missing");
  return c;
}
