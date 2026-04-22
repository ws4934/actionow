"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";

export type Theme = "light" | "dark" | "system";
type ResolvedTheme = "light" | "dark";

interface ThemeProviderProps {
  children: ReactNode;
}

interface ThemeContextValue {
  theme: Theme;
  resolvedTheme: ResolvedTheme;
  systemTheme: ResolvedTheme;
  setTheme: (theme: Theme) => void;
}

const STORAGE_KEY = "theme";
const MEDIA_QUERY = "(prefers-color-scheme: dark)";
const DEFAULT_THEME: Theme = "system";

const themeScript = `(function(){try{var stored=localStorage.getItem("${STORAGE_KEY}");var theme=stored==="light"||stored==="dark"||stored==="system"?stored:"${DEFAULT_THEME}";var resolved=theme==="system"&&window.matchMedia("${MEDIA_QUERY}").matches?"dark":theme==="system"?"light":theme;var root=document.documentElement;root.classList.remove("light","dark");root.classList.add(resolved);root.setAttribute("data-theme",resolved);root.style.colorScheme=resolved;}catch(e){}})();`;

const ThemeContext = createContext<ThemeContextValue | null>(null);

function parseTheme(value: string | null): Theme {
  if (value === "light" || value === "dark" || value === "system") {
    return value;
  }
  return DEFAULT_THEME;
}

function getSystemTheme() {
  return window.matchMedia(MEDIA_QUERY).matches ? "dark" : "light";
}

function getInitialTheme() {
  if (typeof window === "undefined") {
    return DEFAULT_THEME;
  }

  return parseTheme(window.localStorage.getItem(STORAGE_KEY));
}

function getInitialSystemTheme(): ResolvedTheme {
  if (typeof window === "undefined") {
    return "light";
  }

  return getSystemTheme();
}

function resolveTheme(theme: Theme, systemTheme: ResolvedTheme): ResolvedTheme {
  return theme === "system" ? systemTheme : theme;
}

function applyTheme(theme: Theme, systemTheme: ResolvedTheme) {
  const resolvedTheme = resolveTheme(theme, systemTheme);
  const root = document.documentElement;

  root.classList.remove("light", "dark");
  root.classList.add(resolvedTheme);
  root.setAttribute("data-theme", resolvedTheme);
  root.style.colorScheme = resolvedTheme;
}

export function ThemeProvider({ children }: ThemeProviderProps) {
  const [theme, setThemeState] = useState<Theme>(getInitialTheme);
  const [systemTheme, setSystemTheme] = useState<ResolvedTheme>(getInitialSystemTheme);
  const themeRef = useRef<Theme>(theme);

  useEffect(() => {
    themeRef.current = theme;
  }, [theme]);

  useEffect(() => {
    const mediaQuery = window.matchMedia(MEDIA_QUERY);
    applyTheme(themeRef.current, mediaQuery.matches ? "dark" : "light");

    const handleSystemThemeChange = (event: MediaQueryListEvent) => {
      const nextSystemTheme: ResolvedTheme = event.matches ? "dark" : "light";
      setSystemTheme(nextSystemTheme);
      applyTheme(themeRef.current, nextSystemTheme);
    };

    const handleStorage = (event: StorageEvent) => {
      if (event.key !== STORAGE_KEY) {
        return;
      }

      const nextTheme = parseTheme(event.newValue);
      const nextSystemTheme = getSystemTheme();

      themeRef.current = nextTheme;
      setThemeState(nextTheme);
      setSystemTheme(nextSystemTheme);
      applyTheme(nextTheme, nextSystemTheme);
    };

    mediaQuery.addEventListener("change", handleSystemThemeChange);
    window.addEventListener("storage", handleStorage);

    return () => {
      mediaQuery.removeEventListener("change", handleSystemThemeChange);
      window.removeEventListener("storage", handleStorage);
    };
  }, []);

  const setTheme = useCallback((nextTheme: Theme) => {
    const nextSystemTheme = getSystemTheme();

    themeRef.current = nextTheme;
    setThemeState(nextTheme);
    setSystemTheme(nextSystemTheme);
    window.localStorage.setItem(STORAGE_KEY, nextTheme);
    applyTheme(nextTheme, nextSystemTheme);
  }, []);

  const value = useMemo<ThemeContextValue>(
    () => ({
      theme,
      resolvedTheme: resolveTheme(theme, systemTheme),
      systemTheme,
      setTheme,
    }),
    [setTheme, systemTheme, theme]
  );

  return (
    <>
      <script dangerouslySetInnerHTML={{ __html: themeScript }} />
      <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
    </>
  );
}

export function useTheme() {
  const context = useContext(ThemeContext);

  if (!context) {
    throw new Error("useTheme must be used within ThemeProvider");
  }

  return context;
}
