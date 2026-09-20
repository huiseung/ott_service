const SAMPLE = 1024 * 1024;
export async function createFileFingerprint(file: File): Promise<string> {
  const offsets = [0, Math.max(0, Math.floor(file.size / 2) - SAMPLE / 2), Math.max(0, file.size - SAMPLE)];
  const samples = await Promise.all(offsets.map(offset => file.slice(offset, Math.min(file.size, offset + SAMPLE)).arrayBuffer()));
  const bytes = new Uint8Array(8 + samples.reduce((total, sample) => total + sample.byteLength, 0));
  new DataView(bytes.buffer).setBigUint64(0, BigInt(file.size));
  let cursor = 8;
  for (const sample of samples) { bytes.set(new Uint8Array(sample), cursor); cursor += sample.byteLength; }
  return Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", bytes)), byte => byte.toString(16).padStart(2, "0")).join("");
}
