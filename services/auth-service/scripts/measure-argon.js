const argon2 = require('argon2');

async function measure() {
  const options = {
    type: argon2.argon2id,
    timeCost: Number(process.env.ARGON2_TIME || 3),
    memoryCost: Number(process.env.ARGON2_MEMORY_KB || 65536),
    parallelism: Number(process.env.ARGON2_PARALLELISM || 1),
  };

  const password = 'benchmark-password';
  const hash = await argon2.hash(password, options);

  const start = Date.now();
  await argon2.verify(hash, password);
  const duration = Date.now() - start;

  console.log('argon2 verify duration_ms=', duration, 'options=', options);
}

measure().catch(e => { console.error(e); process.exit(1); });