import fs from 'node:fs';
const secret=JSON.parse(fs.readFileSync('../.runtime/dev-secrets.json','utf8').replace(/^\uFEFF/,''));
const base='http://localhost:8080/api/v1';
async function request(path,method='GET',data,token){const r=await fetch(base+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:data?JSON.stringify(data):undefined});const text=await r.text();if(!r.ok)throw new Error(path+' '+r.status+' '+text);return text?JSON.parse(text):null;}
const auth=await request('/auth/login','POST',{email:'free@cineverse.local',password:secret.demoPassword});
const shows=await request('/showtimes');const s=shows.find(s=>s.movieId===1)??shows[0];const map=await request(`/showtimes/${s.id}/seats`);const seats=map.seats.filter(x=>x.status==='AVAILABLE').slice(0,2);
const booking=await request('/bookings','POST',{showtimeId:s.id,seatIds:seats.map(s=>s.id)},auth.accessToken);
const order=await request('/payments','POST',{bookingId:booking.id},auth.accessToken);
await request(`/payments/${order.id}/simulate`,'POST',{outcome:'success'},auth.accessToken);
const ticket=await request('/bookings/'+booking.id,'GET',undefined,auth.accessToken);
fs.writeFileSync('../.runtime/ticket-smoke.json',JSON.stringify({bookingId:booking.id,orderId:order.id,ticket},null,2));
console.log(JSON.stringify({showtimeId:s.id,bookingStatus:ticket.status,total:ticket.totalAmount,tickets:ticket.tickets.length,seats:ticket.items.map(x=>x.seatLabel)},null,2));
