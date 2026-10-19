from pathlib import Path
import base64
root=Path(__file__).resolve().parents[1]
fonts=[]
for filename,weight in [('nunito_regular.ttf',400),('nunito_extra_bold.ttf',800)]:
    data=base64.b64encode((root/'app/src/main/res/font'/filename).read_bytes()).decode()
    fonts.append("@font-face{font-family:Nunito;src:url(data:font/ttf;base64,"+data+") format('truetype');font-weight:"+str(weight)+";font-display:swap}")
page=(root/'design/product-intro.source.html').read_text(encoding='utf-8-sig')
page=page.replace('__FONTS__','\n'.join(fonts)).replace('__CSS__',(root/'design/product-intro.css').read_text(encoding='utf-8-sig')).replace('__LICENSE__',(root/'design/Nunito-OFL.txt').read_text(encoding='utf-8-sig'))
output=root/'deliverables/SpeakPen-产品功能介绍.html'
output.write_text(page,encoding='utf-8')
print(f'PRODUCT_PAGE_READY: {len(page.encode("utf-8"))//1024} KiB; self-contained, no network dependencies')
