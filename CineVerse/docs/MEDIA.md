# Media hợp pháp

Bộ poster hiện tại gồm ảnh mẫu “Phía bên kia chân trời” và 39 ảnh mới trong `frontend/public/art/posters`, tạo bằng imagegen tích hợp theo yêu cầu người dùng. Đây là cảnh và nhân vật hư cấu; nguồn, danh sách và prompt tại [ASSETS.md](ASSETS.md). Hai phim “test” và “Phim kiểm thử E2E” giữ nguyên ảnh. Không lấy poster thương mại trên Internet cho bộ này.

Các phim, poster, tên rạp và nội dung catalog là dữ liệu minh họa. Trailer không phải nội dung của bộ phim hư cấu trên thẻ. Website bán vé, không cung cấp phim đầy đủ.

| Tệp | Nguồn tải | Giấy phép / tác giả | Sử dụng |
|---|---|---|---|
| sintel.mp4 | https://media.w3.org/2010/05/sintel/trailer.mp4 | Blender Foundation, CC BY 3.0 | Trailer minh họa |
| bunny.mp4 | https://media.w3.org/2010/05/bunny/trailer.mp4 | Blender Foundation, CC BY 3.0 | Trailer dự phòng |
| bunny-full.mp4 | https://media.w3.org/2010/05/bunny/movie.mp4 | Blender Foundation, CC BY 3.0 | Tệp legacy local, không có route phát phim đầy đủ |
| demo-vi.vtt | Tự viết | Văn bản minh họa của dự án | Phụ đề demo |

[Sintel sharing](https://durian.blender.org/sharing/), [Big Buck Bunny about](https://peach.blender.org/about/), [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/).
Attribution: © Blender Foundation | durian.blender.org. Big Buck Bunny: © copyright 2008, Blender Foundation / www.bigbuckbunny.org. Giữ nguyên credit roll trong bản đầy đủ; không xóa ghi công khỏi video.

Chạy scripts/setup-media.ps1 để tải từ nguồn công khai trên. MP4 được gitignore để không đưa binary lớn vào repository. Không thay bằng phim thương mại chưa được cấp phép. Khi đưa trailer thật lên rạp thương mại cần kiểm tra quyền phân phối của từng nhà phát hành.

Poster và backdrop SVG do scripts/generate-art.mjs tạo từ hình học, không sao chép poster thương mại. Hero AI xem ASSETS.md. Font Be Vietnam Pro được đóng gói qua @fontsource; giữ license của package. Các thư viện icon sử dụng license đi kèm dependency.

Poster mẫu `poster-horizon-realistic-v1.png` cho “Phía bên kia chân trời” được tạo mới bằng imagegen cho dự án ngày 05/11/2026, nhân vật và bối cảnh hư cấu; không lấy từ phim thương mại. Nguồn và prompt trong ASSETS.md. Đây là hình minh họa AI, không phải tài liệu quảng bá của một bộ phim đang phát hành thật.

