"""Render the complete derivative version history into README without dropping old versions."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
START = '<!-- VERSION_HISTORY_START -->'
END = '<!-- VERSION_HISTORY_END -->'

def render_history(changelog: str) -> str:
    own = changelog.split('\n以下为原工程', 1)[0].rstrip()
    if own.endswith('---'):
        own = own[:-3].rstrip()
    headings = list(re.finditer(r'^## (\d+(?:\.\d+)+)[^\n]*$', own, re.MULTILINE))
    if not headings:
        raise ValueError('No application version history found')
    records = []
    for i, match in enumerate(headings):
        version = match.group(1)
        body = own[match.end():headings[i+1].start() if i+1 < len(headings) else len(own)].strip()
        heading = '#' + match.group(0)
        if version == '1.1' and '支持媒体控制版安装说明：' in body:
            # The compatibility instructions belong to their own update item.
            main = body.split('主要更新：', 1)[1].split('支持媒体控制版安装说明：', 1)[0]
            items = re.findall(r'^\d+\. (.+)$', main, re.MULTILINE)
            media = [item for item in items if '新增“支持媒体控制版”' in item]
            shared = [item for item in items if item not in media]
            if len(media) != 1:
                raise ValueError('Expected one media-panel update')
            note = body.split('支持媒体控制版安装说明：', 1)[1].split('其他优化与修复：', 1)[0].strip()
            other = body.split('其他优化与修复：', 1)[1].strip()
            other_items = re.findall(r'^\d+\. (.+)$', other, re.MULTILINE)
            body = ('#### 主要优化\n\n' + '\n'.join('- ' + item for item in shared)
                    + '\n\n#### 新增：支持媒体控制面板\n\n' + '- ' + media[0]
                    + '\n\n> **安装提示：** ' + note
                    + '\n\n#### 其他优化与修复\n\n' + '\n'.join('- ' + item for item in other_items))
        records.append(heading + '\n\n' + body)
    if '1.0' not in [match.group(1) for match in headings]:
        raise ValueError('The 1.0 history must be retained')
    return '\n\n'.join(records) + '\n'

def main() -> None:
    path = ROOT / 'README.md'
    readme = path.read_text(encoding='utf-8')
    if readme.count(START) != 1 or readme.count(END) != 1:
        raise ValueError('README must contain exactly one pair of version-history markers')
    before, rest = readme.split(START, 1)
    _, after = rest.split(END, 1)
    history = render_history((ROOT / 'CHANGELOG.md').read_text(encoding='utf-8'))
    path.write_text(before + START + '\n\n' + history + '\n' + END + after, encoding='utf-8')
    print('README includes the complete version history from CHANGELOG.md.')

if __name__ == '__main__':
    main()
