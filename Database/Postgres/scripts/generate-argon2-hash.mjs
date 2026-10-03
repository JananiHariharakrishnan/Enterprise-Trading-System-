#!/usr/bin/env node
/**
 * Helper script to generate Argon2id password hashes for database migrations.
 * 
 * Usage:
 *   node generate-argon2-hash.mjs "your-password"
 * 
 * This generates a hash with the same parameters used by the auth service:
 * - Algorithm: argon2id
 * - Time cost: 3
 * - Memory cost: 32768 KB
 * - Parallelism: 1
 */

import argon2 from 'argon2';

const password = process.argv[2];

if (!password) {
  console.error('Usage: node generate-argon2-hash.mjs "password"');
  process.exit(1);
}

const PASSWORD_HASH_OPTIONS = {
  type: argon2.argon2id,
  timeCost: 3,
  memoryCost: 32768,
  parallelism: 1,
};

(async () => {
  try {
    const hash = await argon2.hash(password, PASSWORD_HASH_OPTIONS);
    console.log('Password:', password);
    console.log('Hash:', hash);
    console.log('\nCopy the hash above into your migration file.');
  } catch (error) {
    console.error('Error generating hash:', error.message);
    process.exit(1);
  }
})();
