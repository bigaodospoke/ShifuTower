// Gera todas as texturas do ShifuTower (troféu, ícones da GUI, skins dos NPCs e ícone do mod).
// Uso: node tools/gerar_texturas.js   (sem dependências — encoder PNG próprio)
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const ROOT = path.resolve(__dirname, '..', 'src', 'main', 'resources', 'assets', 'shifutower');

// ------------------------------------------------------------------ PNG
const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();
function crc32(buf) {
  let c = 0xffffffff;
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
class Img {
  constructor(w, h) { this.w = w; this.h = h; this.d = new Uint8ClampedArray(w * h * 4); }
  get(x, y) { if (x < 0 || y < 0 || x >= this.w || y >= this.h) return [0, 0, 0, 0]; const i = (y * this.w + x) * 4; return [...this.d.slice(i, i + 4)]; }
  set(x, y, c) {
    if (x < 0 || y < 0 || x >= this.w || y >= this.h || !c) return;
    const i = (y * this.w + x) * 4; this.d[i] = c[0]; this.d[i + 1] = c[1]; this.d[i + 2] = c[2]; this.d[i + 3] = c[3] ?? 255;
  }
  rect(x, y, w, h, c) { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) this.set(x + i, y + j, typeof c === 'function' ? c(i, j) : c); }
  save(file) {
    const raw = Buffer.alloc((this.w * 4 + 1) * this.h);
    for (let y = 0; y < this.h; y++) {
      raw[y * (this.w * 4 + 1)] = 0;
      Buffer.from(this.d.buffer, y * this.w * 4, this.w * 4).copy(raw, y * (this.w * 4 + 1) + 1);
    }
    const ihdr = Buffer.alloc(13);
    ihdr.writeUInt32BE(this.w, 0); ihdr.writeUInt32BE(this.h, 4); ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
    const png = Buffer.concat([
      Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
      chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0)),
    ]);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, png);
  }
  scaled(f) {
    const o = new Img(this.w * f, this.h * f);
    for (let y = 0; y < o.h; y++) for (let x = 0; x < o.w; x++) o.set(x, y, this.get(Math.floor(x / f), Math.floor(y / f)));
    return o;
  }
}
const hex = (h, a = 255) => { const n = parseInt(h.replace('#', ''), 16); return [(n >> 16) & 255, (n >> 8) & 255, n & 255, a]; };
const shade = (c, f) => [Math.min(255, Math.round(c[0] * f)), Math.min(255, Math.round(c[1] * f)), Math.min(255, Math.round(c[2] * f)), c[3] ?? 255];
const mix = (a, b, t) => [0, 1, 2].map((i) => Math.round(a[i] + (b[i] - a[i]) * t)).concat([255]);
// Ruído determinístico para dar textura sem "piscar" entre gerações
const noise = (x, y, s = 0) => { const v = Math.sin(x * 12.9898 + y * 78.233 + s * 37.719) * 43758.5453; return v - Math.floor(v); };
const jitter = (c, x, y, amt = 0.06, s = 0) => shade(c, 1 - amt + noise(x, y, s) * amt * 2);

// ------------------------------------------------------------------ Troféu (texturas de bloco 16x16)
const GOLD = ['#4A2E05', '#7A4E08', '#A8740F', '#D49A1C', '#F2C230', '#FFDD66', '#FFF3B8'].map((h) => hex(h));

function trophyTextures() {
  // Ouro: faixas verticais de brilho (ficam bonitas em qualquer pedaço de UV do modelo)
  const ouro = new Img(16, 16);
  const cols = [2, 3, 4, 5, 6, 5, 4, 4, 3, 4, 5, 4, 3, 3, 2, 2];
  ouro.rect(0, 0, 16, 16, (x, y) => {
    let c = GOLD[cols[x]];
    if (y >= 13) c = shade(c, 0.88);
    if (noise(x, y, 1) > 0.94) c = GOLD[6];
    return jitter(c, x, y, 0.04, 2);
  });
  ouro.save(path.join(ROOT, 'textures/block/trofeu_ouro.png'));

  // Interior da taça: escuro no fundo, clareando para a borda
  const escuro = new Img(16, 16);
  escuro.rect(0, 0, 16, 16, (x, y) => {
    const d = Math.hypot(x - 7.5, y - 7.5) / 7.5;
    return jitter(mix(GOLD[0], GOLD[2], Math.min(1, d * 0.9)), x, y, 0.05, 3);
  });
  escuro.save(path.join(ROOT, 'textures/block/trofeu_ouro_escuro.png'));

  // Base: obsidiana arroxeada com frisos dourados (os quadrados batem com as UVs do modelo)
  const base = new Img(16, 16);
  const B = ['#0E0818', '#1A1030', '#271848', '#3A2766'].map((h) => hex(h));
  base.rect(0, 0, 16, 16, (x, y) => {
    let c = B[Math.floor(noise(x, y, 4) * 3)];
    if (noise(x, y, 5) > 0.9) c = B[3];
    return c;
  });
  const goldLine = (x0, y0, x1, y1) => { for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) base.set(x, y, jitter(GOLD[4], x, y, 0.08, 6)); };
  goldLine(3, 3, 12, 3); goldLine(3, 12, 12, 12); goldLine(3, 3, 3, 12); goldLine(12, 3, 12, 12);
  goldLine(4, 4, 11, 4); goldLine(4, 11, 11, 11); goldLine(4, 4, 4, 11); goldLine(11, 4, 11, 11);
  // Friso das laterais (linha 14) usado pelas UVs [x, 14, x, 15.5] do modelo
  goldLine(0, 14, 15, 14);
  base.save(path.join(ROOT, 'textures/block/trofeu_base.png'));

  // Placa (linhas 0-3): moldura dourada com "inscrição" gravada
  const placa = new Img(16, 16);
  placa.rect(0, 0, 16, 16, GOLD[3]);
  placa.rect(0, 0, 16, 1, GOLD[5]);
  placa.rect(0, 3, 16, 1, GOLD[1]);
  placa.rect(0, 0, 1, 4, GOLD[2]); placa.rect(15, 0, 1, 4, GOLD[2]);
  for (let x = 2; x <= 13; x++) {
    if (noise(x, 1, 7) > 0.35) placa.set(x, 1, GOLD[1]);
    if (x > 3 && x < 12 && noise(x, 2, 8) > 0.5) placa.set(x, 2, GOLD[1]);
  }
  placa.save(path.join(ROOT, 'textures/block/trofeu_placa.png'));

  // Emblema: Poké Bola sobre fundo dourado
  const em = new Img(16, 16);
  const red = hex('#E3350D'), redL = hex('#FF6A45'), white = hex('#F4F4F4'), whiteS = hex('#C9C9D1'), black = hex('#15101A');
  em.rect(0, 0, 16, 16, (x, y) => jitter(GOLD[3], x, y, 0.05, 9));
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const d = Math.hypot(x - 7.5, y - 7.5);
    if (d > 7.6) continue;
    let c;
    if (d > 6.6) c = black;
    else if (y === 7 || y === 8) c = black;
    else if (y < 7) c = (x < 6 && y < 4) ? redL : red;
    else c = (x > 9 && y > 11) ? whiteS : white;
    const bd = Math.hypot(x - 7.5, y - 7.5);
    if (bd <= 2.6) c = black;
    if (bd <= 1.6) c = white;
    em.set(x, y, c);
  }
  em.set(4, 3, hex('#FFC2B0'));
  em.save(path.join(ROOT, 'textures/block/trofeu_emblema.png'));
}

// ------------------------------------------------------------------ Ícones da GUI (atlas 64x64, 16 ícones)
const OUT = hex('#1A0E0A');
function autoOutline(img, ox, oy, color = OUT) {
  const marks = [];
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    if (img.get(ox + x, oy + y)[3] !== 0) continue;
    const n = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => {
      const nx = x + dx, ny = y + dy;
      if (nx < 0 || ny < 0 || nx > 15 || ny > 15) return false;
      const p = img.get(ox + nx, oy + ny);
      return p[3] !== 0 && !(p[0] === color[0] && p[1] === color[1] && p[2] === color[2]);
    });
    if (n) marks.push([x, y]);
  }
  for (const [x, y] of marks) img.set(ox + x, oy + y, color);
}
function ascii(img, ox, oy, rows, pal) {
  rows.forEach((row, y) => {
    if (row.length !== 16) throw new Error(`linha ${y} com ${row.length} colunas: "${row}"`);
    [...row].forEach((ch, x) => { if (ch !== '.') img.set(ox + x, oy + y, pal[ch]); });
  });
}
function line(img, x0, y0, x1, y1, c) {
  const n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
  for (let i = 0; i <= n; i++) img.set(Math.round(x0 + (x1 - x0) * i / n), Math.round(y0 + (y1 - y0) * i / n), c);
}
function disc(img, cx, cy, r, c) {
  for (let y = Math.floor(cy - r); y <= Math.ceil(cy + r); y++) for (let x = Math.floor(cx - r); x <= Math.ceil(cx + r); x++) {
    if (Math.hypot(x - cx, y - cy) <= r) img.set(x, y, typeof c === 'function' ? c(x, y) : c);
  }
}

function icons() {
  const img = new Img(64, 64);
  const P = {
    y: GOLD[4], Y: GOLD[5], o: GOLD[2], O: GOLD[1], w: hex('#F6F3FF'), g: hex('#A8A3B8'), G: hex('#5BD47A'), D: hex('#1F7A3C'),
    r: hex('#E5484D'), R: hex('#8E1F27'), s: hex('#E3E8F2'), S: hex('#8D96AA'), c: hex('#E39A5B'), C: hex('#8C5426'),
    p: hex('#8A5CF0'), P: hex('#4A2C8C'), b: hex('#4A90E2'), B: hex('#1E4F8F'), n: hex('#8A5A2B'), N: hex('#4F3015'),
    f: hex('#FF9A3C'), F: hex('#FFD34D'), k: OUT, a: hex('#6FE3F2'),
  };
  const at = (i) => [(i % 4) * 16, Math.floor(i / 4) * 16];

  // 0 troféu
  {
    const [ox, oy] = at(0);
    ascii(img, ox, oy, [
      '................',
      '................',
      '...YYYYYYYYYY...',
      '.yy.YYyyyyyyo.yy',
      '.y..Yyyyyyyyo..y',
      '.y..Yyyyyyyyo..y',
      '..y.yyyyyyyyo.y.',
      '...yyyyyyyyoo...',
      '....yyyyyyoo....',
      '......yyoo......',
      '.......yo.......',
      '.......yo.......',
      '.....yyyooo.....',
      '....PPPPPPPP....',
      '....PppppppP....',
      '................',
    ], P);
    autoOutline(img, ox, oy);
  }
  // 1 moeda BP
  {
    const [ox, oy] = at(1);
    disc(img, ox + 7.5, oy + 7.5, 6.5, (x, y) => (x - ox < 6 && y - oy < 6) ? GOLD[5] : (x - ox > 9 && y - oy > 9 ? GOLD[3] : GOLD[4]));
    ascii(img, ox, oy, [
      '................', '................', '................', '................',
      '.....OOOO.......', '.....O...O......', '.....O...O......', '.....OOOO.......',
      '.....O...O......', '.....O...O......', '.....OOOO.......',
      '................', '................', '................', '................', '................',
    ], P);
    for (let y = 4; y <= 10; y++) img.set(ox + 10, oy + y, GOLD[5]);
    autoOutline(img, ox, oy);
  }
  // 2 relógio
  {
    const [ox, oy] = at(2);
    disc(img, ox + 7.5, oy + 7.5, 6.5, P.w);
    disc(img, ox + 7.5, oy + 7.5, 6.5, (x, y) => Math.hypot(x - ox - 7.5, y - oy - 7.5) > 5.4 ? P.b : (y - oy > 10 ? P.g : P.w));
    line(img, ox + 7, oy + 3, ox + 7, oy + 8, OUT);
    line(img, ox + 7, oy + 8, ox + 11, oy + 8, OUT);
    img.set(ox + 7, oy + 2, P.B);
    autoOutline(img, ox, oy);
  }
  // 3 coroa
  {
    const [ox, oy] = at(3);
    ascii(img, ox, oy, [
      '................',
      '................',
      '.......Y........',
      '..Y...yyy...Y...',
      '..yy..yyy..yy...',
      '..yyy.yyy.yyy...',
      '..yyyyyyyyyyy...',
      '..yyyyyyyyyyy...',
      '..yrryyGGyybb...',
      '..yrryyGGyybb...',
      '..yyyyyyyyyyy...',
      '..YYYYYYYYYYY...',
      '..ooooooooooo...',
      '................',
      '................',
      '................',
    ].map((r) => '.' + r.slice(0, 15)), P);
    autoOutline(img, ox, oy);
  }
  // 4 espadas cruzadas
  {
    const [ox, oy] = at(4);
    ascii(img, ox, oy, [
      '................',
      '.ww..........ww.',
      '.wsw........wsw.',
      '..wsw......wsw..',
      '...wsw....wsw...',
      '....wsw..wsw....',
      '.....wswwsw.....',
      '......wssw......',
      '......wssw......',
      '.....wswwsw.....',
      '..yy.ws..sw.yy..',
      '..yyyw....wyyy..',
      '...ny......yn...',
      '..nn........nn..',
      '.NN..........NN.',
      '................',
    ], P);
    autoOutline(img, ox, oy);
  }
  // 5 sacola da loja
  {
    const [ox, oy] = at(5);
    ascii(img, ox, oy, [
      '................',
      '................',
      '......nnnn......',
      '.....n....n.....',
      '.....n....n.....',
      '...pppppppppp...',
      '...pPPPPPPPPp...',
      '...ppppppppPp...',
      '...pppYYpppPp...',
      '...ppYyyYppPp...',
      '...ppYyyYppPp...',
      '...pppYYpppPp...',
      '...ppppppppPp...',
      '...PPPPPPPPPP...',
      '................',
      '................',
    ], P);
    autoOutline(img, ox, oy);
  }
  // 6 torre
  {
    const [ox, oy] = at(6);
    ascii(img, ox, oy, [
      '.......Y........',
      '......yYy.......',
      '.....pppPP......',
      '....ppppPPP.....',
      '.....sssSS......',
      '.....sYsSS......',
      '.....sssSS......',
      '....sssssSS.....',
      '....ssYsYSS.....',
      '....sssssSS.....',
      '...ssssssSSS....',
      '...ssYssYsSS....',
      '...ssssssSSS....',
      '..sssssnnsSSS...',
      '..sssssnnsSSS...',
      '................',
    ], P);
    autoOutline(img, ox, oy);
  }
  // 7 estrela
  {
    const [ox, oy] = at(7);
    ascii(img, ox, oy, [
      '................',
      '.......Y........',
      '.......Y........',
      '......YyY.......',
      '......yyy.......',
      '.YYYYyyyyyyyyo..',
      '..Yyyyyyyyyyo...',
      '...yyyyyyyyo....',
      '....yyyyyyo.....',
      '....yyyoyyo.....',
      '...yyyo.oyyo....',
      '...yyo...oyo....',
      '..yo.......oo...',
      '................',
      '................',
      '................',
    ], P);
    autoOutline(img, ox, oy);
  }
  // 8-10 medalhas
  const medal = (i, c1, c2, c3) => {
    const [ox, oy] = at(i);
    ascii(img, ox, oy, [
      '................', '...rr.....bb....', '...rRr...bBb....', '....rRr.bBb.....', '.....rR.bB......',
      '................', '................', '................', '................', '................',
      '................', '................', '................', '................', '................', '................',
    ], P);
    disc(img, ox + 7, oy + 9.5, 4.5, (x, y) => (x - ox < 7 && y - oy < 9) ? c1 : (x - ox > 8 && y - oy > 10 ? c3 : c2));
    autoOutline(img, ox, oy);
  };
  medal(8, GOLD[5], GOLD[4], GOLD[2]);
  medal(9, hex('#FFFFFF'), P.s, P.S);
  medal(10, hex('#F2B47E'), P.c, P.C);
  // 11 cadeado
  {
    const [ox, oy] = at(11);
    ascii(img, ox, oy, [
      '................', '................', '.....gggg.......', '....g....g......', '....g....g......', '....g....g......',
      '...yyyyyyyy.....', '...yYYYYYYo.....', '...yyyOOyyo.....', '...yyyOOyyo.....', '...yyyyOyyo.....', '...yyyyyyyo.....',
      '...oooooooo.....', '................', '................', '................',
    ].map((r) => '..' + r.slice(0, 14)), P);
    autoOutline(img, ox, oy);
  }
  // 12 check
  {
    const [ox, oy] = at(12);
    for (let i = 0; i < 4; i++) { img.set(ox + 3 + i, oy + 7 + i, P.G); img.set(ox + 3 + i, oy + 8 + i, P.G); }
    for (let i = 0; i < 7; i++) { img.set(ox + 7 + i, oy + 10 - i, P.G); img.set(ox + 7 + i, oy + 11 - i, P.D); }
    autoOutline(img, ox, oy);
  }
  // 13 X
  {
    const [ox, oy] = at(13);
    for (let i = 0; i < 9; i++) {
      img.set(ox + 3 + i, oy + 3 + i, P.r); img.set(ox + 4 + i, oy + 3 + i, P.r);
      img.set(ox + 12 - i, oy + 3 + i, P.r); img.set(ox + 11 - i, oy + 3 + i, P.R);
    }
    autoOutline(img, ox, oy);
  }
  // 14 chama
  {
    const [ox, oy] = at(14);
    ascii(img, ox, oy, [
      '................',
      '.......r........',
      '......rr........',
      '......rfr.......',
      '.....rffr..r....',
      '....rrffrr.rr...',
      '....rfffffrfr...',
      '...rfffFffffr...',
      '...rffFFFfffr...',
      '...rfFFwFFffr...',
      '...rfFwwwFffr...',
      '....rFFwFFfr....',
      '.....rFFFfr.....',
      '......rrrr......',
      '................',
      '................',
    ], P);
    autoOutline(img, ox, oy);
  }
  // 15 caveira
  {
    const [ox, oy] = at(15);
    ascii(img, ox, oy, [
      '................',
      '................',
      '.....wwwwww.....',
      '....wwwwwwwg....',
      '...wwwwwwwwwg...',
      '...wwkkwwkkwg...',
      '...wwkkwwkkwg...',
      '...wwwwwwwwwg...',
      '....wwwkkwwg....',
      '....wwwwwwwg....',
      '.....wkwkwg.....',
      '.....wgwgwg.....',
      '................',
      '................',
      '................',
      '................',
    ], P);
    autoOutline(img, ox, oy);
  }
  img.save(path.join(ROOT, 'textures/gui/icones.png'));
  return img;
}

// ------------------------------------------------------------------ Skins dos NPCs (layout padrão 64x64)
// Cada parte: [u, v, largura, altura, profundidade] no formato de "box UV" do Minecraft
const PARTS = {
  head: [0, 0, 8, 8, 8], hat: [32, 0, 8, 8, 8],
  body: [16, 16, 8, 12, 4], jacket: [16, 32, 8, 12, 4],
  armR: [40, 16, 4, 12, 4], sleeveR: [40, 32, 4, 12, 4],
  armL: [32, 48, 4, 12, 4], sleeveL: [48, 48, 4, 12, 4],
  legR: [0, 16, 4, 12, 4], pantsR: [0, 32, 4, 12, 4],
  legL: [16, 48, 4, 12, 4], pantsL: [0, 48, 4, 12, 4],
};
const FACE_LIGHT = { front: 1.0, right: 0.86, left: 0.86, back: 0.78, top: 1.08, bottom: 0.7 };

// Pinta uma parte chamando fn(face, x, y, w, h) -> cor | null
function paintPart(img, part, fn, mirror = false) {
  const [u, v, w, h, d] = PARTS[part];
  const faces = {
    top: [u + d, v, w, d], bottom: [u + d + w, v, w, d],
    right: [u, v + d, d, h], front: [u + d, v + d, w, h], left: [u + d + w, v + d, d, h], back: [u + d + w + d, v + d, w, h],
  };
  for (const [face, [fx, fy, fw, fh]] of Object.entries(faces)) {
    for (let y = 0; y < fh; y++) for (let x = 0; x < fw; x++) {
      const lx = mirror ? fw - 1 - x : x;
      const c = fn(face, lx, y, fw, fh);
      if (!c) continue;
      const lit = c[3] === 0 ? c : jitter(shade(c, FACE_LIGHT[face] * (1.04 - (y / fh) * 0.08)), fx + x, fy + y, 0.035, part.length);
      img.set(fx + x, fy + y, lit);
    }
  }
}

function skin(spec) {
  const img = new Img(64, 64);
  const S = spec;
  const skinC = hex(S.skin);
  const hair = S.hair ? hex(S.hair) : null;
  const hairDark = hair ? shade(hair, 0.75) : null;

  // ---- cabeça
  paintPart(img, 'head', (face, x, y, w, h) => {
    if (face === 'top') return S.hairTop === false ? skinC : (hair || skinC);
    if (face === 'bottom') return shade(skinC, 0.9);
    if (face === 'back') {
      if (!hair) return skinC;
      if (S.hairStyle === 'bald') return y < 3 ? skinC : (y < 6 ? hair : skinC);
      return y < (S.hairStyle === 'long' || S.hairStyle === 'ponytail' ? 8 : 6) ? (y > 5 ? hairDark : hair) : skinC;
    }
    if (face === 'right' || face === 'left') {
      if (!hair) return skinC;
      const ear = (y === 4 || y === 5) && (x === 3 || x === 4);
      if (S.hairStyle === 'bald') return y >= 3 && y <= 5 && x > 3 ? hair : (ear ? shade(skinC, 0.85) : skinC);
      const long = S.hairStyle === 'long' || S.hairStyle === 'ponytail';
      if (y < 2) return hair;
      if (y < 3) return x > 1 ? hair : skinC;
      if (long && (x > 3 || y > 5)) return y > 5 ? hairDark : hair;
      if (x > 5 && y < 6) return hair;
      return ear ? shade(skinC, 0.85) : skinC;
    }
    // frente (rosto)
    if (hair && S.hairStyle !== 'bald') {
      if (y === 0) return hair;
      if (y === 1) return (S.fringe ?? [1, 1, 1, 1, 1, 1, 1, 1])[x] ? hair : skinC;
      if (S.hairStyle === 'long' && (x === 0 || x === 7) && y < 7) return hairDark;
    }
    if (y === 3 && S.brows && (x === 1 || x === 2 || x === 5 || x === 6)) return hex(S.brows);
    if (y === 4) {
      if (S.glasses && (x >= 1 && x <= 6)) return x === 3 || x === 4 ? hex(S.glasses) : (x === 1 || x === 6 ? hex(S.glasses) : hex('#CFE9FF'));
      if (x === 1 || x === 6) return hex('#FFFFFF');
      if (x === 2 || x === 5) return hex(S.eyes);
    }
    if (y === 5 && (x === 3 || x === 4)) return shade(skinC, 0.88);
    if (S.mustache && y === 5 && x >= 2 && x <= 5) return hex(S.mustache);
    if (S.beard && y >= 5 && (y > 5 || x <= 1 || x >= 6)) return y === 7 && (x === 0 || x === 7) ? skinC : hex(S.beard);
    if (y === 6 && (x === 3 || x === 4)) return hex(S.mouth || '#9C5B4A');
    return skinC;
  });
  if (S.beard) {
    paintPart(img, 'head', (face, x, y) => (face === 'right' || face === 'left') && y >= 5 && x < 3 ? hex(S.beard) : null);
  }

  // ---- chapéu / acessório de cabeça (camada externa)
  if (S.hat) S.hat(img);

  // ---- corpo
  const shirt = hex(S.shirt);
  const jacket = S.jacket ? hex(S.jacket) : null;
  const trim = S.trim ? hex(S.trim) : null;
  paintPart(img, 'body', (face, x, y, w, h) => {
    if (S.body) { const c = S.body(face, x, y, w, h); if (c) return c; }
    if (y === 10 && S.belt && face !== 'top' && face !== 'bottom') return x === 3 && face === 'front' && S.buckle ? hex(S.buckle) : hex(S.belt);
    if (y === 11 && S.pants) return hex(S.pants);
    if (jacket && face === 'front') {
      if (x <= 2 || x >= 5) return trim && (x === 2 || x === 5) ? trim : jacket;
      if (S.tie && (x === 3 || x === 4) && y >= 1 && y <= 6) return hex(S.tie);
      return shirt;
    }
    if (jacket && face !== 'front') return jacket;
    if (face === 'front' && S.neckline && y < 3 && (x === 3 || x === 4 || (y < 2 && (x === 2 || x === 5)))) return skinC;
    return shirt;
  });

  // ---- braços
  const sleeveLen = S.sleeve ?? 10;
  const armColor = jacket || shirt;
  const arm = (face, x, y) => {
    if (face === 'top') return armColor;
    if (y >= sleeveLen) {
      if (S.gloves) return hex(S.gloves);
      return face === 'bottom' ? shade(skinC, 0.9) : skinC;
    }
    if (S.cuff && y === sleeveLen - 1) return hex(S.cuff);
    if (trim && y === sleeveLen - 1) return trim;
    return armColor;
  };
  paintPart(img, 'armR', arm);
  paintPart(img, 'armL', arm, true);

  // ---- pernas
  const pants = hex(S.pants);
  const shoes = hex(S.shoes);
  const shoeTop = S.shoeTop ?? 10;
  const leg = (face, x, y, w, h) => {
    if (S.legs) { const c = S.legs(face, x, y, w, h); if (c) return c; }
    if (face === 'top') return pants;
    if (face === 'bottom') return shade(shoes, 0.8);
    if (y >= shoeTop) return y === shoeTop && S.shoeTrim ? hex(S.shoeTrim) : shoes;
    return pants;
  };
  paintPart(img, 'legR', leg);
  paintPart(img, 'legL', leg, true);

  if (S.extra) S.extra(img);
  return img;
}

// Camada de chapéu: preenche a "casca" externa da cabeça
function hatLayer(fn) {
  return (img) => paintPart(img, 'hat', (face, x, y, w, h) => {
    const c = fn(face, x, y, w, h);
    return c || [0, 0, 0, 0];
  });
}
function overlay(part, fn, mirror = false) {
  return (img) => paintPart(img, part, (face, x, y, w, h) => fn(face, x, y, w, h) || [0, 0, 0, 0], mirror);
}

const TRAINERS = {
  as_treinador: {
    skin: '#D9A066', hair: '#3B2416', hairStyle: 'short', fringe: [1, 1, 0, 1, 1, 0, 1, 1], eyes: '#2B5DA8', brows: '#3B2416',
    shirt: '#22202B', jacket: '#D7263D', trim: '#F4F4F4', pants: '#1F3A6B', shoes: '#D7263D', shoeTrim: '#F4F4F4',
    gloves: '#1A1A1A', sleeve: 9, belt: '#1A1A1A', buckle: '#F2C230',
    hat: hatLayer((face, x, y) => {
      const red = hex('#D7263D'), white = hex('#F4F4F4');
      if (face === 'top') return y < 6 ? red : white;
      if (face === 'bottom') return null;
      if (y > 2) return null;
      if (face === 'front') return y === 2 ? hex('#1A1A1A') : ((x === 3 || x === 4) && y === 1 ? white : red);
      return y === 2 ? hex('#B21E33') : red;
    }),
  },
  veterana: {
    skin: '#E8B88A', hair: '#E9E4EF', hairStyle: 'long', eyes: '#5A3E8A', brows: '#C9C2D6', mouth: '#B0626B',
    shirt: '#E8DCC2', jacket: '#5B2A86', trim: '#C9A0FF', pants: '#3A3346', shoes: '#2A2230', sleeve: 11, belt: '#C9A0FF',
    hat: hatLayer((face, x, y) => face === 'back' && y >= 1 && y <= 3 && x >= 2 && x <= 5 ? hex('#F6F2FA') : (face === 'top' && x >= 2 && x <= 5 && y >= 4 ? hex('#F6F2FA') : null)),
    extra: overlay('jacket', (face, x, y) => (face === 'front' || face === 'back' || face === 'left' || face === 'right') && y <= 2 ? hex('#7B3FB0') : null),
  },
  faixa_preta: {
    skin: '#C68642', hair: '#1A1A1A', hairStyle: 'bald', hairTop: false, eyes: '#2A1A10', brows: '#1A1A1A',
    shirt: '#F7F5EF', pants: '#F7F5EF', shoes: '#C68642', shoeTop: 11, belt: '#141414', sleeve: 8, neckline: true,
    hat: hatLayer((face, x, y) => (face !== 'top' && face !== 'bottom' && y === 2) ? hex('#D7263D') : (face === 'back' && y === 3 && (x === 3 || x === 4) ? hex('#D7263D') : null)),
    body: (face, x, y) => (face === 'front' && y < 6 && (x === 3 - Math.floor(y / 2) || x === 4 + Math.floor(y / 2))) ? hex('#D8D4CA') : null,
  },
  cientista: {
    skin: '#F1C27D', hair: '#9A9AA6', hairStyle: 'short', fringe: [1, 0, 1, 1, 0, 1, 1, 0], eyes: '#3A3A3A', brows: '#7A7A86', glasses: '#2A2A2A',
    shirt: '#3BA3A3', jacket: '#F4F7FA', trim: '#DDE3EA', tie: '#C0392B', pants: '#6B4A2E', shoes: '#1E1E22', sleeve: 11, belt: null,
    extra: (img) => {
      // Jaleco comprido: cobre a parte de cima das pernas
      overlay('pantsR', (face, x, y) => face !== 'top' && face !== 'bottom' && y < 5 ? hex('#F4F7FA') : null)(img);
      overlay('pantsL', (face, x, y) => face !== 'top' && face !== 'bottom' && y < 5 ? hex('#F4F7FA') : null, true)(img);
    },
  },
  domadora_dragoes: {
    skin: '#E0AC69', hair: '#E2572B', hairStyle: 'ponytail', eyes: '#1F8A70', brows: '#B5401C', mouth: '#A6524A',
    shirt: '#1F4E5A', jacket: '#123940', trim: '#F2C230', pants: '#123940', shoes: '#2A1A12', shoeTop: 8, gloves: '#2A1A12', sleeve: 9,
    belt: '#F2C230',
    hat: hatLayer((face, x, y) => face === 'back' && y >= 2 && y <= 7 && (x === 3 || x === 4) ? hex('#C0461F') : (face !== 'top' && face !== 'bottom' && y === 1 ? hex('#F2C230') : null)),
    extra: overlay('jacket', (face, x, y) => face === 'back' && y < 11 ? hex('#7A1F2B') : null),
  },
  psiquica: {
    skin: '#F3D3B8', hair: '#1B1423', hairStyle: 'long', eyes: '#C2185B', brows: '#1B1423', mouth: '#C2185B',
    shirt: '#C2378C', pants: '#3C1E4F', shoes: '#E36BB0', shoeTop: 10, sleeve: 7, belt: '#7B1F5C',
    hat: hatLayer((face, x, y) => face === 'front' && y === 1 ? ((x === 3 || x === 4) ? hex('#7CF2FF') : hex('#F2C230')) : (face !== 'top' && face !== 'bottom' && y === 1 ? hex('#F2C230') : null)),
    legs: (face, x, y) => y < 4 && face !== 'top' ? hex('#C2378C') : null,
  },
  cavalheiro: {
    skin: '#E8B88A', hair: '#5A3A22', hairStyle: 'short', eyes: '#3A2A1A', brows: '#5A3A22', mustache: '#5A3A22',
    shirt: '#F4F4F4', jacket: '#1C1C24', trim: '#2E2E3A', tie: '#B3132B', pants: '#1C1C24', shoes: '#6B3E1E', sleeve: 11, cuff: '#F4F4F4',
    hat: hatLayer((face, x, y) => {
      if (face === 'top') return hex('#121216');
      if (face === 'bottom') return null;
      if (y <= 1) return hex('#121216');
      if (y === 2) return hex('#8E1F2B');
      return null;
    }),
  },
  mestre_shifu: {
    skin: '#D2A06D', hair: '#F2F0EA', hairStyle: 'bald', hairTop: false, eyes: '#2A1A10', brows: '#FFFFFF', beard: '#F2F0EA', mouth: '#7A4A3A',
    shirt: '#B3132B', pants: '#1A1418', shoes: '#141014', shoeTrim: '#F2C230', sleeve: 10, gloves: null, cuff: '#F2C230', belt: '#F2C230', buckle: '#FFF3B8',
    body: (face, x, y) => {
      if (face === 'front' && (x === 3 || x === 4) && y < 9) return hex('#F2C230');
      if (face === 'front' && y < 2 && (x === 2 || x === 5)) return hex('#F2C230');
      if (face === 'back' && y >= 2 && y <= 6 && x >= 2 && x <= 5) return (x + y) % 2 === 0 ? hex('#F2C230') : hex('#8E0F22');
      return null;
    },
    extra: (img) => {
      // Mangas largas do manto e barra dourada
      overlay('sleeveR', (face, x, y) => face !== 'top' && y < 10 ? (y === 9 ? hex('#F2C230') : hex('#C21A33')) : null)(img);
      overlay('sleeveL', (face, x, y) => face !== 'top' && y < 10 ? (y === 9 ? hex('#F2C230') : hex('#C21A33')) : null, true)(img);
      overlay('pantsR', (face, x, y) => face !== 'top' && face !== 'bottom' && y < 4 ? (y === 3 ? hex('#F2C230') : hex('#B3132B')) : null)(img);
      overlay('pantsL', (face, x, y) => face !== 'top' && face !== 'bottom' && y < 4 ? (y === 3 ? hex('#F2C230') : hex('#B3132B')) : null, true)(img);
      // Barba longa caindo sobre o peito
      overlay('jacket', (face, x, y) => face === 'front' && y < 3 && x >= 2 && x <= 5 ? hex('#F2F0EA') : null)(img);
    },
  },
  recepcionista: {
    skin: '#F1C27D', hair: '#6B3A1E', hairStyle: 'long', eyes: '#3D7A3A', brows: '#6B3A1E', mouth: '#C0596A',
    shirt: '#F4F4F4', jacket: '#1E6F8F', trim: '#F2C230', pants: '#174E66', shoes: '#141418', shoeTop: 10, sleeve: 11, belt: null,
    hat: hatLayer((face, x, y) => {
      if (face === 'top') return x >= 1 && x <= 6 && y >= 1 && y <= 6 ? hex('#1E6F8F') : null;
      if (face === 'bottom') return null;
      if (y === 0) return hex('#1E6F8F');
      if (face === 'front' && y === 1 && (x === 3 || x === 4)) return hex('#F2C230');
      return null;
    }),
    legs: (face, x, y) => y >= 4 && y < 10 && face !== 'top' ? hex('#E6C7A3') : null,
  },
};

function skins() {
  for (const [name, spec] of Object.entries(TRAINERS)) {
    skin(spec).save(path.join(ROOT, `textures/npcs/${name}.png`));
  }
}

// ------------------------------------------------------------------ Ícone do mod (128x128)
function modIcon(iconAtlas) {
  const out = new Img(128, 128);
  const bgTop = hex('#3D2575'), bgBot = hex('#140B26');
  for (let y = 0; y < 128; y++) for (let x = 0; x < 128; x++) {
    const corner = (x < 6 || x > 121) && (y < 6 || y > 121) && Math.hypot(Math.min(x, 127 - x) - 6, Math.min(y, 127 - y) - 6) > 6;
    if (corner) continue;
    const edge = Math.min(x, y, 127 - x, 127 - y);
    let c = mix(bgTop, bgBot, y / 127);
    if (edge < 4) c = edge < 2 ? hex('#07030F') : GOLD[3];
    out.set(x, y, c);
  }
  // Raios de luz
  for (let i = 0; i < 12; i++) {
    const a = i * Math.PI / 6;
    for (let r = 18; r < 58; r++) {
      const px = Math.round(64 + Math.cos(a) * r), py = Math.round(60 + Math.sin(a) * r);
      const p = out.get(px, py);
      if (p[3]) out.set(px, py, mix(p, GOLD[5], 0.25 * (1 - r / 58)));
    }
  }
  // Troféu ampliado (ícone 0 do atlas) x6
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const c = iconAtlas.get(x, y);
    if (!c[3]) continue;
    out.rect(16 + x * 6, 10 + y * 6, 6, 6, c);
  }
  out.save(path.join(ROOT, 'icon.png'));
}

trophyTextures();
const atlas = icons();
skins();
modIcon(atlas);

// Pré-visualizações ampliadas para conferir (não vão para o mod)
const preview = process.argv[2];
if (preview) {
  const load = (f) => {
    // decoder mínimo: só para os PNGs que nós mesmos geramos (RGBA, filtro 0)
    const buf = fs.readFileSync(f);
    const w = buf.readUInt32BE(16), h = buf.readUInt32BE(20);
    let idat = []; let o = 8;
    while (o < buf.length) { const len = buf.readUInt32BE(o); const type = buf.toString('ascii', o + 4, o + 8); if (type === 'IDAT') idat.push(buf.slice(o + 8, o + 8 + len)); o += 12 + len; }
    const raw = zlib.inflateSync(Buffer.concat(idat)); const img = new Img(w, h);
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const i = y * (w * 4 + 1) + 1 + x * 4; img.set(x, y, [raw[i], raw[i + 1], raw[i + 2], raw[i + 3]]); }
    return img;
  };
  fs.mkdirSync(preview, { recursive: true });
  load(path.join(ROOT, 'textures/gui/icones.png')).scaled(8).save(path.join(preview, 'icones.png'));
  for (const n of ['trofeu_ouro', 'trofeu_ouro_escuro', 'trofeu_base', 'trofeu_placa', 'trofeu_emblema']) {
    load(path.join(ROOT, `textures/block/${n}.png`)).scaled(16).save(path.join(preview, `${n}.png`));
  }
  const all = Object.keys(TRAINERS);
  const sheet = new Img(all.length * 20, 34);
  sheet.rect(0, 0, sheet.w, sheet.h, hex('#2A2440'));
  all.forEach((n, i) => {
    const sk = load(path.join(ROOT, "textures/npcs/" + n + ".png"));
    const ox = i * 20 + 2, oy = 1;
    const blit = (sx, sy, w, h, dx, dy) => { for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const c = sk.get(sx + x, sy + y); if (c[3]) sheet.set(ox + dx + x, oy + dy + y, c); } };
    blit(8, 8, 8, 8, 4, 0); blit(40, 8, 8, 8, 4, 0);
    blit(20, 20, 8, 12, 4, 8); blit(20, 36, 8, 12, 4, 8);
    blit(44, 20, 4, 12, 0, 8); blit(44, 36, 4, 12, 0, 8);
    blit(36, 52, 4, 12, 12, 8); blit(52, 52, 4, 12, 12, 8);
    blit(4, 20, 4, 12, 4, 20); blit(4, 36, 4, 12, 4, 20);
    blit(20, 52, 4, 12, 8, 20); blit(4, 52, 4, 12, 8, 20);
  });
  sheet.scaled(8).save(path.join(preview, 'skins_frente.png'));
  const back = new Img(all.length * 20, 34);
  back.rect(0, 0, back.w, back.h, hex('#2A2440'));
  all.forEach((n, i) => {
    const sk = load(path.join(ROOT, "textures/npcs/" + n + ".png"));
    const ox = i * 20 + 2, oy = 1;
    const blit = (sx, sy, w, h, dx, dy) => { for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const c = sk.get(sx + x, sy + y); if (c[3]) back.set(ox + dx + x, oy + dy + y, c); } };
    blit(24, 8, 8, 8, 4, 0); blit(56, 8, 8, 8, 4, 0);
    blit(32, 20, 8, 12, 4, 8); blit(32, 36, 8, 12, 4, 8);
    blit(44, 52, 4, 12, 0, 8); blit(60, 52, 4, 12, 0, 8);
    blit(52, 20, 4, 12, 12, 8); blit(52, 36, 4, 12, 12, 8);
    blit(28, 52, 4, 12, 4, 20); blit(12, 52, 4, 12, 4, 20);
    blit(12, 20, 4, 12, 8, 20); blit(12, 36, 4, 12, 8, 20);
  });
  back.scaled(8).save(path.join(preview, 'skins_costas.png'));
}
console.log('Texturas geradas em', ROOT);
