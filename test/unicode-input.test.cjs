const test = require('node:test');
const assert = require('node:assert/strict');

test('Unicode inputs accept characters and hexadecimal code points', async () => {
    const {unicodeInput} = await import('../halo-plugin/ui/src/unicode.ts');
    for (const input of ['一','U+4E00','u+4e00','0x4e00','4E00']) assert.equal(unicodeInput(input),'一');
    for (const input of ['𠀀','U+20000','0x20000','20000']) assert.equal(unicodeInput(input),'𠀀');
    assert.equal(unicodeInput('A'),'A');
});

test('Invalid Unicode scalar values are rejected', async () => {
    const {unicodeInput} = await import('../halo-plugin/ui/src/unicode.ts');
    for (const input of ['U+110000','0xD800','DFFF']) assert.throws(()=>unicodeInput(input));
});

test('Code labels use whole Unicode characters, including supplementary planes', async () => {
    const {unicodeCodes} = await import('../halo-plugin/ui/src/unicode.ts');
    assert.equal(unicodeCodes('一𠀀A'),'U+4E00 U+20000 U+0041');
});
