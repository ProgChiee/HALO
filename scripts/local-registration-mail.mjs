// Disposable loopback-only SMTP sink. No outbound network or filesystem operations.
import net from 'node:net';
import http from 'node:http';
const messages=[];
const escape=value=>value.replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;');
const smtp=net.createServer(socket=>{
 socket.setTimeout(15000,()=>socket.destroy());socket.setEncoding('utf8');
 let buffer='',data=false,mail='',sender=false,recipient=false;
 socket.on('error',()=>{});socket.write('220 HALO local test SMTP\r\n');
 socket.on('data',chunk=>{
  buffer+=chunk;if(buffer.length+mail.length>65536){socket.end('552 Message too large\r\n');return;}
  let end;
  while((end=buffer.indexOf('\r\n'))>=0){
   const line=buffer.slice(0,end);buffer=buffer.slice(end+2);
   if(data){
    if(line==='.') {messages.unshift(mail);messages.splice(16);mail='';data=false;sender=false;recipient=false;socket.write('250 Captured locally\r\n');}
    else mail+=(line.startsWith('..')?line.slice(1):line)+'\r\n';
   }else if(line.length>512){socket.end('500 Command too long\r\n');return;}
   else if(/^(EHLO|HELO) /i.test(line))socket.write('250 HALO-local-only\r\n');
   else if(/^MAIL FROM:/i.test(line)){sender=true;recipient=false;socket.write('250 OK\r\n');}
   else if(/^RCPT TO:/i.test(line)&&sender){recipient=true;socket.write('250 OK\r\n');}
   else if(/^DATA$/i.test(line)&&recipient){data=true;socket.write('354 End with dot\r\n');}
   else if(/^QUIT$/i.test(line)){socket.end('221 Bye\r\n');return;}
   else if(/^RSET$/i.test(line)){sender=false;recipient=false;mail='';socket.write('250 OK\r\n');}
   else if(/^NOOP$/i.test(line))socket.write('250 OK\r\n');
   else socket.write('502 Unsupported command\r\n');
  }
 });
});
smtp.maxConnections=4;
const ui=http.createServer((req,res)=>{
 if(req.headers.host!=='127.0.0.1:18025'||req.method!=='GET'||!['/','/messages'].includes(req.url)){res.writeHead(404);res.end();return;}
 res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');
 res.setHeader('Content-Security-Policy',"default-src 'none'; frame-ancestors 'none'; base-uri 'none'");
 if(req.url==='/messages'){res.setHeader('Content-Type','application/json');res.end(JSON.stringify(messages));return;}
 res.setHeader('Content-Type','text/html; charset=utf-8');
 res.end('<!doctype html><html><title>HALO local email catcher</title><h1>HALO local email catcher</h1><p>Disposable messages only. Reload to see new codes. Nothing is delivered externally.</p>'+messages.map(m=>'<hr><pre>'+escape(m)+'</pre>').join('')+'</html>');
});
ui.headersTimeout=5000;ui.requestTimeout=10000;
smtp.listen(11025,'127.0.0.1');ui.listen(18025,'127.0.0.1');
for(const server of [smtp,ui])server.on('error',()=>{console.error('Local mail catcher could not bind its reserved port.');process.exit(1);});
