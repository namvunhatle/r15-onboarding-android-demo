// Export the Figma variables bound in the A7 frames → tools/tokens/a7_figma_tokens.json.
//
// Read-only: it walks the nodes and resolves each bound variable in that node's modes; it writes nothing to Figma.
// Run it in the R15 file (2ckF7fXzWpx8fBkOImitb4) from a plugin context that can reach localhost — e.g. the Figma
// Console MCP's figma_execute — with a receiver listening:
//
//   python3 -c "import http.server as h
//   class H(h.BaseHTTPRequestHandler):
//       def do_OPTIONS(s): s.send_response(204); [s.send_header(k, '*') for k in ('Access-Control-Allow-Origin', 'Access-Control-Allow-Methods', 'Access-Control-Allow-Headers')]; s.end_headers()
//       def do_POST(s): open('tools/tokens/a7_figma_tokens.json', 'wb').write(s.rfile.read(int(s.headers['Content-Length']))); s.send_response(200); s.send_header('Access-Control-Allow-Origin', '*'); s.end_headers()
//   h.HTTPServer(('localhost', 9230), H).serve_forever()"
//
// then: python3 tools/gen_tokens.py

const ROOTS = ['15552:115354', '15560:138551']; // A7 · UI (ZEN) v2 · Phone Hero · 1.3.3, 03 · Interstitial (SDK)
const vars = new Map();
const round = (x) => Math.round(x * 10000) / 10000;

async function record(id, node) {
  const v = await figma.variables.getVariableByIdAsync(id);
  if (!v) return;
  const coll = await figma.variables.getVariableCollectionByIdAsync(v.variableCollectionId);
  let value = v.resolveForConsumer(node).value;
  if (value && typeof value === 'object' && 'r' in value) value = [value.r, value.g, value.b, value.a ?? 1].map((c) => Math.round(c * 1000) / 1000);
  else if (typeof value === 'number') value = round(value);
  const key = `${coll.name}\u0000${v.name}`;
  const prev = vars.get(key);
  if (prev && JSON.stringify(prev.value) !== JSON.stringify(value)) throw new Error(`${v.name} resolves differently across nodes`);
  vars.set(key, { collection: coll.name, name: v.name, type: v.resolvedType, value, bindings: (prev ? prev.bindings : 0) + 1 });
}

async function scan(node) {
  for (const b of Object.values(node.boundVariables || {})) {
    for (const alias of Array.isArray(b) ? b : b && b.id ? [b] : Object.values(b || {})) {
      if (alias && alias.id) await record(alias.id, node);
    }
  }
  if ('children' in node) for (const c of node.children) await scan(c);
}

for (const id of ROOTS) await scan(await figma.getNodeByIdAsync(id));
const out = {
  source: {
    file: 'R15 2024 Update (2ckF7fXzWpx8fBkOImitb4)',
    nodes: ['15552:115354 A7 · UI (ZEN) v2 · Phone Hero · 1.3.3', '15560:138551 03 · Interstitial (SDK)'],
    modes: 'as the nodes resolve them: ZEN Light · Global - Base 14 · Brand Emphasis - S1 · Base Colors Zen',
    exported: new Date().toISOString().slice(0, 10),
    note: 'Every variable bound to a node in those frames, with the value it resolves to there. Colors are [r, g, b, a] in 0..1. Regenerate code with tools/gen_tokens.py.',
  },
  variables: [...vars.values()].sort((a, b) => (a.collection + a.name < b.collection + b.name ? -1 : 1)),
};
const res = await fetch('http://localhost:9230/a7_figma_tokens.json', { method: 'POST', body: JSON.stringify(out, null, 1) });
return { variables: out.variables.length, status: res.status };
