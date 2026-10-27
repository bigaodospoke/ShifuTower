// Gera src/main/resources/shifutower/defaults/times.json a partir dos dados do Showdown
// que o Cobblemon usa (tiers oficiais + sets de Random Battle) e dos dados de espécies do Cobblemon.
//
// Uso:
//   node tools/gerar_times.js <pasta showdown> <species.json> <saida.json>
//
// <pasta showdown>  = pasta "showdown" de uma instância com Cobblemon (tem data/moves.js, formats-data.js...)
// <species.json>    = resumo das espécies do Cobblemon (nome -> {impl, legend, bst, abil})
const fs = require('fs');
const path = require('path');

const [sdDir, speciesFile, outFile] = process.argv.slice(2);
const req = (f) => require(path.join(sdDir, 'data', f));
const formats = req('formats-data.js').FormatsData;
const dex = req('pokedex.js').Pokedex;
const moves = req('moves.js').Moves;
const randomSets = require(path.join(sdDir, 'data', 'random-sets.json'));
const cobblemon = require(path.resolve(speciesFile));

const toId = (s) => s.toLowerCase().replace(/[^a-z0-9]/g, '');

// Itens existentes no Cobblemon 1.8.1 (conferidos em assets/cobblemon/models/item)
const ITEMS_BY_ROLE = {
  'Fast Attacker': ['life_orb', 'choice_scarf', 'focus_sash', 'expert_belt'],
  'Setup Sweeper': ['life_orb', 'lum_berry', 'focus_sash', 'white_herb'],
  'Bulky Attacker': ['leftovers', 'assault_vest', 'sitrus_berry', 'rocky_helmet'],
  'Bulky Support': ['leftovers', 'rocky_helmet', 'heavy_duty_boots', 'sitrus_berry'],
  'Fast Support': ['focus_sash', 'leftovers', 'heavy_duty_boots', 'light_clay'],
  'Wallbreaker': ['life_orb', 'choice_band', 'choice_specs', 'expert_belt'],
  'Bulky Setup': ['leftovers', 'sitrus_berry', 'weakness_policy', 'lum_berry'],
  'Fast Bulky Setup': ['leftovers', 'lum_berry', 'weakness_policy'],
  'Tera Blast user': ['life_orb', 'leftovers'],
};
const DEFAULT_ITEMS = ['leftovers', 'life_orb', 'sitrus_berry'];

function category(moveId) {
  const m = moves[moveId];
  return m ? m.category : null;
}

function buildEntry(species, set) {
  const movepool = set.movepool.map(toId).filter((m) => moves[m]);
  if (movepool.length < 4) return null;
  let phys = 0, spec = 0;
  for (const m of movepool) {
    const c = category(m);
    if (c === 'Physical') phys++;
    else if (c === 'Special') spec++;
  }
  const physical = phys >= spec;
  const role = set.role || '';
  const fast = role.startsWith('Fast') || role === 'Setup Sweeper' || role === 'Wallbreaker';
  const bulky = role.startsWith('Bulky');
  let items = (ITEMS_BY_ROLE[role] || DEFAULT_ITEMS).filter((i) => {
    if (i === 'choice_band') return physical;
    if (i === 'choice_specs') return !physical;
    return true;
  });
  let natures;
  let evs;
  if (bulky) {
    natures = physical ? ['impish', 'adamant', 'careful'] : ['bold', 'calm', 'modest'];
    evs = physical ? 'hp=252,atk=252,spe=4' : 'hp=252,spa=252,spe=4';
  } else if (fast) {
    natures = physical ? ['jolly', 'adamant'] : ['timid', 'modest'];
    evs = physical ? 'hp=4,atk=252,spe=252' : 'hp=4,spa=252,spe=252';
  } else {
    natures = physical ? ['adamant', 'jolly'] : ['modest', 'timid'];
    evs = physical ? 'hp=4,atk=252,spe=252' : 'hp=4,spa=252,spe=252';
  }
  return {
    especie: species,
    golpes: movepool,
    itens: items.map((i) => 'cobblemon:' + i),
    naturezas: natures,
    evs,
    papel: role,
  };
}

// Faixas da torre -> tiers do Smogon (geração 9)
const BUCKETS = {
  iniciante: ['PU', 'ZU', 'PUBL'],
  intermediario: ['NU', 'NUBL'],
  avancado: ['RU', 'RUBL'],
  elite: ['UU', 'UUBL'],
  mestre: ['OU'],
};

const pools = {};
const stats = {};
for (const [bucket, tiers] of Object.entries(BUCKETS)) {
  const list = [];
  for (const [id, data] of Object.entries(formats)) {
    if (!tiers.includes(data.tier)) continue;
    const entry = dex[id];
    if (!entry || entry.forme) continue; // só formas base (sem regionais/megas)
    const cob = cobblemon[id];
    if (!cob || !cob.impl) continue; // só espécies com modelo no Cobblemon
    const rs = randomSets[id];
    if (!rs) continue;
    for (const set of rs.sets) {
      const e = buildEntry(id, set);
      if (e) list.push(e);
    }
  }
  pools[bucket] = list;
  stats[bucket] = new Set(list.map((e) => e.especie)).size;
}

// Chefe final: lendários restritos (Uber / Anything Goes). Sets montados à mão.
// "exigeModelo": false deixa o mod usar a espécie mesmo sem modelo, caso faltem lendários implementados.
const L = (especie, habilidade, golpes, itens, naturezas, evs) =>
  ({ especie, habilidades: [habilidade], golpes, itens: itens.map((i) => 'cobblemon:' + i), naturezas, evs, papel: 'Lendário' });
const SPA = 'hp=4,spa=252,spe=252', ATK = 'hp=4,atk=252,spe=252', TANK = 'hp=252,def=128,spd=128';
pools.lendarios_uber = [
  L('mewtwo', 'unnerve', ['psystrike', 'aurasphere', 'icebeam', 'fireblast', 'nastyplot', 'recover', 'shadowball'], ['life_orb', 'leftovers'], ['timid', 'modest'], SPA),
  L('lugia', 'multiscale', ['aeroblast', 'psychicnoise', 'calmmind', 'roost', 'earthquake', 'toxic'], ['leftovers', 'heavy_duty_boots'], ['bold', 'timid'], TANK),
  L('hooh', 'regenerator', ['sacredfire', 'bravebird', 'earthquake', 'recover', 'swordsdance', 'flamecharge'], ['heavy_duty_boots', 'life_orb'], ['adamant', 'impish'], 'hp=252,atk=252,spe=4'),
  L('rayquaza', 'airlock', ['dragonascent', 'dragondance', 'extremespeed', 'earthquake', 'vcreate', 'swordsdance'], ['life_orb', 'lum_berry'], ['jolly', 'adamant'], ATK),
  L('xerneas', 'fairyaura', ['geomancy', 'moonblast', 'focusblast', 'psyshock', 'thunderbolt'], ['power_herb'], ['modest', 'timid'], SPA),
  L('mew', 'synchronize', ['psychic', 'aurasphere', 'fireblast', 'nastyplot', 'roost', 'thunderbolt'], ['life_orb', 'leftovers'], ['timid'], SPA),
  L('kyogre', 'drizzle', ['waterspout', 'originpulse', 'icebeam', 'thunder', 'calmmind'], ['choice_scarf', 'choice_specs', 'leftovers'], ['timid', 'modest'], SPA),
  L('groudon', 'drought', ['precipiceblades', 'heatcrash', 'stoneedge', 'stealthrock', 'swordsdance', 'firepunch'], ['leftovers', 'life_orb'], ['adamant', 'jolly'], ATK),
  L('dialga', 'telepathy', ['dracometeor', 'flashcannon', 'fireblast', 'thunderbolt', 'stealthrock', 'earthpower'], ['choice_specs', 'life_orb'], ['modest', 'timid'], SPA),
  L('palkia', 'telepathy', ['spacialrend', 'hydropump', 'fireblast', 'thunderbolt', 'thunderwave', 'dracometeor'], ['life_orb', 'choice_specs'], ['modest', 'timid'], SPA),
  L('giratina', 'telepathy', ['hex', 'willowisp', 'dragontail', 'defog', 'rest', 'shadowball'], ['leftovers'], ['bold', 'calm'], TANK),
  L('arceus', 'multitype', ['extremespeed', 'swordsdance', 'shadowclaw', 'earthquake', 'recover'], ['silk_scarf', 'life_orb'], ['adamant', 'jolly'], ATK),
  L('reshiram', 'turboblaze', ['blueflare', 'dracometeor', 'earthpower', 'roost', 'flamecharge'], ['choice_scarf', 'heavy_duty_boots'], ['modest', 'timid'], SPA),
  L('zekrom', 'teravolt', ['boltstrike', 'outrage', 'dragonclaw', 'voltswitch', 'dragondance', 'roost'], ['life_orb', 'choice_band'], ['jolly', 'adamant'], ATK),
  L('kyurem', 'pressure', ['icebeam', 'freezedry', 'dracometeor', 'earthpower', 'roost', 'substitute'], ['heavy_duty_boots', 'leftovers'], ['timid'], SPA),
  L('yveltal', 'darkaura', ['oblivionwing', 'darkpulse', 'suckerpunch', 'roost', 'uturn', 'heatwave'], ['life_orb', 'heavy_duty_boots'], ['timid', 'modest'], SPA),
  L('zygarde', 'aurabreak', ['thousandarrows', 'dragondance', 'extremespeed', 'coil', 'glare', 'substitute'], ['leftovers', 'lum_berry'], ['adamant', 'jolly'], ATK),
  L('necrozma', 'prismarmor', ['photongeyser', 'earthpower', 'heatwave', 'calmmind', 'moonlight', 'stealthrock'], ['leftovers', 'weakness_policy'], ['bold', 'modest'], TANK),
  L('solgaleo', 'fullmetalbody', ['sunsteelstrike', 'flareblitz', 'closecombat', 'psychicfangs', 'morningsun', 'knockoff'], ['life_orb', 'choice_band'], ['jolly', 'adamant'], ATK),
  L('lunala', 'shadowshield', ['moongeistbeam', 'moonblast', 'focusblast', 'calmmind', 'roost', 'psyshock'], ['leftovers', 'power_herb'], ['timid', 'modest'], SPA),
  L('zacian', 'intrepidsword', ['playrough', 'closecombat', 'wildcharge', 'crunch', 'swordsdance', 'psychicfangs'], ['life_orb', 'lum_berry'], ['jolly'], ATK),
  L('zamazenta', 'dauntlessshield', ['closecombat', 'crunch', 'bodypress', 'irondefense', 'wildcharge', 'stoneedge'], ['leftovers', 'lum_berry'], ['jolly', 'impish'], ATK),
  L('eternatus', 'pressure', ['dynamaxcannon', 'flamethrower', 'sludgebomb', 'recover', 'toxicspikes', 'meteorbeam'], ['leftovers', 'power_herb'], ['timid', 'modest'], SPA),
  L('koraidon', 'orichalcumpulse', ['collisioncourse', 'flareblitz', 'uturn', 'dragonclaw', 'swordsdance', 'outrage'], ['choice_scarf', 'life_orb'], ['jolly', 'adamant'], ATK),
  L('miraidon', 'hadronengine', ['electrodrift', 'dracometeor', 'voltswitch', 'overheat', 'dazzlinggleam', 'calmmind'], ['choice_specs', 'choice_scarf'], ['timid', 'modest'], SPA),
  L('deoxys', 'pressure', ['psychoboost', 'superpower', 'knockoff', 'extremespeed', 'icebeam'], ['life_orb', 'focus_sash'], ['hasty', 'naive'], 'atk=252,spa=252,spe=4'),
  L('marshadow', 'technician', ['spectralthief', 'closecombat', 'shadowsneak', 'icepunch', 'bulkup', 'rocktomb'], ['life_orb', 'choice_scarf'], ['jolly', 'adamant'], ATK),
  L('magearna', 'soulheart', ['fleurcannon', 'flashcannon', 'aurasphere', 'shiftgear', 'calmmind', 'voltswitch'], ['leftovers', 'weakness_policy'], ['modest', 'timid'], SPA),
];

// Validação: golpes / habilidades existem no Showdown, espécies existem no Cobblemon
const abilities = req('abilities.js').Abilities;
let problems = 0;
for (const [bucket, list] of Object.entries(pools)) {
  for (const e of list) {
    if (!cobblemon[e.especie]) { console.warn(`[${bucket}] espécie inexistente: ${e.especie}`); problems++; }
    for (const m of e.golpes) if (!moves[m]) { console.warn(`[${bucket}] ${e.especie}: golpe inválido ${m}`); problems++; }
    for (const a of e.habilidades || []) if (!abilities[a]) { console.warn(`[${bucket}] ${e.especie}: habilidade inválida ${a}`); problems++; }
  }
}

const legendImpl = pools.lendarios_uber.filter((e) => cobblemon[e.especie] && cobblemon[e.especie].impl).map((e) => e.especie);
fs.writeFileSync(outFile, JSON.stringify(pools, null, 2));
console.log('Espécies por faixa:', JSON.stringify(stats));
console.log('Lendários com modelo no Cobblemon puro:', legendImpl.join(', '));
console.log('Problemas:', problems);
