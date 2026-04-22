import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

function Navbar() {
  const { isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate("/login");
  };

  return (
    <nav className="border-b bg-white">
      <div className="mx-auto flex w-full max-w-5xl items-center justify-between px-4 py-3">
        <Link to="/dashboard" className="text-lg font-semibold text-slate-900">
          URL Shortener
        </Link>

        {isAuthenticated ? (
          <button
            onClick={handleLogout}
            className="rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-700"
          >
            Logout
          </button>
        ) : (
          <div className="flex items-center gap-3 text-sm">
            <Link className="text-slate-600 hover:text-slate-900" to="/login">
              Login
            </Link>
            <Link className="text-slate-600 hover:text-slate-900" to="/register">
              Register
            </Link>
          </div>
        )}
      </div>
    </nav>
  );
}

export default Navbar;
