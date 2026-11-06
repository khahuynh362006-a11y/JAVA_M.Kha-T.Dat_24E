# Nguồn artwork

## Bộ poster điện ảnh cho 40 phim gốc

Theo yêu cầu mở rộng từ mẫu đã duyệt: giữ `poster-horizon-realistic-v1.png` và tạo riêng 39 poster nữa bằng imagegen tích hợp. Mỗi phim có cảnh phù hợp nội dung, ảnh phong cách điện ảnh chân thật, tiêu đề Việt và tên gốc; các phim hoạt hình dùng chất liệu/ánh sáng kiểu mô hình điện ảnh. Không tải poster thương mại hoặc dùng ảnh người nổi tiếng.

- Tệp ảnh mới: `frontend/public/art/posters/<slug>-v1.png` (39 ảnh). Ảnh mẫu đầu tiên giữ ở thư mục art.
- Danh sách chính xác 40 slug → URL: `frontend/public/art/poster-collection.json`.
- Toàn bộ 39 prompt: [poster-collection-v1.json](prompts/poster-collection-v1.json). Prompt mẫu đầu tiên vẫn ở [poster-horizon-realistic-v1.txt](prompts/poster-horizon-realistic-v1.txt).
- [Trang xem toàn bộ poster](poster-collection.html) dùng ảnh đã lưu trong source; nhấp poster để mở phim tương ứng trên web local.
- Hai phim “test” và “Phim kiểm thử E2E” được loại trừ, giữ ảnh và metadata hiện tại. Ảnh SVG gốc cũng được giữ để khôi phục.
- `scripts/apply-poster-collection.ps1` chỉ cập nhật local qua API admin, kiểm đủ ảnh, sao lưu metadata, đổi poster của 40 slug gốc và đối chiếu hai phim loại trừ trước/sau. Seed/generator đọc registry để không trở về poster cũ khi dựng lại catalog mới.

Các nhân vật và cảnh là minh họa AI cho phim hư cấu của dự án, không phải hình quay hoặc ảnh quảng bá của tác phẩm phát hành thương mại.

Hero: frontend/public/art/hero-horizon.png. Ảnh minh họa do công cụ image generation tạo cho dự án; không phải cảnh trích từ phim thương mại.

Prompt: Cinematic photorealistic wide alien rocky shoreline, enormous ringed planet above the horizon, lone astronaut on the right, atmospheric light and dark empty space on the left for website copy. No text, no logos, no existing movie franchise.

Bản gốc nằm trong thư mục generated_images của phiên Codex; bản dùng trong website được sao chép vào public/art. Chỉ tạo một hero, không tải ảnh tìm kiếm để giả làm tài sản tự tạo.

40 poster/backdrop SVG là artwork hình học sinh bằng scripts/generate-art.mjs, dùng tiêu đề phim hư cấu. Các screenshot trong docs/screenshots chụp website chạy local; không phải mockup thay cho bằng chứng chạy.

Không cần image generation để tái chạy ứng dụng vì asset đã nằm trong source. Không chạy lại generator chỉ để khởi động website.

## Poster mẫu chân thật — Phía bên kia chân trời (05/11/2026)

- Asset: `frontend/public/art/poster-horizon-realistic-v1.png`, 1024 × 1536 (2:3). Tạo bằng công cụ imagegen tích hợp cho phim hư cấu của CineVerse, không phải poster hoặc diễn viên từ phim thương mại. Không tải ảnh Internet cho mẫu này.
- Prompt đầy đủ: [poster-horizon-realistic-v1.txt](prompts/poster-horizon-realistic-v1.txt). Phong cách ảnh quay phim 35mm, nữ phi hành gia với chất liệu/da chân thật, bờ biển hành tinh lạ và tiêu đề Việt có dấu.
- Thay thử một phim có slug `phia-ben-kia-chan-troi`; giữ nguyên poster SVG gốc để có thể quay lại. Các poster khác không đổi. Seed/generator giữ đường dẫn PNG khi tạo catalog mới; dữ liệu đang chạy được cập nhật qua API admin có kiểm quyền và audit.
