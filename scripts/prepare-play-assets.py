"""Export the existing clock icon, a store graphic, and the in-app policy to HTML."""
from pathlib import Path
from html import escape
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'store' / 'assets'
ASSETS.mkdir(parents=True, exist_ok=True)
ACCENT = '#72E6CF'
BACKGROUND = '#142C33'

def font(name, size):
    return ImageFont.truetype(str(Path('C:/Windows/Fonts') / name), size)

def clock_icon(size):
    scale = 4
    image = Image.new('RGB', (size * scale, size * scale), BACKGROUND)
    draw = ImageDraw.Draw(image)
    unit = size * scale / 48
    draw.ellipse(tuple(round(v * unit) for v in (7, 7, 41, 41)), outline=ACCENT, width=round(3 * unit))
    draw.line([(round(x * unit), round(y * unit)) for x, y in [(24, 13), (24, 25), (32, 30)]], fill=ACCENT, width=round(3 * unit), joint='curve')
    return image.resize((size, size), Image.Resampling.LANCZOS)

clock_icon(512).save(ASSETS / 'icon-512.png')
graphic = Image.new('RGB', (1024, 500), '#101A20')
draw = ImageDraw.Draw(graphic)
for y in range(500):
    draw.line((0, y, 1024, y), fill=(16 + y * 4 // 500, 26 + y * 18 // 500, 32 + y * 19 // 500))
graphic.paste(clock_icon(112), (68, 58))
draw = ImageDraw.Draw(graphic)
draw.text((206, 65), 'ChronoSync', font=font('consolab.ttf', 58), fill='#F1FCF8')
draw.text((210, 137), 'NTP同期オーバーレイ時計', font=font('meiryo.ttc', 25), fill=ACCENT)
draw.rounded_rectangle((68, 238, 956, 342), radius=18, fill='#0B1418', outline='#32584F', width=2)
draw.text((106, 251), '18:34:25.128', font=font('consola.ttf', 67), fill=ACCENT)
draw.text((70, 381), '小さく、見やすく。表示時刻を1ms単位で補正。', font=font('meiryo.ttc', 26), fill='#E0F1EA')
draw.text((70, 451), 'Gunlod', font=font('consola.ttf', 20), fill='#92B5AC')
graphic.save(ASSETS / 'feature-1024x500.png')

resource = ET.parse(ROOT / 'app/src/main/res/values/privacy.xml').getroot()
policy = resource.find("string[@name='privacy_policy_text']").text.replace('\\n', '\n')
paragraphs = policy.split('\n\n')
body = '\n'.join('<p>' + '<br>'.join(escape(line) for line in paragraph.splitlines()) + '</p>' for paragraph in paragraphs)
html = '''<!doctype html>
<html lang="ja"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>ChronoSync プライバシーポリシー | Gunlod</title>
<style>body{max-width:760px;margin:48px auto;padding:0 24px;font:16px/1.9 system-ui,sans-serif;color:#172b2d;background:#f4f8f6}h1{font-size:28px}p{margin:28px 0}a{color:#006b5c}footer{border-top:1px solid #c7d8d1;margin-top:40px;padding-top:20px}</style>
<main><h1>ChronoSync プライバシーポリシー</h1>''' + body + '''<footer><a href="mailto:gunlod.0122%2Bplaystore@gmail.com">お問い合わせ：Gunlod</a></footer></main></html>
'''
destination = ROOT / 'store/privacy/index.html'
destination.parent.mkdir(parents=True, exist_ok=True)
destination.write_text(html, encoding='utf-8')
print('Exported icon (512x512), feature graphic (1024x500), and privacy policy HTML.')
