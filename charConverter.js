class CharConverter {
    constructor(option) {
        if (option !== 'v2s' && option !== 'v2t') {
            throw new Error("Option must be either 'v2s' (simplified) or 'v2t' (traditional).");
        }
        this.mapping = {};
        this.mode = 'one2one';

        // Return a promise that resolves when the mapping data is loaded
        this.loaded = this.loadMappingData(option);
    }

    loadMappingData(option) {
        const url = `char_converter/data/${option}.json`;
        return fetch(url)
            .then(response => {
                if (!response.ok) {
                    throw new Error("Network response was not ok");
                }
                return response.json();
            })
            .then(data => {
                this.mapping = data;
            })
            .catch(error => {
                throw error;
            });
    }

    setMode(mode) {
        if (mode !== 'one2one' && mode !== 'one2many') {
            throw new Error("Mode must be either 'one2one' or 'one2many'.");
        }
        this.mode = mode;
    }

    convert(text) {
        // Normalize CJK compatibility ideographs to unified code points so the
        // mapping needs no compatibility-code-point keys (parity with Python).
        text = text.normalize('NFC');
        let convertedText = [];
        for (let char of text) {
            if (this.mode === 'one2one') {
                convertedText.push(this.mapping[char] ? this.mapping[char][0] : char);
            } else if (this.mode === 'one2many') {
                if (this.mapping[char] && this.mapping[char].length > 1) {
                    convertedText.push(`【${this.mapping[char].join('|')}】`);
                } else {
                    convertedText.push(this.mapping[char] ? this.mapping[char][0] : char);
                }
            }
        }
        return convertedText.join('');
    }
}
