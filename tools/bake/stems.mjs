// node stems.mjs <prototype-a7 1.6.x dir> <out dir> — renders the six 1.6.6 stems (stems.html) to 48 kHz WAV.
// Then: tools/encode_stems.sh <out dir>  → Ogg Opus in the library's assets + the frame counts for stems.json.
import { chromium } from 'playwright'
import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'

const [proto, outDir] = process.argv.slice(2)
const here = path.dirname(new URL(import.meta.url).pathname)
const tracks = JSON.parse(fs.readFileSync(path.join(proto, 'src/tracks.json'), 'utf8'))
const bed = tracks.find((t) => t.id === 'future-pop')
const teaser = tracks.find((t) => t.id === 'upbeat-pop')
fs.mkdirSync(outDir, { recursive: true })

const server = http.createServer((req, res) => {
  const p = req.url === '/' ? path.join(here, 'stems.html') : path.join(proto, 'public', decodeURIComponent(req.url))
  if (!fs.existsSync(p)) { res.writeHead(404); return res.end() }
  res.writeHead(200, { 'Content-Type': p.endsWith('.html') ? 'text/html' : 'application/octet-stream' })
  fs.createReadStream(p).pipe(res)
}).listen(0)

function wav(pcm, sr) {
  const h = Buffer.alloc(44)
  h.write('RIFF', 0); h.writeUInt32LE(36 + pcm.length, 4); h.write('WAVE', 8)
  h.write('fmt ', 12); h.writeUInt32LE(16, 16); h.writeUInt16LE(1, 20); h.writeUInt16LE(2, 22)
  h.writeUInt32LE(sr, 24); h.writeUInt32LE(sr * 4, 28); h.writeUInt16LE(4, 32); h.writeUInt16LE(16, 34)
  h.write('data', 36); h.writeUInt32LE(pcm.length, 40)
  return Buffer.concat([h, pcm])
}

const browser = await chromium.launch()
const page = await browser.newPage()
await page.goto(`http://localhost:${server.address().port}/`)
const r = await page.evaluate(([b, t]) => window.bake(b, t), [bed, teaser])
for (const [k, v] of Object.entries(r)) {
  fs.writeFileSync(path.join(outDir, `${k}.wav`), wav(Buffer.from(v.data, 'base64'), 48000))
  console.log(`${k}  ${(v.frames / 48000).toFixed(3)} s  frames ${v.frames}  peak ${v.peak.toFixed(3)}`)
}
await browser.close()
server.close()
