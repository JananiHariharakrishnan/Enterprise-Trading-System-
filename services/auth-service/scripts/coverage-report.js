const fs = require('fs');
const path = require('path');
const covPath = path.join(__dirname, '..', 'coverage', 'coverage-final.json');
const raw = fs.readFileSync(covPath, 'utf8');
const cov = JSON.parse(raw);
const out = [];
for (const k of Object.keys(cov)) {
  const m = cov[k];
  const stmtMap = m.statementMap || {};
  const s = m.s || {};
  const total = Object.keys(stmtMap).length;
  let covered = 0;
  const missedRanges = [];
  for (const id of Object.keys(stmtMap)) {
    const cnt = s[id] || 0;
    if (cnt > 0) covered++;
    else {
      const r = stmtMap[id];
      if (r && r.start) {
        const start = r.start.line;
        const end = r.end && r.end.line ? r.end.line : r.start.line;
        missedRanges.push(start === end ? `${start}` : `${start}-${end}`);
      }
    }
  }
  const pct = total ? Math.round((covered / total) * 100) : 100;
  out.push({pct, path: m.path || k, total, covered, missed: missedRanges});
}
out.sort((a,b)=>a.pct - b.pct || a.path.localeCompare(b.path));
console.log(JSON.stringify(out, null, 2));
