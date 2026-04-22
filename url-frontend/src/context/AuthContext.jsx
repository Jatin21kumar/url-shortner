import { createContext, useContext, useMemo, useState } from "react";
import api from "../api/axios";

const AuthContext = createContext(null);

function extractErrorMessage(error, fallback) {
  return error?.response?.data?.message || error?.response?.data || fallback;
}

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem("token"));
  const [username, setUsername] = useState(() => localStorage.getItem("username") || "");

  const login = async (credentials) => {
    try {
      const response = await api.post("/auth/login", credentials);
      const nextToken = response?.data?.token;
      const nextUsername = response?.data?.username || "";

      if (!nextToken) {
        return {
          success: false,
          message: "Login response did not include a token.",
        };
      }

      localStorage.setItem("token", nextToken);
      localStorage.setItem("username", nextUsername);
      setToken(nextToken);
      setUsername(nextUsername);
      return { success: true };
    } catch (error) {
      return {
        success: false,
        message: extractErrorMessage(error, "Login failed. Please try again."),
      };
    }
  };

  const register = async (payload) => {
    try {
      await api.post("/auth/register", payload);
      return { success: true };
    } catch (error) {
      return {
        success: false,
        message: extractErrorMessage(error, "Registration failed. Please try again."),
      };
    }
  };

  const logout = () => {
    localStorage.removeItem("token");
    localStorage.removeItem("username");
    setToken(null);
    setUsername("");
  };

  const value = useMemo(
    () => ({
      token,
      username,
      isAuthenticated: Boolean(token),
      login,
      register,
      logout,
    }),
    [token, username]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
