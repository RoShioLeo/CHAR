const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '..');
const source = fs.readFileSync(path.join(root, 'charConverter.js'), 'utf8');
const overrides = JSON.parse(fs.readFileSync(path.join(__dirname, 'fork-overrides.json'), 'utf8'));

for (const option of ['v2s', 'v2t']) {
    const mapping = JSON.parse(fs.readFileSync(path.join(root, 'char_converter/data', option + '.json'), 'utf8'));
    const context = vm.createContext({ fetch: async url => {
        assert.equal(url, 'char_converter/data/' + option + '.json');
        return { ok: true, json: async () => mapping };
    } });
    vm.runInContext(source + '\nglobalThis.converterClass = CharConverter;', context);
    test(option + ': preserve all fork corrections in both modes', async () => {
        const converter = new context.converterClass(option);
        await converter.loaded;
        for (const mode of ['one2one', 'one2many']) {
            converter.setMode(mode);
            for (const [character, values] of Object.entries(overrides[option])) {
                assert.deepEqual(mapping[character] ?? null, values, character);
                const expected = values === null ? character : values.length > 1 && mode === 'one2many' ? '【' + values.join('|') + '】' : values[0];
                assert.equal(converter.convert(character), expected, character + ' / ' + mode);
            }
            assert.equal(converter.convert('壹拾柒叁捌玖肆贰机拿砂'), '壹拾柒叁捌玖肆贰机拿砂');
            assert.equal(converter.convert('神'), converter.convert('神'));
            assert.equal(converter.convert('丽'), converter.convert('丽'.normalize('NFC')));
        }
    });
    test(option + ': full dictionary and supplementary characters', async () => {
        const converter = new context.converterClass(option);
        await converter.loaded;
        for (const mode of ['one2one', 'one2many']) {
            converter.setMode(mode);
            for (const character of Object.keys(mapping)) {
                const expected = Array.from(character.normalize('NFC'), c => {
                    const values = mapping[c];
                    return !values ? c : mode === 'one2many' && values.length > 1 ? '【' + values.join('|') + '】' : values[0];
                }).join('');
                assert.equal(converter.convert(character), expected, character + ' / ' + mode);
            }
        }
    });
}
test('dictionary loading failures reject instead of returning unconverted text', async () => {
    const context = vm.createContext({ fetch: async () => ({ok: false}) });
    vm.runInContext(source + '\nglobalThis.converterClass = CharConverter;', context);
    await assert.rejects(new context.converterClass('v2s').loaded);
});
