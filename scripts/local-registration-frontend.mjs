import { createServer } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';
const root=fileURLToPath(new URL('../',import.meta.url));
const server=await createServer({root,configFile:false,envDir:process.argv[2],plugins:[react()],
 define:{'import.meta.env.VITE_API_URL':JSON.stringify('http://127.0.0.1:8182/api')},
 server:{host:'127.0.0.1',port:5179,strictPort:true,hmr:{port:5179}}});
await server.listen();
