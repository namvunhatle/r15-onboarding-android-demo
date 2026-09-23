// node bake.mjs <prototype-a7 dir> <out dir> — renders each track's intro + loop to 16-bit stereo WAV.
import { chromium } from 'playwright'
import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'

const [proto, outDir] = process.argv.slice(2)
const here = path.dirname(new URL(import.meta.url).pathname)
const tracks = JSON.parse(fs.readFileSync(path.join(proto, 'src/tracks.json'), 'utf8'))
fs.mkdirSync(outDir, { recursive: true })

const server = http.createServer((req, res) => {
  const p = req.url === '/' ? path.join(here, 'bake.html') : path.join(proto, 'public', decodeURIComponent(req.url))
  if (!fs.existsSync(p)) { res.writeHead(404); return res.end() }
  res.writeHead(200, { 'Content-Type': p.endsWith('.html') ? 'text/html' : 'application/octet-stream' })
  fs.createReadStream(p).pipe(res)
}).listen(0)
const port = server.address().port

function wav(pcm, sr) {
  const h = Buffer.alloc(44)
  h.write('RIFF', 0); h.writeUInt32LE(36 + pcm.length, 4); h.write('WAVE', 8)
  h.write('fmt ', 12); h.writeUInt32LE(16, 16); h.writeUInt16LE(1, 20); h.writeUInt16LE(2, 22)
  h.writeUInt32LE(sr, 24); h.writeUInt32LE(sr * 4, 28); h.writeUInt16LE(4, 32); h.writeUInt16LE(16, 34)
  h.write('data', 36); h.writeUInt32LE(pcm.length, 40)
  return Buffer.concat([h, pcm])
}

const browser = await chromium.launch({ channel: 'chrome' })
const page = await browser.newPage()
await page.goto(`http://localhost:${port}/`)
for (const t of tracks) {
  const r = await page.evaluate((tr) => window.bake(tr), t)
  fs.writeFileSync(path.join(outDir, `${t.id}_intro.wav`), wav(Buffer.from(r.intro, 'base64'), r.sr))
  fs.writeFileSync(path.join(outDir, `${t.id}_loop.wav`), wav(Buffer.from(r.loop, 'base64'), r.sr))
  console.log(t.id, `sr ${r.sr}`, `intro ${(r.introFrames / r.sr).toFixed(3)}s peak ${r.introPeak.toFixed(3)}`, `loop ${(r.loopFrames / r.sr).toFixed(3)}s peak ${r.loopPeak.toFixed(3)}`)
}
await browser.close()
server.close()
