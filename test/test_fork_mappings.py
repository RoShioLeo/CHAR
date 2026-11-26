from pathlib import Path
import contextlib
import io
import json
import unittest
from char_converter import CharConverter


class ForkMappings(unittest.TestCase):
    def test_user_corrections(self):
        overrides = json.loads((Path(__file__).parent / 'fork-overrides.json').read_text(encoding='utf-8'))
        for option, changes in overrides.items():
            converter = CharConverter(option)
            for mode in ('one2one', 'one2many'):
                with contextlib.redirect_stdout(io.StringIO()):
                    converter.set_mode(mode)
                for character, values in changes.items():
                    with self.subTest(option=option, mode=mode, character=character):
                        self.assertEqual(converter.mapping.get(character), values)
                        expected = character if values is None else ('【' + '|'.join(values) + '】' if mode == 'one2many' and len(values) > 1 else values[0])
                        self.assertEqual(converter.convert(character), expected)
                self.assertEqual(converter.convert('壹拾柒叁捌玖肆贰机拿砂'), '壹拾柒叁捌玖肆贰机拿砂')
                self.assertEqual(converter.convert('神'), converter.convert('神'))


if __name__ == '__main__':
    unittest.main()
