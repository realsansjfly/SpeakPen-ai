from pathlib import Path
from datetime import datetime
import sys, json, html
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / '.tooling/preview-python'))
import qrcode
import qrcode.image.svg

workspace = Path(__file__).resolve().parents[1]
connection = json.loads((workspace / '.local/connection.json').read_text(encoding='utf-8-sig'))
url = connection['webUrl'] + '/#token=' + connection['accessToken']
qr = qrcode.make(url, image_factory=qrcode.image.svg.SvgPathImage)
svg = qr.to_string(encoding='unicode')
page = '''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="referrer" content="no-referrer"><title>SpeakPen 已就绪</title><style>body{font-family:system-ui,'Microsoft YaHei',sans-serif;background:#f3f8f3;color:#21382c;margin:0;padding:32px}main{max-width:560px;margin:auto;background:white;border:1px solid #e4ebe6;border-radius:28px;padding:28px}h1{font-size:25px;margin:0}p{line-height:1.8;color:#64756c}a{display:block;background:#dcf8c6;padding:15px;border-radius:15px;text-align:center;text-decoration:none;color:#21382c;font-weight:bold}.qr{display:flex;justify-content:center}.qr svg{width:280px;height:280px}small{color:#64756c;line-height:1.7;display:block;margin-top:20px}</style><main><h1>SpeakPen · 可以开口了</h1><p>本入口已通过公网访问检查。手机扫描这个新二维码，打开后请点“连接 App”，让已安装的 App 更新地址；只启动电脑不会自动更新手机中的旧地址。</p><div class="qr">''' + svg + '''</div><a href="''' + html.escape(url, quote=True) + '''">打开真实对话 / 下载安装包</a><p><strong>App 未被浏览器唤起时：</strong>在手机网页复制完整连接入口，打开 App → 偏好 → 服务连接 → 重新连接 → 粘贴。</p><label>完整连接入口（含配对信息）</label><textarea readonly style="width:100%;height:74px;box-sizing:border-box;margin:10px 0;padding:12px;border:1px solid #ddd;border-radius:12px;word-break:break-all" onclick="this.select()">''' + html.escape(url) + '''</textarea><p>本入口生成时间：''' + datetime.now().strftime('%Y-%m-%d %H:%M:%S') + '''</p><p>此服务运行在这台电脑上，电脑需要保持开机联网。若服务重启导致地址改变，重新运行启动脚本并使用新入口。</p><small>二维码包含本机服务的配对凭据，请勿公开分享。阿里云 API Key 只保存在本机 server/.env。</small></main></html>'''
(workspace / 'deliverables/打开真实对话.html').write_text(page, encoding='utf-8')
print('PAIRING_LAUNCHER_READY (credentials not displayed)')

