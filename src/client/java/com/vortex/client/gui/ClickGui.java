package com.vortex.client.gui;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.KeySetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.core.setting.Setting;
import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ClickGui extends Screen {
    private static final int WIN_MAX_W = 960;
    private static final int WIN_MAX_H = 520;
    private static final int HEADER_H = 46;
    private static final int FOOTER_H = 24;
    private static final int TAB_H = 0;
    private static final int SIDEBAR_W = 176;
    private static final int PAD = 14;
    private static final int RADIUS = 3;
    private static final int C_DIM = 0xD2060409;
    private static final int C_WINDOW = 0xFC0E0B16;
    private static final int C_SIDEBAR = 0xFF0A0812;
    private static final int C_CARD = 0xFF15111F;
    private static final int C_INNER = 0xFF120E1B;
    private static final int C_LINE = 0xFF241E36;
    private static final int C_TEXT = 0xFFF2F0F8;
    private static final int A_VIOLETT = 0xFF8B5CF6;
    private static final int A_BLAU = 0xFF3B82F6;

    private final Set<Module> expanded = new HashSet<>();
    private final Map<Module, Float> hoverAnim = new HashMap<>();
    private final Map<Module, Float> expandAnim = new HashMap<>();
    private final Map<Module, Float> toggleAnim = new HashMap<>();
    private enum Section { MODULE, WAYPOINTS, MACROS, COMMUNITY, KEYS, SKINS, DESIGN }
    private Section section = Section.MODULE;
    private Module.Category selected = ersteBelegteKategorie();
    private float openAnim;
    private EditBox search;
    private long lastNano;
    private int mx, my, lastWinX, lastWinY, lastWinW;
    private Module hoverModule, frameHover;
    private float hoverTime;
    private final List<Hit> hits = new ArrayList<>();
    private enum Act { THEME, PRESET, CATEGORY, FAVCAT, STAR, SUB_WAYPOINT, SUB_SCREEN, SECTION, WP_SETTING, WP_MANAGE, TOGGLE, EXPAND, S_BOOL, S_NUM, S_MODE_PREV, S_MODE_NEXT, S_COLOR, S_KEY }
    private static final class Hit {
        final int x, y, w, h; final Act act; final Module module; final Setting setting; final Object extra;
        Hit(int x, int y, int w, int h, Act act, Module module, Setting setting, Object extra) {
            this.x=x; this.y=y; this.w=w; this.h=h; this.act=act; this.module=module; this.setting=setting; this.extra=extra;
        }
        boolean contains(double px, double py) { return px >= x && px < x+w && py >= y && py < y+h; }
    }

    public ClickGui() { super(Component.literal("Vortex Client")); }

    @Override protected void init() {
        int w = windowWidth(), x = (width-w)/2, y = (height-windowHeight())/2;
        search = new EditBox(font, x+w-134, y+10, 120, 14, Component.literal(""));
        search.setBordered(false); search.setMaxLength(32); addRenderableWidget(search);
    }

    private int windowHeight() { int avail=height-20; return Math.min(avail, Math.max(180, Math.min(avail, GuiState.getWindowH()>0?GuiState.getWindowH():Math.min(height-40, WIN_MAX_H)))); }
    private int windowWidth() { int avail=width-20; return Math.min(avail, Math.max(360, Math.min(avail, GuiState.getWindowW()>0?GuiState.getWindowW():Math.min(width-40, WIN_MAX_W)))); }
    private float opacity() { try { return (float)Theme.INSTANCE.opacity.get(); } catch (Throwable ignored) { return 1f; } }

    @Override public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        mx=mouseX; my=mouseY; hits.clear(); frameHover=null;
        long now=System.nanoTime(); float dt=lastNano==0?0.016f:Math.min(0.1f,(now-lastNano)/1_000_000_000f); lastNano=now;
        openAnim=anim(openAnim,1f,14f,dt); pvpclient$captureKeyIfListening();
        ctx.fill(0,0,width,height,fade(C_DIM,openAnim));
        int ww=windowWidth(), wh=windowHeight(), wx=(width-ww)/2+GuiState.getOffsetX(), wy=(height-wh)/2+GuiState.getOffsetY();
        wx=Math.max(0,Math.min(width-ww,wx)); wy=Math.max(0,Math.min(height-wh,wy)); lastWinX=wx;lastWinY=wy;lastWinW=ww;
        int accent=Theme.INSTANCE.accent.get()|0xFF000000; schatten(ctx,wx,wy,wx+ww,wy+wh,openAnim); roundRect(ctx,wx,wy,ww,wh,fade(C_WINDOW,openAnim*opacity()));
        drawHeader(ctx,wx,wy,ww,accent,Theme.INSTANCE); drawSeitenleiste(ctx,wx,wy+HEADER_H,wh-HEADER_H-FOOTER_H,accent,Theme.INSTANCE,dt); drawContent(ctx,wx+SIDEBAR_W,wy+HEADER_H,ww-SIDEBAR_W,wh-HEADER_H-FOOTER_H,accent,Theme.INSTANCE,dt); drawFooter(ctx,wx,wy+wh-FOOTER_H,ww);
        if (search!=null) { search.setX(wx+ww-search.getWidth()-PAD); search.setY(wy+10); } super.extractRenderState(ctx,mouseX,mouseY,delta);
    }

    private void drawHeader(GuiGraphicsExtractor ctx,int x,int y,int w,int accent,Theme t) {
        verlauf(ctx,x,y,x+w,y+HEADER_H,fade(mix(C_SIDEBAR,A_VIOLETT,.14f),openAnim),fade(mix(C_SIDEBAR,A_BLAU,.07f),openAnim));
        ctx.text(font,Component.literal("Vortex Client"),x+PAD+4,y+10,fade(C_TEXT,openAnim),false);
        if(search!=null) { int sx=search.getX()-16, sy=y+7, sw=search.getWidth()+20; roundRect(ctx,sx,sy,sw,20,fade(C_INNER,openAnim)); ctx.text(font,Component.literal("Search..."),sx+18,sy+6,fade(0xFF6A6A76,openAnim),false); }
    }
    private void drawSeitenleiste(GuiGraphicsExtractor ctx,int x,int y,int h,int accent,Theme t,float dt) { ctx.fill(x,y,x+SIDEBAR_W,y+h,fade(C_SIDEBAR,openAnim)); }
    private void drawContent(GuiGraphicsExtractor ctx,int x,int y,int w,int h,int accent,Theme t,float dt) { ctx.fill(x,y,x+w,y+h,fade(C_WINDOW,openAnim)); }
    private void drawFooter(GuiGraphicsExtractor ctx,int x,int y,int w) { ctx.fill(x,y,x+w,y+FOOTER_H,fade(C_SIDEBAR,openAnim)); }
    private void drawTooltip(GuiGraphicsExtractor ctx,String text,int accent) { }

    private static int fade(int c,float f) { if(f>=1)return c; if(f<=0)return c&0x00FFFFFF; return (((int)(((c>>>24)&255)*f))<<24)|(c&0x00FFFFFF); }
    private static int mix(int a,int b,float t) { t=Math.max(0,Math.min(1,t)); int aa=(a>>>24)&255,ar=(a>>>16)&255,ag=(a>>>8)&255,ab=a&255, ba=(b>>>24)&255,br=(b>>>16)&255,bg=(b>>>8)&255,bb=b&255; return ((int)(aa+(ba-aa)*t)<<24)|((int)(ar+(br-ar)*t)<<16)|((int)(ag+(bg-ag)*t)<<8)|(int)(ab+(bb-ab)*t); }
    private static int akzent(float t) { return mix(A_VIOLETT,A_BLAU,t); }
    private static float anim(float cur,float target,float speed,float dt) { float f=1-(float)Math.exp(-speed*dt); return cur+(target-cur)*f; }
    private boolean inRect(int px,int py,int x,int y,int w,int h) { return px>=x&&px<x+w&&py>=y&&py<y+h; }
    private static void schatten(GuiGraphicsExtractor c,int x,int y,int x2,int y2,float s) { int a=Math.max(0,Math.min(255,(int)(36*s))); c.fill(x-2,y-2,x2+2,y2+2,a<<24); }
    private static void verlauf(GuiGraphicsExtractor c,int x,int y,int x2,int y2,int a,int b) { if(x2>x)c.fill(x,y,x2,y2,mix(a,b,.5f)); }
    private static void roundRect(GuiGraphicsExtractor c,int x,int y,int w,int h,int color) { if(w>0&&h>0)c.fill(x,y,x+w,y+h,color); }
    private static Module.Category ersteBelegteKategorie() { for(Module.Category c:Module.Category.values()) for(Module m:ModuleManager.INSTANCE.getModules()) if(m.getCategory()==c)return c; return Module.Category.values()[0]; }
    private KeySetting pvpclient$listeningSetting() { for(Module m:ModuleManager.INSTANCE.getModules()) for(Setting s:m.getSettings()) if(s instanceof KeySetting k&&k.isListening())return k; return null; }
    private void pvpclient$captureKeyIfListening() { }
}
