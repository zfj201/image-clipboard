#!/usr/bin/env python3
"""Deterministic raster preview of the Android vector icon. Requires Pillow."""
from PIL import Image, ImageDraw
from pathlib import Path
root=Path(__file__).resolve().parents[1]
scale=16
im=Image.new('RGBA',(108*scale,108*scale),(0,0,0,0)); d=ImageDraw.Draw(im)
def box(bounds,color,r=0):
    xy=tuple(v*scale for v in bounds)
    if r:d.rounded_rectangle(xy,r*scale,fill=color)
    else:d.rectangle(xy,fill=color)
def polygon(points,color):d.polygon([(x*scale,y*scale) for x,y in points],fill=color)
box((4,4,104,104),'#2864E8',20)
box((29,26,73,87),'white',7)
box((39,21,63,33),'#BFD5FF',4);box((39,27,63,33),'#BFD5FF')
box((38,44,64,71),'#2864E8')
polygon([(40,66),(48,55),(55,63),(59,58),(64,66)],'#83E5D3')
d.ellipse((54*scale,48*scale,60*scale,54*scale),fill='white')
d.ellipse((57*scale,53*scale,87*scale,83*scale),fill='#83E5D3')
polygon([(63,66),(75,66),(71,62),(74,59),(83,68),(74,77),(71,74),(75,70),(63,70)],'#163763')
im.resize((512,512),Image.Resampling.LANCZOS).save(root/'artwork/icon.png')
