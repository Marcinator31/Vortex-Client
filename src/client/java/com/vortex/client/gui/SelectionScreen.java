package com.vortex.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Shared modern selection UI for mob, entity and block selectors. */
public abstract class SelectionScreen extends Screen {
    protected static final class Entry {
        final ItemStack icon;
        final String id;
        final String name;
        final String search;
        Entry(Item icon, String id, String name) {
            this.icon = new ItemStack(icon);
            this.id = id;
            this.name = name;
            this.search = (name + " " + id).toLowerCase(Locale.ROOT);
        }
    }

    private static final int WIN_MAX_W = 620;
    private static final int WIN_MAX_H = 400;
    private static final int HEADER_H = 46;
    private static final int FOOTER_H = 20;
    private static final int CELL_H = 30;
    private static final int PAD = 10;
    private static final int C_DIM = 0xD2060409;
    private static final int C_WINDOW = 0xFC0E0B16;
    private static final int C_BAR = 0xFF0A0812;
    private static final int C_CARD = 0xFF15111F;
    private static final int C_HOVER = 0xFF1E1930;
    private static final int C_INNER = 0xFF120E1B;
    private static final int C_LINE = 0xFF241E36;
    private static final int A_PURPLE = 0xFF8B5CF6;
    private static final int A_BLUE = 0xFF3B82F6;

    private final Screen parent;
    private final String title;
    protected final List<Entry> entries = new ArrayList<>();
    private final Map<String, Float> hover = new HashMap<>();
    private final Map<String, Float> selected = new HashMap<>();
    private EditBox search;
    private float openAnim;
    private float scroll, scrollTarget;
    private int contentHeight;
    private long lastNano;
    private int mx, my;
    private final List<Hit> hits = new ArrayList<>();
    private Hit clearButton;

    private static final class Hit {
        final int x, y, w, h; final String id;
        Hit(int x, int y, int w, int h, String id) {
            this.x=x; this.y=y; this.w=w; this.h=h; this.id=id;
        }
        boolean contains(double px, double py) {
            return px >= x && px < x+w && py >= y && py < y+h;
        }
    }

    protected SelectionScreen(Screen parent, String title) {
        super(Component.literal(title));
        this.parent = parent;
        this.title = title;
    }

    protected abstract void buildEntries();
    protected abstract boolean isOn(String id);
    protected abstract void toggle(String id);
    protected abstract void clearAll();
    protected abstract String hint();

    @Override
    protected void init() {
        if (entries.isEmpty()) {
            buildEntries();
            entries.sort((a,b) -> a.name.compareToIgnoreCase(b.name));
        }
        int w = Math.min(this.width - 20, WIN_MAX_W);
        int h = windowHeight();
        int x = (this.width-w)/2, y = (this.height-h)/2;
        search = new EditBox(this.font, x+w-166, y+25, 150, 14, Component.literal(""));
        search.setBordered(false);
        search.setMaxLength(48);
        search.setResponder(text -> { scroll=0f; scrollTarget=0f; });
        addRenderableWidget(search);
    }

    private int windowHeight() { return Math.min(this.height-20, WIN_MAX_H); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        mx=mouseX; my=mouseY; hits.clear(); clearButton=null;
        long now=System.nanoTime();
        float dt=lastNano==0 ? .016f : Math.min(.1f,(now-lastNano)/1_000_000_000f);
        lastNano=now;
        openAnim=anim(openAnim,1f,14f,dt);
        scroll=anim(scroll,scrollTarget,18f,dt);
        int w=Math.min(this.width-20,WIN_MAX_W), h=windowHeight();
        int x=(this.width-w)/2, y=(this.height-h)/2+(int)((1f-openAnim)*12f);
        int accent=Theme.INSTANCE.accent.get()|0xFF000000;

        ctx.fill(0,0,width,height,fade(C_DIM,openAnim));
        roundRect(ctx,x,y,w,h,fade(C_WINDOW,openAnim));
        gradient(ctx,x,y,x+w,y+2,fade(accent,openAnim),fade(A_BLUE,openAnim*.3f));
        drawHeader(ctx,x,y,w,accent);
        drawGrid(ctx,x,y+HEADER_H,w,h-HEADER_H-FOOTER_H,accent,dt);
        ctx.fill(x,y+h-FOOTER_H,x+w,y+h,fade(C_BAR,openAnim));
        ctx.fill(x,y+h-FOOTER_H,x+w,y+h-FOOTER_H+1,fade(C_LINE,openAnim));
        ctx.text(font,Component.literal("Click to toggle  ·  Type to search  ·  ESC to go back"),
                x+PAD,y+h-FOOTER_H+6,fade(0xFF74747F,openAnim),false);
        if(search!=null){ search.setX(x+w-166); search.setY(y+25); }
        super.extractRenderState(ctx,mouseX,mouseY,delta);
    }

    private void drawHeader(GuiGraphicsExtractor ctx,int x,int y,int w,int accent) {
        gradient(ctx,x,y,x+w,y+HEADER_H,fade(C_BAR,openAnim),fade(0xFF101022,openAnim));
        ctx.fill(x,y+HEADER_H-1,x+w,y+HEADER_H,fade(accent,openAnim*.45f));
        boolean back=in(x+PAD,y+8,16,16);
        ctx.text(font,Component.literal("<"),x+PAD+4,y+12,fade(back?accent:0xFF9A9AA6,openAnim));
        hits.add(new Hit(x+PAD,y+8,16,16,"\0back"));
        ctx.text(font,Component.literal(title),x+PAD+22,y+11,fade(Theme.INSTANCE.text.get(),openAnim));
        int on=countSelected();
        ctx.text(font,Component.literal(on+" selected  ·  "+hint()),x+PAD+22,y+28,
                fade(Theme.INSTANCE.textDim.get(),openAnim),false);
        if(on>0){
            String label="Clear selection"; int bw=font.width(label)+14;
            int bx=x+w-bw-PAD; boolean hov=in(bx,y+7,bw,15);
            roundRect(ctx,bx,y+7,bw,15,fade(hov?mix(C_INNER,accent,.4f):C_INNER,openAnim));
            ctx.text(font,Component.literal(label),bx+7,y+10,fade(Theme.INSTANCE.text.get(),openAnim),false);
            clearButton=new Hit(bx,y+7,bw,15,"\0clear");
        }
        if(search!=null){
            int sx=search.getX()-8, sy=y+22, sw=search.getWidth()+14;
            roundRect(ctx,sx,sy,sw,20,fade(C_INNER,openAnim));
            ctx.text(font,Component.literal("Q"),sx+6,sy+6,fade(0xFF6A6A76,openAnim),false);
            if(search.getValue().isEmpty()) ctx.text(font,Component.literal("Search..."),
                    sx+18,sy+6,fade(0xFF6A6A76,openAnim),false);
        }
    }

    private void drawGrid(GuiGraphicsExtractor ctx,int x,int y,int w,int h,int accent,float dt) {
        ctx.enableScissor(x,y,x+w,y+h);
        List<Entry> list=filtered(); int inner=w-PAD*2-6;
        int cols=Math.max(1,inner/190), cellW=(inner-(cols-1)*6)/cols;
        for(int i=0;i<list.size();i++){
            Entry e=list.get(i); int cx=x+PAD+(i%cols)*(cellW+6);
            int cy=y+PAD+(i/cols)*(CELL_H+6)-(int)scroll;
            if(cy+CELL_H<y||cy>y+h) continue;
            boolean on=isOn(e.id), hov=in(cx,cy,cellW,CELL_H);
            float hv=anim(hover.getOrDefault(e.id,0f),hov?1f:0f,14f,dt);
            float sv=anim(selected.getOrDefault(e.id,on?1f:0f),on?1f:0f,14f,dt);
            hover.put(e.id,hv); selected.put(e.id,sv);
            int bg=mix(mix(C_CARD,C_HOVER,hv),mix(C_CARD,accent,.35f),sv);
            roundRect(ctx,cx,cy,cellW,CELL_H,bg);
            if(sv>.01f) gradient(ctx,cx,cy,cx+3,cy+CELL_H,fade(A_PURPLE,sv),fade(A_BLUE,sv));
            try { ctx.item(e.icon,cx+8,cy+7); } catch(Throwable ignored) { }
            String name=e.name; int max=cellW-34;
            while(name.length()>1&&font.width(name+"..")>max) name=name.substring(0,name.length()-1);
            if(!name.equals(e.name)) name+="..";
            ctx.text(font,Component.literal(name),cx+30,cy+11,
                    on?Theme.INSTANCE.text.get():Theme.INSTANCE.textDim.get(),false);
            hits.add(new Hit(cx,cy,cellW,CELL_H,e.id));
        }
        contentHeight=((list.size()+cols-1)/cols)*(CELL_H+6)+PAD*2;
        ctx.disableScissor();
        if(contentHeight>h){
            int track=h-8, bar=Math.max(24,(int)(track*(h/(float)contentHeight)));
            float p=Math.max(0f,Math.min(1f,scroll/Math.max(1f,contentHeight-h)));
            int by=y+4+(int)((track-bar)*p);
            ctx.fill(x+w-6,y+4,x+w-4,y+4+track,0x30FFFFFF);
            roundRect(ctx,x+w-7,by,4,bar,fade(mix(accent,0xFFFFFFFF,.15f),openAnim));
        }
        if(list.isEmpty()) ctx.text(font,Component.literal("No results"),
                x+(w-font.width("No results"))/2,y+h/2-4,fade(0xFF6A6A76,openAnim),false);
    }

    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click,boolean doubled){
        if(super.mouseClicked(click,doubled)) return true;
        if(clearButton!=null&&clearButton.contains(mx,my)){clearAll();selected.clear();return true;}
        for(int i=hits.size()-1;i>=0;i--){Hit hit=hits.get(i);if(!hit.contains(mx,my))continue;
            if("\0back".equals(hit.id)) onClose(); else toggle(hit.id); return true;}
        return false;
    }
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double horizontal,double vertical){
        int h=windowHeight()-HEADER_H-FOOTER_H; scrollTarget-=(float)vertical*36f;
        float max=Math.max(0f,contentHeight-h); scrollTarget=Math.max(0f,Math.min(max,scrollTarget)); return true;
    }
    private List<Entry> filtered(){
        String q=search==null?"":search.getValue().trim().toLowerCase(Locale.ROOT);
        if(q.isEmpty()) return entries; List<Entry> out=new ArrayList<>();
        for(Entry e:entries) if(e.search.contains(q)) out.add(e); return out;
    }
    private int countSelected(){int n=0;for(Entry e:entries)if(isOn(e.id))n++;return n;}
    private boolean in(int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    private static float anim(float a,float b,float speed,float dt){return a+(b-a)*(1f-(float)Math.exp(-speed*dt));}
    private void roundRect(GuiGraphicsExtractor c,int x,int y,int w,int h,int color){
        if(w<=0||h<=0)return; int r=Math.min(3,Math.min(w/2,h/2));
        c.fill(x+r,y,x+w-r,y+h,color); c.fill(x,y+r,x+r,y+h-r,color); c.fill(x+w-r,y+r,x+w,y+h-r,color);
    }
    private static void gradient(GuiGraphicsExtractor c,int x,int y,int x2,int y2,int a,int b){
        if(x2<=x){c.fill(x,y,x+3,y2,mix(a,b,.5f));return;}
        int n=Math.max(1,(x2-x+7)/8); for(int i=0;i<n;i++){int ax=x+(x2-x)*i/n,bx=x+(x2-x)*(i+1)/n;c.fill(ax,y,bx,y2,mix(a,b,(i+.5f)/n));}
    }
    private static int fade(int c,float f){if(f>=1)return c;if(f<=0)return c&0x00FFFFFF;return((int)(((c>>>24)&255)*f)<<24)|(c&0x00FFFFFF);}
    private static int mix(int a,int b,float t){t=Math.max(0,Math.min(1,t));int aa=(a>>>24)&255,ar=(a>>16)&255,ag=(a>>8)&255,ab=a&255;int ba=(b>>>24)&255,br=(b>>16)&255,bg=(b>>8)&255,bb=b&255;return((int)(aa+(ba-aa)*t)<<24)|((int)(ar+(br-ar)*t)<<16)|((int)(ag+(bg-ag)*t)<<8)|(int)(ab+(bb-ab)*t);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
