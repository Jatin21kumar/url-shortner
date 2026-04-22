import { useEffect, useState } from "react";
import api, { extractError } from "../api/axios";
import UrlCard from "../components/UrlCard";

function Dashboard() {
  const [longUrl, setLongUrl] = useState("");
  const [urls, setUrls] = useState([]);
  const [urlsLoading, setUrlsLoading] = useState(true);
  const [loading, setLoading] = useState(false);
  const [deletingId, setDeletingId] = useState("");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const fetchUrls = async () => {
    try {
      setUrlsLoading(true);
      const response = await api.get("/url/my");
      setUrls(Array.isArray(response.data) ? response.data : []);
    } catch (fetchError) {
      setError(extractError(fetchError));
    } finally {
      setUrlsLoading(false);
    }
  };

  useEffect(() => {
    fetchUrls();
  }, []);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError("");
    setSuccess("");

    if (!longUrl.trim()) {
      setError("Please enter a URL.");
      return;
    }

    try {
      setLoading(true);
      await api.post("/url/shorten", { longUrl: longUrl.trim() });
      setLongUrl("");
      setSuccess("URL shortened successfully.");
      await fetchUrls();
    } catch (submitError) {
      setError(extractError(submitError));
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (identifier) => {
    try {
      setError("");
      setSuccess("");
      setDeletingId(String(identifier));
      await api.delete(`/url/${identifier}`);
      setUrls((prev) => prev.filter((item) => String(item.id) !== String(identifier)));
      setSuccess("URL deleted.");
    } catch (deleteError) {
      setError(extractError(deleteError));
    } finally {
      setDeletingId("");
    }
  };

  return (
    <div className="mx-auto w-full max-w-5xl px-4 py-8">
      <h1 className="mb-6 text-2xl font-semibold text-slate-900">Dashboard</h1>

      <form onSubmit={handleSubmit} className="mb-6 rounded-lg border bg-white p-4 shadow-sm">
        <label className="mb-2 block text-sm text-slate-600">Enter long URL</label>
        <div className="flex flex-col gap-3 sm:flex-row">
          <input
            type="url"
            placeholder="https://example.com"
            value={longUrl}
            onChange={(event) => setLongUrl(event.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 outline-none focus:border-slate-500"
            required
          />
          <button
            type="submit"
            disabled={loading}
            className="rounded-md bg-slate-900 px-4 py-2 text-white hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-70"
          >
            {loading ? (
              <span className="inline-flex items-center gap-2">
                <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                Shortening...
              </span>
            ) : (
              "Shorten"
            )}
          </button>
        </div>
      </form>

      {error && <p className="mb-4 rounded-md bg-red-50 p-3 text-sm text-red-700">{error}</p>}
      {success && <p className="mb-4 rounded-md bg-emerald-50 p-3 text-sm text-emerald-700">{success}</p>}

      {urlsLoading ? (
        <p className="text-sm text-slate-600">Loading URLs...</p>
      ) : urls.length === 0 ? (
        <div className="flex min-h-[200px] items-center justify-center rounded-lg border border-dashed bg-white p-8 text-center text-slate-500">
          No URLs yet. Shorten your first link above!
        </div>
      ) : (
        <div className="grid gap-4">
          {urls.map((url) => (
            <UrlCard
              key={url.id || url.shortCode}
              url={url}
              onDelete={handleDelete}
              deleting={deletingId === String(url.id)}
            />
          ))}
        </div>
      )}
    </div>
  );
}

export default Dashboard;
