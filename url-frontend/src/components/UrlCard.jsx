import { useState } from "react";

const API_BASE = import.meta.env.VITE_API_BASE_URL || "";

function UrlCard({ url, onDelete, deleting }) {
  const [copied, setCopied] = useState(false);

  const shortUrl =
    url?.shortUrl ||
    (url?.shortCode ? `${API_BASE}/${url.shortCode}` : "-");

  const longUrl = url?.longUrl || url?.originalUrl || "-";

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(shortUrl);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      setCopied(false);
    }
  };

  const handleDelete = () => {
    if (url?.id) onDelete(url.id);
  };

  return (
    <div className="rounded-lg border bg-white p-4 shadow-sm">
      <p className="mb-2 break-all text-sm text-slate-500">{longUrl}</p>
      <a
        href={shortUrl}
        target="_blank"
        rel="noreferrer"
        className="mb-4 block break-all text-sm font-medium text-blue-600 hover:underline"
      >
        {shortUrl}
      </a>

      <div className="mb-3 flex items-center gap-2 text-xs text-slate-500">
        <span>📊 {url?.clickCount || 0} clicks</span>
      </div>

      <div className="flex items-center gap-2">
        <button
          onClick={handleCopy}
          className="rounded-md border border-slate-300 px-3 py-2 text-sm hover:bg-slate-50"
        >
          {copied ? "Copied" : "Copy"}
        </button>
        <button
          onClick={handleDelete}
          disabled={deleting}
          className="rounded-md bg-red-600 px-3 py-2 text-sm text-white hover:bg-red-500 disabled:cursor-not-allowed disabled:opacity-70"
        >
          {deleting ? "Deleting..." : "Delete"}
        </button>
      </div>
    </div>
  );
}

export default UrlCard;
