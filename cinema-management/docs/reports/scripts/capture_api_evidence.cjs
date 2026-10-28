const {chromium}=require('playwright');const fs=require('fs');const crypto=require('crypto');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});const context=await browser.newContext({viewport:{width:1100,height:900}});const api=context.request;
 const base=process.env.DEMO_BASE_URL,out='docs/reports/evidence';const records=[];let csrf;
 async function call(method,path,data,expected,key){let headers={'Accept':'application/json'};if(csrf)headers['X-CSRF-TOKEN']=csrf;if(key)headers['Idempotency-Key']=key;
  const r=await api.fetch(base+path,{method,data,headers});const body=r.status()===204?null:await r.json();if(r.status()!==expected)throw Error(method+' '+path+' expected '+expected+' actual '+r.status()+' '+JSON.stringify(body));
  if(!path.includes('/csrf'))records.push({method,path,status:r.status(),response:body});return body;}
 async function login(role){csrf=(await call('GET','/api/auth/csrf',undefined,200)).token;await call('POST','/api/auth/login',{email:process.env['DEMO_'+role+'_EMAIL'],password:process.env['DEMO_'+role+'_PASSWORD']},200);csrf=(await call('GET','/api/auth/csrf',undefined,200)).token;}
 await login('ADMIN');await call('GET','/api/auth/me',undefined,200);const tag='RUN-'+crypto.randomUUID().replace(/-/g,'').slice(0,14);
 let g=await call('POST','/api/admin/genres',{name:tag+' Genre',description:'Packaged JAR evidence'},201);await call('PUT','/api/admin/genres/'+g.id,{name:tag+' Updated',description:null},200);
 let movieBody={code:tag,title:'Packaged demo '+tag,durationMinutes:120,minimumAge:13,status:'ACTIVE',genreIds:[g.id],description:null,posterUrl:null,trailerUrl:null};
 await call('POST','/api/admin/movies',{...movieBody,durationMinutes:0},400);let m=await call('POST','/api/admin/movies',movieBody,201);
 let room=await call('POST','/api/admin/rooms',{code:tag,name:tag,status:'ACTIVE'},201);let layout=await call('POST','/api/admin/rooms/'+room.id+'/seats/bulk',{rows:['A'],seatsPerRow:3,seatType:'STANDARD'},201);
 await call('PUT','/api/admin/rooms/'+room.id+'/seats/'+layout.seats[1].id,{rowLabel:'A',seatNumber:2,seatType:'VIP',status:'ACTIVE'},200);
 const day=new Date(Date.now()+30*86400000).toISOString().slice(0,10);const input={movieId:m.id,roomId:room.id,startsAt:day+'T18:00:00+07:00',standardPrice:80000,vipPrice:100000};
 const show=await call('POST','/api/admin/showtimes',input,201);await call('POST','/api/admin/showtimes',input,409);await call('GET',show._links.self.href,undefined,200);
 const booking={showtimeId:show.id,seatIds:[layout.seats[0].id,layout.seats[1].id],priceVersion:0,ageChecked:true};let key=crypto.randomUUID();let b=await call('POST','/api/bookings',booking,201,key);await call('POST','/api/bookings',booking,200,key);await call('POST','/api/bookings',booking,409,crypto.randomUUID());
 await call('POST','/api/bookings/'+b.id+'/cancel',{reason:'Packaged demo'},200);let replacement=await call('POST','/api/bookings',{...booking,seatIds:[layout.seats[0].id]},201,crypto.randomUUID());await call('POST','/api/bookings/'+b.id+'/cancel',{reason:'Repeat keeps replacement'},200);await call('POST','/api/bookings/'+replacement.id+'/cancel',{reason:'Keep demo history'},200);
 await login('STAFF');await call('GET','/api/admin/statistics/showtimes?from='+day+'&to='+day,undefined,403);await call('POST','/api/admin/movies',movieBody,403);await call('GET','/api/showtimes/'+show.id+'/seats',undefined,200);await login('ADMIN');
 const fixture=JSON.parse(fs.readFileSync('target/statistics-evidence.json','utf8')).fixture;
 const stats=await call('GET','/api/admin/statistics/booked-seats?from='+fixture.day+'&to='+fixture.day+'&movieId='+fixture.movieA,undefined,200);
 if(Number(stats.items[0].bookingValue)!==260000)throw Error('Unexpected statistics');
 async function shot(path,name){const p=await context.newPage();await p.goto(base+path);const pre=p.locator('pre').first();await pre.evaluate(el=>{el.textContent=JSON.stringify(JSON.parse(el.textContent),null,2);el.style.cssText='font:16px Consolas,monospace;white-space:pre-wrap;margin:20px;color:#111;background:white;';});
  const content=await pre.textContent();if(/password|X-CSRF|JSESSIONID|"token"/i.test(content))throw Error('Unsafe screenshot content');await p.screenshot({path:out+'/'+name,fullPage:true});await p.close();}
 await shot(show._links.self.href,'api-hateoas.png');await shot('/api/admin/statistics/booked-seats?from='+fixture.day+'&to='+fixture.day+'&movieId='+fixture.movieA,'api-statistics.png');
 await call('POST','/api/auth/logout',undefined,200);await call('GET','/api/auth/me',undefined,401);
 fs.writeFileSync(out+'/packaged-api-evidence.json',JSON.stringify({capturedAt:new Date().toISOString(),frontendTested:false,formattingNote:'Screenshots show actual API JSON with whitespace formatting only',records},null,2));await browser.close();process.stdout.write('Packaged JAR flows passed; safe JSON screenshots captured; steps='+records.length+'\n');
})().catch(e=>{process.stderr.write(e.message+'\n');process.exit(1)});
