const converters = new Map();
const element = id => document.getElementById(id);
const status = (message, error = false) => {
    element('conversionStatus').textContent = message;
    element('conversionStatus').dataset.error = String(error);
};

async function currentConverter() {
    const option = element('optionSelect').value;
    if (!converters.has(option)) {
        const converter = new CharConverter(option);
        converters.set(option, converter);
    }
    const converter = converters.get(option);
    try {
        await converter.loaded;
    } catch (error) {
        converters.delete(option);
        throw error;
    }
    converter.setMode(element('modeSelect').value);
    return converter;
}

async function runConversion(button, action) {
    button.disabled = true;
    status('');
    try { await action(); }
    catch (_) { status('转换失败，请检查网络或文本文件后重试。', true); }
    finally { button.disabled = false; }
}

element('convertText').addEventListener('click', event => runConversion(event.currentTarget, async () => {
    const converter = await currentConverter();
    element('outputText').value = converter.convert(element('inputText').value);
    element('copyOutput').disabled = !element('outputText').value;
}));

element('clearText').addEventListener('click', () => {
    element('inputText').value = '';
    element('outputText').value = '';
    element('copyOutput').disabled = true;
    status('');
    element('inputText').focus();
});

element('copyOutput').addEventListener('click', async () => {
    if (!element('outputText').value) return;
    try {
        await navigator.clipboard.writeText(element('outputText').value);
        status('已复制');
    } catch (_) {
        element('outputText').focus();
        element('outputText').select();
        status(document.execCommand('copy') ? '已复制' : '请复制已选中的文字。');
    }
});

element('chooseFile').addEventListener('click', () => element('fileInput').click());
element('fileInput').addEventListener('change', () => {
    const file = element('fileInput').files[0];
    element('convertFile').disabled = !file;
    status(file ? file.name : '');
});
element('convertFile').addEventListener('click', event => runConversion(event.currentTarget, async () => {
    const file = element('fileInput').files[0];
    if (!file) return;
    const converter = await currentConverter();
    const text = new TextDecoder('utf-8', { fatal: true }).decode(await file.arrayBuffer());
    const converted = converter.convert(text);
    const url = URL.createObjectURL(new Blob([converted], { type: 'text/plain;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = file.name.replace(/\.[^.]+$/, '') + '-转换结果.txt';
    document.body.append(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    status('文件已转换');
}));
