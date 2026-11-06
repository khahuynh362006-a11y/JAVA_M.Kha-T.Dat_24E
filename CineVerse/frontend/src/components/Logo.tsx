import { Link } from "react-router-dom";
import { Play } from "lucide-react";
export function Logo() {
  return (
    <Link to="/" className="logo" aria-label="CineVerse trang chủ">
      <span className="logo-symbol">
        <Play size={14} fill="currentColor" />
      </span>
      <span>
        CINE<span>VERSE</span>
        <sup>®</sup>
      </span>
    </Link>
  );
}
