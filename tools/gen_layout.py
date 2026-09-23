#!/usr/bin/env python3
"""Generate app-views/src/main/res/layout/activity_main.xml from core/src/main/assets/manifest.json.

Every sprite = an ImageView at its Figma box (dp in the 360x800 frame). Stacking order = DOM order of
prototype-a7 v1.3.3 (App.tsx). `android:tag` = the element id the shared timeline animates.
Run again whenever the Figma export (manifest + sprites) changes.
"""
import json, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
M = json.load(open(os.path.join(ROOT, 'core/src/main/assets/manifest.json')))
OUT = os.path.join(ROOT, 'app-views/src/main/res/layout/activity_main.xml')

LX, LY = 180, 298
TILES = ['tile_hiphop', 'tile_rock', 'tile_country', 'tile_holiday', 'tile_alarm', 'tile_rnb', 'tile_sfx', 'tile_baby', 'tile_msg']
MINIS = [('tile_hiphop', 'Hip-Hop', '#ED8002', '#590023'), ('tile_rock', 'Rock', '#E94E75', '#46004E'),
         ('tile_holiday', 'Holiday', '#3FAB53', '#003932'), ('tile_country', 'Country', '#C99A2C', '#571400')]
MINI_WALLS = [m[0] for m in MINIS]
BGS = ['bg_G01', 'bg_G02a', 'bg_G02b', 'bg_G02c', 'bg_G03', 'bg_G04']
HEADS = ['g01_head', 'g02a_head', 'g02b_head', 'g03_head']


def n(v):
    return ('%.2f' % v).rstrip('0').rstrip('.')


def box(x, y, w, h):
    return f'android:layout_width="{n(w)}dp" android:layout_height="{n(h)}dp" android:layout_marginLeft="{n(x)}dp" android:layout_marginTop="{n(y)}dp"'


def sprite(k, ind):
    x, y, w, h = M[k]['pos']
    return f'{ind}<ImageView android:tag="{k}" {box(x, y, w, h)} android:src="@drawable/{k.lower()}" android:scaleType="fitXY" android:importantForAccessibility="no" />'


def ring(tag, d, cx, cy, stroke, alpha, ind):
    return f'{ind}<namvunhatle.r15.onboarding.views.RingView android:tag="{tag}" {box(cx - d / 2, cy - d / 2, d, d)} app:ringStroke="{n(stroke)}dp" app:ringAlpha="{alpha}" />'


def full(tag, ind, inner, cls='FrameLayout'):
    return f'{ind}<{cls} android:tag="{tag}" android:layout_width="360dp" android:layout_height="800dp">\n{inner}\n{ind}</{cls}>'


def center(k):
    x, y, w, h = M[k]['pos']
    return x + w / 2, y + h / 2


def wall_center(k):
    x, y, w, h = M[k]['bbox']
    return x + w / 2, y + h / 2


I2, I3, I4 = ' ' * 8, ' ' * 12, ' ' * 16
cam = []
cam.append(sprite('splash_bg', I3))
cam += [sprite(k, I3) for k in BGS]
# logo: rings + record, one group the camera zooms through
g = 232
logo = [ring(t, d, g / 2, g / 2, s, a, I4) for t, d, s, a in
        [('emit0', 136, 1.5, .45), ('emit1', 136, 1.5, .45), ('sring0', 136, 1, .18), ('sring1', 184, 1, .18), ('sring2', 232, 1, .18)]]
logo.append(f'{I4}<namvunhatle.r15.onboarding.views.BakedView android:tag="icon" {box(g / 2 - 48, g / 2 - 48, 96, 96)} />')
cam.append(f'{I3}<FrameLayout android:tag="logo" {box(LX - g / 2, LY - g / 2, g, g)}>\n' + '\n'.join(logo) + f'\n{I3}</FrameLayout>')
cam.append(f'{I3}<namvunhatle.r15.onboarding.views.BakedView android:tag="logoB" {box(LX - g / 2, LY - g / 2, g, g)} />')
cam.append(sprite('tagline', I3))
cam.append(f'{I3}<namvunhatle.r15.onboarding.views.CssBox android:tag="sp_track" {box(24, 644, 312, 8)} app:fill="#0F010101" app:cornerRadius="4dp">\n'
           f'{I4}<namvunhatle.r15.onboarding.views.CssBox android:tag="sp_fill" android:layout_width="match_parent" android:layout_height="match_parent" app:fill="#BB4ABF" app:cornerRadius="4dp" />\n{I3}</namvunhatle.r15.onboarding.views.CssBox>')
cam.append(sprite('sp_note', I3))
cam.append(sprite('sp_strip', I3))
cam += [ring(t, d, LX, LY, s, a, I3) for t, d, s, a in [('bring0', 300, 2, .55), ('bring1', 520, 2, .30), ('bring2', 800, 1.5, .14)]]
cam.append(f'{I3}<View android:tag="flash" {box(LX - 150, LY - 150, 300, 300)} android:background="@drawable/flash" />')
tiles = [sprite(k, I4) for k in TILES if k not in MINI_WALLS]
for k, label, c0, c1 in MINIS:
    cx, cy = wall_center(k)
    tiles.append(
        f'{I4}<namvunhatle.r15.onboarding.views.CssBox android:tag="{k}" {box(cx - 84, cy - 60, 168, 120)} app:fill="{c0}" app:fill2="{c1}" app:fillAngle="118" app:fillStop0="0.128" app:fillStop1="0.872" app:cornerRadius="16dp">\n'
        f'{I4}    <TextView style="@style/T.W600" android:layout_width="wrap_content" android:layout_height="40dp" android:layout_marginLeft="16dp" android:layout_marginTop="64dp" android:textSize="32dp" android:letterSpacing="-0.045" android:textColor="#F4FFFFFF" android:text="{label}" />\n'
        f'{I4}</namvunhatle.r15.onboarding.views.CssBox>')
cam.append(full('tiles', I3, '\n'.join(tiles)))
cam += [ring(t, d, 180, 318, 1.5, .28, I3) for t, d in [('g3ring0', 224), ('g3ring1', 300), ('g3ring2', 380)]]
cam += [sprite(k, I3) for k in ['g01_phone', 'g02_phone', 'g03_phone', 'st_mia', 'st_leo', 'st_zoe', 'st_noah']]
cam.append(f'{I3}<include layout="@layout/lyric_card" />')
cam.append(f'{I3}<include layout="@layout/bubble" />')
cam += [sprite(k, I3) for k in HEADS]
cam.append(f'{I3}<FrameLayout android:tag="wave" {box(16, 62, 328, 28)}>\n'
           f'{I4}<namvunhatle.r15.onboarding.views.WaveView android:id="@+id/wave_bars" android:layout_width="282dp" android:layout_height="28dp" />\n'
           f'{I4}<TextView android:tag="wave_tc" style="@style/T.W400" android:layout_width="44dp" android:layout_height="20dp" android:layout_marginLeft="284dp" android:layout_marginTop="4dp" '
           f'android:gravity="end|center_vertical" android:ellipsize="none" android:textSize="14dp" android:textColor="#CCFFFFFF" android:fontFeatureSettings="tnum" android:text="-0:11" />\n{I3}</FrameLayout>')
cam.append(f'{I3}<View android:tag="divider" {box(16, 463, 328, 1)} android:background="#14FFFFFF" />')
cam.append(f'{I3}<include android:id="@+id/nat1" layout="@layout/native_ad" />')
cam.append(f'{I3}<include android:id="@+id/nat2" layout="@layout/native_ad" />')
cam += [sprite(k, I3) for k in ['g04_sam', 'g04_emma', 'g04_jake', 'g04_mia', 'g04_leo', 'g04_zoe', 'g04_center', 'g04_cta', 'g04_secondary']]
cam.append(f'{I3}<View android:id="@+id/hot_cta" {box(15, 343, 330, 58)} android:contentDescription="Explore AI Ringtones" />')
cam.append(f'{I3}<View android:id="@+id/hot_secondary" {box(16, 407, 328, 48)} android:contentDescription="Browse ringtones" />')
cam += [f'{I3}<namvunhatle.r15.onboarding.views.BakedView android:tag="{t}" {box(0, 0, 16, 16)} />' for t in ['ghost0', 'ghost1', 'ghost2', 'dot']]

dests = []
for d in ['paywall', 'ai', 'home']:
    hot = ''
    if d == 'paywall':
        hot = (f'\n{I4}<View android:id="@+id/hot_paywall_close" {box(300, 28, 52, 52)} android:contentDescription="Close paywall" />'
               f'\n{I4}<View android:id="@+id/hot_subscribe" {box(20, 638, 320, 52)} android:contentDescription="Subscribe" />')
    dests.append(full(f'dest_{d}', I2, f'{I4}<ImageView android:layout_width="match_parent" android:layout_height="match_parent" android:src="@drawable/dest_{d}" android:scaleType="fitXY" android:importantForAccessibility="no" />{hot}'))

xml = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- GENERATED by tools/gen_layout.py from the Figma export (manifest.json). Edit the script, not this file.
     A7 onboarding, XML Views build: one 360×800 design frame (dp), scaled to fit by DesignFrame.
     android:tag = element id animated by the shared timeline in :core. -->
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#050507">

    <namvunhatle.r15.onboarding.views.DesignFrame
        android:id="@+id/frame"
        android:layout_width="360dp"
        android:layout_height="800dp">

{full('cam', I2, chr(10).join(cam))}

        <ImageView android:tag="statusbar" {box(0, 0, 360, 40)} android:src="@drawable/statusbar" android:scaleType="fitXY" android:importantForAccessibility="no" />
        <View {box(167.5, 7.5, 25, 25)} android:background="@drawable/punch" />

        <!-- Interstitial — third-party, R15 does not control it -->
        <FrameLayout android:tag="bridge" android:layout_width="360dp" android:layout_height="800dp">
            <ImageView android:layout_width="match_parent" android:layout_height="match_parent" android:src="@drawable/bridge_full" android:scaleType="fitXY" android:importantForAccessibility="no" />
            <View android:id="@+id/hot_skip" {box(248, 12, 110, 44)} android:contentDescription="Skip ads" />
        </FrameLayout>

        <!-- Destinations (static screenshots + hotspots) -->
{chr(10).join(dests)}
    </namvunhatle.r15.onboarding.views.DesignFrame>

    <ImageButton
        android:id="@+id/fab"
        android:layout_width="44dp"
        android:layout_height="44dp"
        android:layout_gravity="bottom|end"
        android:layout_margin="16dp"
        android:background="@drawable/fab"
        android:src="@drawable/ic_replay"
        android:contentDescription="Replay"
        android:visibility="gone" />
</FrameLayout>
'''
open(OUT, 'w').write(xml)
print('wrote', OUT, len(xml.splitlines()), 'lines')
