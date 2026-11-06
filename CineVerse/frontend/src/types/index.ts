export interface User {
  id: number;
  name: string;
  email: string;
  role: "USER" | "ADMIN";
  verified: boolean;
  locked: boolean;
  avatar: string | null;
  marketing: boolean;
  vip: boolean;
  vipUntil: string | null;
  createdAt: string;
}
export interface Session {
  accessToken: string;
  user: User;
}
export interface Lookup {
  id: number;
  name: string;
  slug: string;
  active: boolean;
}
export interface Movie {
  id: number;
  title: string;
  originalTitle: string;
  slug: string;
  synopsis: string;
  shortDescription: string;
  type: "MOVIE" | "SERIES";
  countryId: number;
  releaseYear: number;
  duration: number;
  ageRating: string;
  language: string;
  director: string;
  castNames: string;
  tags: string;
  poster: string;
  backdrop: string;
  status: string;
  vip: boolean;
  featured: boolean;
  priority: number;
  rating: number;
  views: number;
  genres: Lookup[];
  createdAt: string;
}
export interface Episode {
  id: number;
  number: number;
  title: string;
  description: string;
  duration: number;
  published: boolean;
  vip: boolean;
}
export interface Season {
  id: number;
  number: number;
  title: string;
  episodes: Episode[];
}
export interface Detail {
  movie: Movie;
  seasons: Season[];
  related: Movie[];
}
export interface Paged<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}
export interface History {
  id: number;
  movie: Movie;
  episodeId: number | null;
  positionSeconds: number;
  durationSeconds: number;
  completed: boolean;
  updatedAt: string;
}
export interface Review {
  id: number;
  userId: number;
  name: string;
  avatar: string | null;
  stars: number;
  content: string;
  createdAt: string;
  updatedAt: string;
}
export interface Plan {
  id: number;
  name: string;
  price: number;
  durationDays: number;
  description: string;
  active: boolean;
  recommended: boolean;
  sortOrder: number;
}
export interface Order {
  bookingId: string | null;
  purpose: string;
  id: string;
  userId: number;
  planId: number;
  planName: string;
  durationDays: number;
  amount: number;
  currency: string;
  provider: string;
  status: string;
  payUrl: string | null;
  providerTransaction: string | null;
  createdAt: string;
  paidAt: string | null;
  expiresAt: string;
  failureReason: string | null;
}
export interface Playback {
  movieId: number;
  episodeId: number | null;
  title: string;
  slug: string;
  poster: string;
  url: string;
  subtitleUrl: string | null;
  expiresAt: string;
  demo: boolean;
  license: string;
}
export interface Notification {
  id: number;
  user_id: number;
  type: string;
  title: string;
  content: string;
  link: string;
  created_at: string;
  read_at: string | null;
}
export type Row = Record<string, string | number | boolean | null>;
export interface Dashboard {
  showtimes: number;
  cinemas: number;
  ticketsSold: number;
  users: number;
  vipUsers: number;
  movies: number;
  series: number;
  episodes: number;
  views: number;
  momoRevenue: number;
  sandboxRevenue: number;
  payosRevenue: number;
  simulationRevenue: number;
  paidOrders: number;
  failedOrders: number;
  chart: {
    day: string;
    revenue: number;
    payos: number;
    simulation: number;
    registrations: number;
    views: number;
  }[];
  recentOrders: Row[];
  topMovies: Row[];
  paymentStatus: Row[];
}
export interface MovieInput {
  title: string;
  originalTitle: string;
  slug: string;
  synopsis: string;
  shortDescription: string;
  type: string;
  countryId: number;
  releaseYear: number;
  duration: number;
  ageRating: string;
  language: string;
  director: string;
  castNames: string;
  tags: string;
  poster: string;
  backdrop: string;
  status: string;
  vip: boolean;
  featured: boolean;
  priority: number;
  genreIds: number[];
  videoFile: string | null;
  trailerFile: string | null;
  subtitleFile: string | null;
}
export interface Cinema {
  id: number;
  name: string;
  city: string;
  address: string;
  active: boolean;
}
export interface Showtime {
  id: number;
  movieId: number;
  movieTitle: string;
  movieSlug: string;
  poster: string;
  duration: number;
  ageRating: string;
  auditoriumId: number;
  auditoriumName: string;
  format: string;
  cinemaId: number;
  cinemaName: string;
  city: string;
  address: string;
  startsAt: string;
  endsAt: string;
  basePrice: number;
  availableSeats: number;
  totalSeats: number;
}
export interface Seat {
  id: number;
  rowLabel: string;
  number: number;
  kind: "STANDARD" | "VIP";
  price: number;
  status: "AVAILABLE" | "HELD" | "BOOKED";
}
export interface SeatMap {
  showtime: Showtime;
  seats: Seat[];
  serverTime: string;
}
export interface BookingItem {
  seatId: number;
  seatLabel: string;
  seatKind: string;
  price: number;
}
export interface CinemaTicket {
  id: string;
  seatId: number;
  seatLabel: string;
  code: string;
  status: string;
  issuedAt: string;
  usedAt: string | null;
}
export interface Booking {
  id: string;
  userId: number;
  status: string;
  totalAmount: number;
  holdUntil: string;
  createdAt: string;
  confirmedAt: string | null;
  showtime: Showtime;
  items: BookingItem[];
  tickets: CinemaTicket[];
  paymentOrderId: string | null;
}
