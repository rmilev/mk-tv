"""Regenerates the launcher art (run: python3 tools/make_icons.py <outdir>). Draws the МК ТВ mark: a 4:3 CRT TV whose screen shows the Macedonian flag."""
import math, sys
from PIL import Image, ImageDraw, ImageFont

RED=(210,0,0); GOLD=(255,210,0); BG=(11,13,18); BEZEL=(58,64,76); BEZEL_HI=(92,100,116); DARK=(24,27,34)
SS=4

def flag(w,h):
    im=Image.new('RGB',(w,h),RED); d=ImageDraw.Draw(im)
    cx,cy=w/2,h/2; R=math.hypot(w,h)
    r0=h*0.12; w0=h*0.02; w1=h*0.30
    for k in range(8):
        a=math.radians(k*45-90); c,s=math.cos(a),math.sin(a)
        pt=lambda dist,off:(cx+dist*c-off*s, cy+dist*s+off*c)
        d.polygon([pt(r0,-w0),pt(R,-w1*R/h*0.55),pt(R,w1*R/h*0.55),pt(r0,w0)],fill=GOLD)
    rc=h*0.16; d.ellipse([cx-rc,cy-rc,cx+rc,cy+rc],fill=GOLD)
    return im

def tv(height, antenna=True, feet=True):
    """RGBA image of the TV, `height` px tall overall."""
    H=height*SS
    ant=int(H*0.22) if antenna else 0
    ft=int(H*0.07) if feet else 0
    body_h=H-ant-ft; body_w=int(body_h*1.30)
    W=body_w+int(H*0.02)
    im=Image.new('RGBA',(W,H),(0,0,0,0)); d=ImageDraw.Draw(im)
    x0=(W-body_w)//2; y0=ant
    lw=max(SS,int(body_h*0.035))
    if antenna:
        cx=W/2; base=(cx,y0+lw)
        for dx in (-1,1):
            tip=(cx+dx*body_w*0.24, lw*1.2)
            d.line([base,tip],fill=BEZEL_HI,width=lw)
            r=lw*1.1; d.ellipse([tip[0]-r,tip[1]-r,tip[0]+r,tip[1]+r],fill=BEZEL_HI)
    if feet:
        for fx in (0.22,0.78):
            fxx=x0+body_w*fx; d.rounded_rectangle([fxx-body_w*0.07,y0+body_h-lw,fxx+body_w*0.07,H-1],radius=lw,fill=BEZEL)
    rad=int(body_h*0.16)
    d.rounded_rectangle([x0,y0,x0+body_w,y0+body_h],radius=rad,fill=BEZEL)
    d.rounded_rectangle([x0+lw//2,y0+lw//2,x0+body_w-lw//2,y0+body_h-lw//2],radius=rad,outline=BEZEL_HI,width=max(1,lw//3))
    # 4:3 screen with CRT-round corners
    pad=int(body_h*0.09)
    sh=body_h-2*pad; sw=int(sh*4/3)
    sx=x0+(body_w-sw)//2; sy=y0+pad
    mask=Image.new('L',(sw,sh),0); ImageDraw.Draw(mask).rounded_rectangle([0,0,sw-1,sh-1],radius=int(sh*0.18),fill=255)
    im.paste(flag(sw,sh),(sx,sy),mask)
    # glass highlight
    hl=Image.new('RGBA',(sw,sh),(0,0,0,0)); hd=ImageDraw.Draw(hl)
    hd.ellipse([-sw*0.3,-sh*0.9,sw*0.9,sh*0.35],fill=(255,255,255,34))
    hm=Image.new('L',(sw,sh),0); ImageDraw.Draw(hm).rounded_rectangle([0,0,sw-1,sh-1],radius=int(sh*0.18),fill=255)
    glass=Image.new('RGBA',(sw,sh),(0,0,0,0)); glass.paste(hl,(0,0),hm)
    im.alpha_composite(glass,(sx,sy))
    return im.resize((W//SS,H//SS),Image.LANCZOS)

def font(px):
    for f in ['/System/Library/Fonts/Supplemental/Arial Bold.ttf','/Library/Fonts/Arial Bold.ttf','/System/Library/Fonts/Helvetica.ttc']:
        try: return ImageFont.truetype(f,px)
        except Exception: pass
    return ImageFont.load_default()

out=sys.argv[1]
# TV banner 320x180dp @xhdpi = 640x360
b=Image.new('RGBA',(640,360),BG+(255,))
t=tv(250); b.alpha_composite(t,(46,(360-250)//2+4))
d=ImageDraw.Draw(b); f=font(92)
d.text((46+t.width+34,180),"МК ТВ",font=f,fill=(242,244,248),anchor='lm')
b.convert('RGB').save(f'{out}/banner.png')
# adaptive foreground 108dp @xxxhdpi = 432px, art inside the 66dp safe zone
fg=Image.new('RGBA',(432,432),(0,0,0,0)); t=tv(250); fg.alpha_composite(t,((432-t.width)//2,(432-t.height)//2)); fg.save(f'{out}/fg.png')
prev=Image.new('RGBA',(432,432),BG+(255,)); prev.alpha_composite(fg)
m=Image.new('L',(432,432),0); ImageDraw.Draw(m).ellipse([60,60,372,372],fill=255)
c=Image.new('RGBA',(432,432),(255,255,255,255)); c.paste(prev,(0,0),m); c.save(f'{out}/fg_preview.png')
