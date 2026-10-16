"""Validate the bundled keyboard without Android SDK. Requires PyYAML."""
from pathlib import Path
import yaml

class UniqueKeyLoader(yaml.SafeLoader):
    """Match the Android theme parser: never silently accept duplicate fields."""

def unique_mapping(loader, node):
    result = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node)
        if key in result:
            raise yaml.constructor.ConstructorError(
                'while constructing a mapping', node.start_mark,
                f'duplicate key: {key}', key_node.start_mark)
        result[key] = loader.construct_object(value_node)
    return result

UniqueKeyLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, unique_mapping)
try:
    yaml.load('click: Shift_L\nclick: delimiter\n', Loader=UniqueKeyLoader)
except yaml.constructor.ConstructorError:
    pass
else:
    raise AssertionError('Duplicate key guard did not reject conflicting actions')

root = Path(__file__).resolve().parents[1]
theme = yaml.load((root / 'app/src/main/assets/shared/classic.trime.yaml').read_text(encoding='utf-8'), Loader=UniqueKeyLoader)
print('PASS: strict YAML parsing rejects duplicate fields')
for name in ['default', 'letter', 'wanxiang_pro', 'wanxiang']:
    keyboard = theme['preset_keyboards'][name]
    keys = keyboard['keys']
    assert len(keys) == 43, name
    assert (keyboard['horizontal_gap'], keyboard['vertical_gap']) == (6, 9)
    rows = [keys[a:b] for a, b in [(0, 12), (12, 23), (23, 34), (34, 43)]]
    assert [sum(k['width'] for k in row) for row in rows] == [100] * 4
    clicks = [[k['click'] for k in row if 'click' in k] for row in rows]
    assert clicks[0] == list('qwertyuiop')
    assert clicks[1] == list('asdfghjkl')
    assert clicks[2] == (['Shift_L'] if name == 'letter' else ['delimiter']) + list('zxcvbnm') + ['BackSpace']
    assert clicks[3] == ['liquid_keyboard_cn1', 'Keyboard_number', ',', 'space', '.', 'Mode_switch', 'Return']
    by_click = {k['click']: k for k in keys if 'click' in k}
    for key, action in [('a', 'select_all'), ('x', 'cut'), ('c', 'copy'), ('v', 'paste_clip'), ('z', 'Keyboard_bqrw1'), ('space', 'VOICE_ASSIST')]:
        assert by_click[key]['long_click'] == action
    assert [by_click[k]['label_symbol'] for k in 'qwertyuiop'] == list('1234567890')
    assert [by_click[k]['label_symbol'] for k in 'asdfghjkl'] == list('~@#$%&*()')
    assert [by_click[k]['label_symbol'] for k in 'zxcvbnm'] == ["'", '/', '-', '_', ':', ';', '`']
    assert by_click['space']['swipe_left'] == 'Page_Up'
    assert by_click['space']['swipe_right'] == 'Page_Down'
    print(f'PASS: {name}, four 100% rows, staggered letters, seven bottom keys, editing/emoji/voice shortcuts')
assert [b['action'] for b in theme['tool_bar']['buttons']] == ['Hide', 'Mode_switch', 'clipboard_window', 'Keyboard_numberb', 'undo']
print('PASS: toolbar actions retained')
