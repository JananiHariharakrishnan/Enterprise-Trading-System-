import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

const ROOT = process.cwd();
const SOURCE_DIRS = ['src', 'test'];
const SOURCE_EXTENSIONS = new Set(['.ts', '.tsx', '.js', '.mjs', '.cjs']);

const FORBIDDEN_PATTERNS = [
  /logger\.(info|warn|error)\s*\([^)]*\bbody\b/gi,
  /logger\.(info|warn|error)\s*\([^)]*\breq\.body\b/gi,
  /logger\.(info|warn|error)\s*\([^)]*\brequest\.body\b/gi,
];

function collectFiles(dir) {
  const entries = readdirSync(dir);
  const files = [];

  for (const entry of entries) {
    const fullPath = join(dir, entry);
    const stats = statSync(fullPath);

    if (stats.isDirectory()) {
      files.push(...collectFiles(fullPath));
      continue;
    }

    if (!Array.from(SOURCE_EXTENSIONS).some((ext) => fullPath.endsWith(ext))) {
      continue;
    }

    files.push(fullPath);
  }

  return files;
}

function countLine(content, index) {
  return content.slice(0, index).split(/\r?\n/).length;
}

function scanFile(filePath) {
  const content = readFileSync(filePath, 'utf8');
  const violations = [];

  for (const pattern of FORBIDDEN_PATTERNS) {
    pattern.lastIndex = 0;
    let match = pattern.exec(content);

    while (match) {
      violations.push({
        filePath,
        line: countLine(content, match.index),
        snippet: match[0].trim(),
      });
      match = pattern.exec(content);
    }
  }

  return violations;
}

const allFiles = SOURCE_DIRS
  .map((dir) => join(ROOT, dir))
  .filter((dir) => {
    try {
      return statSync(dir).isDirectory();
    } catch {
      return false;
    }
  })
  .flatMap((dir) => collectFiles(dir));

const violations = allFiles.flatMap((filePath) => scanFile(filePath));

if (violations.length > 0) {
  console.error('Log safety check failed. Do not pass full request bodies directly to logger calls.');
  for (const violation of violations) {
    console.error(`- ${violation.filePath}:${violation.line} -> ${violation.snippet}`);
  }
  process.exit(1);
}

console.log('Log safety check passed.');
