import fs from 'node:fs';
const art='frontend/public/art';fs.mkdirSync(art,{recursive:true});fs.mkdirSync('backend/src/main/resources/seed',{recursive:true});
const posterRegistryPath=`${art}/poster-collection.json`;
const photoPosters=fs.existsSync(posterRegistryPath)?JSON.parse(fs.readFileSync(posterRegistryPath,'utf8')):{};
const titles=[
['Phía bên kia chân trời','Beyond the Horizon','Khoa học viễn tưởng','Một tín hiệu từ hành tinh xa đưa phi hành đoàn đến ranh giới giữa ký ức và thực tại.'],
['Thành phố không ngủ','The Sleepless City','Hành động','Giữa thành phố chìm trong mưa, một thanh tra lần theo dấu vết của cuộc gọi cuối cùng.'],
['Mùa hè năm ấy','That Summer','Tình cảm','Hai người bạn gặp lại bên bờ biển, nơi những lá thư chưa gửi vẫn chờ một lời hồi đáp.'],
['Dấu chân trên mây','Footprints in the Sky','Phiêu lưu','Một nhiếp ảnh gia trẻ bắt đầu hành trình xuyên dãy núi để tìm lại người cha mất tích.'],
['Khu rừng thì thầm','The Whispering Woods','Kỳ ảo','Cánh rừng cổ thức giấc khi một cô bé phát hiện chiếc chìa khóa dưới gốc cây nghìn tuổi.'],
['Chuyến tàu cuối cùng','The Last Train','Bí ẩn','Bảy hành khách trên chuyến tàu đêm nhận ra không ai nhớ được nơi mình đã lên tàu.'],
['Đại dương tĩnh lặng','The Silent Ocean','Tài liệu','Hành trình tưởng tượng khám phá những sinh vật kỳ diệu dưới tầng nước sâu.'],
['Những ngày rực rỡ','Radiant Days','Hài hước','Một nhóm bạn mở quán cà phê nhỏ và học cách trưởng thành từ những điều không như kế hoạch.'],
['Tín hiệu 2099','Signal 2099','Khoa học viễn tưởng','Những bản tin từ tương lai buộc một kỹ sư đưa ra lựa chọn thay đổi vận mệnh nhân loại.'],
['Bóng tối sau cửa','Behind the Door','Kinh dị','Ngôi nhà thừa kế có một căn phòng luôn khóa và những tiếng bước chân lúc ba giờ sáng.'],
['Người giữ bình minh','Keeper of Dawn','Chính kịch','Một người gác hải đăng trở thành điểm tựa cho cả thị trấn sau cơn bão lớn.'],
['Đường về phương Bắc','Northbound','Phiêu lưu','Hai anh em xa cách cùng vượt những con đèo trong chuyến đi cuối của chiếc xe cũ.'],
['Mật mã sao băng','Meteor Code','Hành động','Một nhà thiên văn phát hiện chuỗi số bí ẩn ẩn trong những bức ảnh bầu trời đêm.'],
['Lời hẹn mùa thu','An Autumn Promise','Tình cảm','Bản nhạc dang dở nối hai cuộc đời giữa những con phố nhuộm vàng lá tháng mười.'],
['Vùng đất đã quên','The Forgotten Land','Kỳ ảo','Tấm bản đồ xuất hiện trong giấc mơ dẫn một họa sĩ đến vương quốc nằm ngoài thời gian.'],
['Đội giải cứu tí hon','Tiny Rescue Team','Hoạt hình','Những người bạn bé nhỏ hợp sức cứu khu vườn trước ngày cơn mưa đầu mùa đến.'],
['Nhịp đập Sài Gòn','Saigon Heartbeat','Chính kịch','Những cuộc đời giao nhau trong một đêm thành phố mất điện và sáng lên tình người.'],
['Cánh cửa thời gian','The Time Door','Khoa học viễn tưởng','Một sinh viên tìm thấy lối đi đến quá khứ nhưng mỗi lần trở về lại đánh mất một ký ức.'],
['Hồ sơ số không','Case Zero','Bí ẩn','Vụ án tưởng đã khép lại được mở ra bởi một bức ảnh không có người chụp.'],
['Bản đồ những vì sao','A Map of Stars','Phiêu lưu','Cô bé mê thiên văn cùng ông nội tìm đến đài quan sát cuối cùng trên vùng cao.'],
['Bếp nhỏ yêu thương','A Kitchen of Love','Hài hước','Một đầu bếp vụng về phải cứu nhà hàng gia đình bằng công thức duy nhất của bà ngoại.'],
['Mắt bão','Eye of the Storm','Hành động','Đội cứu hộ đối mặt cơn bão lịch sử khi chiếc cầu duy nhất nối đất liền bị cuốn trôi.'],
['Sắc màu của gió','Colors of the Wind','Tài liệu','Những câu chuyện về người lưu giữ nghề thủ công trong miền đất đầy gió.'],
['Điều ước lúc nửa đêm','A Midnight Wish','Kỳ ảo','Chiếc đồng hồ cổ cho phép chủ nhân sống lại một phút quý giá nhất của mỗi ngày.'],
['Lá thư từ biển','Letters from the Sea','Tình cảm','Một bưu kiện đi lạc đưa người viết thư đến ngôi làng chài và mối duyên bất ngờ.'],
['Vệ tinh cuối cùng','The Last Satellite','Khoa học viễn tưởng','Trạm liên lạc cuối trên quỹ đạo vẫn phát đi một giọng nói không thể thuộc về con người.'],
['Khách sạn sương mù','The Mist Hotel','Kinh dị','Nhóm du khách trú đêm trong khách sạn nơi mỗi tấm gương phản chiếu một câu chuyện khác.'],
['Những kẻ mộng mơ','The Dreamers','Chính kịch','Ban nhạc nghiệp dư lên đường biểu diễn, mang theo tuổi trẻ và chiếc đàn đã cũ.'],
['Hành tinh nhỏ của tôi','My Little Planet','Hoạt hình','Một robot làm vườn bay qua những hành tinh nhỏ để tìm hạt giống cuối cùng của quê nhà.'],
['Lặng nghe mùa đông','Listen to Winter','Tình cảm','Trong thị trấn phủ tuyết, một người sửa đàn và cô giáo âm nhạc tìm lại tiếng nói của mình.'],
['Đường chân trời đỏ','Red Horizon','Hành động','Cuộc đua qua sa mạc trở thành nhiệm vụ sinh tồn khi đoàn xe mất liên lạc với thế giới.'],
['Hương vị ký ức','Taste of Memory','Chính kịch','Những món ăn trong cuốn sổ tay cũ giúp một gia đình nối lại các mối quan hệ đã xa.'],
['Hành trình vô tận','The Endless Journey','Phiêu lưu','Mỗi tập là một chặng đường mới trên hành trình khám phá thế giới và chính bản thân.'],
['Biên niên ánh sáng','Chronicles of Light','Kỳ ảo','Ba người trẻ giữ ngọn lửa cuối cùng giữa vương quốc chìm dần trong đêm dài.'],
['Sau màn mưa','After the Rain','Tình cảm','Những người hàng xóm ở khu chung cư cũ cùng tìm một khởi đầu mới sau mùa mưa.'],
['Trạm nghiên cứu số 7','Station Seven','Khoa học viễn tưởng','Đội khoa học ở trạm xa xôi ghi nhận một tín hiệu lặp lại mỗi bảy ngày.'],
['Thám tử phố cổ','Old Quarter Detective','Bí ẩn','Nữ thám tử trở về phố cũ và nhận giải những vụ mất tích chưa từng được ghi trong hồ sơ.'],
['Nhà mình có khách','Guests at Home','Hài hước','Căn nhà nhỏ bỗng nhộn nhịp khi cả gia đình cùng trở về với những bí mật dễ thương.'],
['Những mùa xanh','Seasons of Green','Tài liệu','Khám phá vẻ đẹp thiên nhiên và cuộc sống hòa hợp với rừng qua bốn mùa.'],
['Cuộc phiêu lưu của Mây','May’s Adventure','Hoạt hình','Mây và chú chim máy cùng đi tìm thành phố bay trong những câu chuyện của bà.']
];
const palettes=[['#080e20','#59677e','#f5caa3'],['#090e18','#184250','#ed644c'],['#251c2a','#b37372','#ffd6ad'],['#0b1b23','#447b89','#d9e4d0'],['#081c1c','#265747','#e2c784'],['#131321','#54465f','#dfa58a'],['#071c2e','#1a6979','#a3d6c5'],['#24213a','#8c627a','#e8ba86']];
function rand(seed){let n=seed;return()=>{n=(n*1664525+1013904223)>>>0;return n/4294967296;};}
function scene(i,w,h){let random=rand(i+81);let [dark,mid,light]=palettes[i%8];let stars=Array.from({length:85},()=>`<circle cx="${random()*w}" cy="${random()*h*.66}" r="${random()*1.7+.4}" fill="white" opacity="${random()*.6}"/>`).join('');
let peaks=(y,amp)=>`M0 ${h} L0 ${y} `+Array.from({length:18},(_,k)=>`L${k*w/17} ${y-random()*amp}`).join(' ')+` L${w} ${h}Z`;
return `<defs><linearGradient id="sky" x2="0" y2="1"><stop stop-color="${dark}"/><stop offset=".72" stop-color="${mid}"/><stop offset="1" stop-color="${light}"/></linearGradient><radialGradient id="glow"><stop stop-color="${light}" stop-opacity=".4"/><stop offset="1" stop-color="${light}" stop-opacity="0"/></radialGradient><linearGradient id="planet" x1="0" y1="0" x2=".8" y2="1"><stop stop-color="${light}"/><stop offset="1" stop-color="${mid}"/></linearGradient><linearGradient id="bottom" x2="0" y2="1"><stop stop-color="${dark}" stop-opacity="0"/><stop offset="1" stop-color="#08090d"/></linearGradient></defs><rect width="${w}" height="${h}" fill="url(#sky)"/>${stars}<ellipse cx="${w*.7}" cy="${h*.45}" rx="${w*.4}" ry="${h*.5}" fill="url(#glow)"/>${i%4===0?`<ellipse cx="${w*.73}" cy="${h*.32}" rx="${w*.23}" ry="${h*.055}" fill="none" stroke="${light}" stroke-width="${h*.012}" opacity=".35" transform="rotate(-22 ${w*.73} ${h*.32})"/><circle cx="${w*.73}" cy="${h*.32}" r="${h*.155}" fill="url(#planet)"/>`:`<circle cx="${w*.73}" cy="${h*.26}" r="${h*.09}" fill="${light}" opacity=".8"/>`}
<path d="${peaks(h*.73,h*.27)}" fill="${mid}"/><path d="${peaks(h*.87,h*.25)}" fill="${dark}" opacity=".85"/><path d="${peaks(h,h*.18)}" fill="#070b13"/><rect y="${h*.58}" width="${w}" height="${h*.42}" fill="url(#bottom)"/>${i%4===1?Array.from({length:20},(_,k)=>{let bh=h*(.12+random()*.25);return `<rect x="${k*w/20}" y="${h*.8-bh}" width="${w/25}" height="${bh}" fill="${dark}"/><path d="M${k*w/20+4} ${h*.8-bh+10}v${bh*.6}" stroke="${light}" opacity=".35"/>`;}).join(''):''}`;}
const esc=s=>s.replace(/&/g,'&amp;').replace(/</g,'&lt;');
const slug=s=>s.normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/đ/g,'d').replace(/Đ/g,'D').toLowerCase().replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,'');
const data=titles.map(([title,original,genre,description],i)=>{let [dark,,light]=palettes[i%8];const words=original.toUpperCase().split(' ');const a=words.slice(0,Math.ceil(words.length/2)).join(' '),b=words.slice(Math.ceil(words.length/2)).join(' ');
 fs.writeFileSync(`${art}/poster-${i+1}.svg`,`<svg xmlns="http://www.w3.org/2000/svg" width="600" height="900" viewBox="0 0 600 900">${scene(i,600,900)}<text x="40" y="56" fill="${light}" font-family="Arial,sans-serif" font-size="12" letter-spacing="4">CINEVERSE · FICTIONAL COLLECTION</text><text x="300" y="686" text-anchor="middle" fill="#fff" font-family="Georgia,serif" font-size="${a.length>15?34:45}" letter-spacing="4">${esc(a)}</text><text x="300" y="744" text-anchor="middle" fill="#fff" font-family="Georgia,serif" font-size="${b.length>15?34:45}" letter-spacing="4">${esc(b)}</text><path d="M260 784h80" stroke="${light}"/><text x="300" y="824" text-anchor="middle" fill="${light}" font-family="Arial,sans-serif" font-size="12" letter-spacing="4">A WORLD WAITING TO BE FOUND</text><text x="300" y="866" text-anchor="middle" fill="#78828f" font-family="Arial,sans-serif" font-size="9" letter-spacing="2">ORIGINAL DEMO ARTWORK / ${2026-i%5}</text></svg>`);
 fs.writeFileSync(`${art}/scene-${i+1}.svg`,`<svg xmlns="http://www.w3.org/2000/svg" width="1920" height="1080" viewBox="0 0 1920 1080">${scene(i,1920,1080)}</svg>`);
 return {title,originalTitle:original,slug:slug(title),genre,description,poster:photoPosters[slug(title)]??(i===0?'/art/poster-horizon-realistic-v1.png':`/art/poster-${i+1}.svg`),backdrop:`/art/scene-${i+1}.svg`,type:i>=32?'SERIES':'MOVIE',year:2026-i%5,vip:i%4===0&&i!==0};});
fs.writeFileSync('backend/src/main/resources/seed/catalog.json',JSON.stringify(data,null,2));
fs.writeFileSync(`${art}/fallback.svg`,`<svg xmlns="http://www.w3.org/2000/svg" width="600" height="900"><rect width="600" height="900" fill="#151821"/><circle cx="300" cy="360" r="100" fill="none" stroke="#e50938" stroke-width="3"/><path d="m280 320 60 40-60 40z" fill="#f7f8fa"/><text x="300" y="550" text-anchor="middle" fill="#f7f8fa" font-family="Arial" font-size="24" letter-spacing="6">CINEVERSE</text></svg>`);
console.log('Created 40 original local posters and backdrops; fictional catalog JSON.');
