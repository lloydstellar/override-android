// Renders the S.A.M. lines of the game to WAV with the same sam-js and the
// same voice settings as the browser game (override.js: speed 68, pitch 58,
// throat 150, mouth 128). Only the rendered audio ships with the app.
//
//   node tools/make_voice.cjs path/to/samjs.min.js app/src/main/assets/voice
//
// sam-js (https://github.com/discordier/sam) is a port of S.A.M., Software
// Automatic Mouth (1982); its author notes that the original is abandonware
// and that the port carries no license.
const fs = require('fs');
const path = require('path');
const [, , samPath, outDir] = process.argv;
const SamJs = require(path.resolve(samPath));

const LINES = {
    override: 'System override successful. A I core offline.',
    sector: 'Sector clear.',
    nexus: 'Nexus core offline. The grid is ours.',
    breach: 'Core breach.',
    purge: 'Global purge.',
};

function wav(samples, rate) {
    const data = Buffer.alloc(samples.length * 2);
    for (let i = 0; i < samples.length; i++) {
        const v = Math.max(-1, Math.min(1, samples[i]));
        data.writeInt16LE(Math.round(v * 32767), i * 2);
    }
    const h = Buffer.alloc(44);
    h.write('RIFF', 0); h.writeUInt32LE(36 + data.length, 4); h.write('WAVE', 8);
    h.write('fmt ', 12); h.writeUInt32LE(16, 16); h.writeUInt16LE(1, 20); h.writeUInt16LE(1, 22);
    h.writeUInt32LE(rate, 24); h.writeUInt32LE(rate * 2, 28); h.writeUInt16LE(2, 32); h.writeUInt16LE(16, 34);
    h.write('data', 36); h.writeUInt32LE(data.length, 40);
    return Buffer.concat([h, data]);
}

const sam = new SamJs({ speed: 68, pitch: 58, throat: 150, mouth: 128 });
for (const [name, text] of Object.entries(LINES)) {
    const buf = sam.buf32(text);            /* Float32Array at 22050 Hz */
    fs.writeFileSync(path.join(outDir, name + '.wav'), wav(buf, 22050));
    console.log(name, (buf.length / 22050).toFixed(2) + ' s');
}
