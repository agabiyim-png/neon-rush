package com.lunagames.neonrush;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.*;
import java.util.*;

public class MainActivity extends Activity {
    RushView game;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512);
        game=new RushView(this); setContentView(game);
    }
    @Override protected void onPause(){super.onPause();if(game!=null)game.paused=true;}
    static final class Thing {
        float lane, y, phase; int kind; boolean collected=false;
        Thing(float l,float pos,int k){lane=l;y=pos;kind=k;phase=(float)Math.random()*6.28f;}
    }
    static final class Particle {
        float x,y,vx,vy,life,maxLife,r; int col;
        Particle(float a,float b,int c,Random rand) {
            x=a;y=b;col=c;vx=(rand.nextFloat()-.5f)*600;vy=(rand.nextFloat()-.5f)*550;
            life=maxLife=.35f+rand.nextFloat()*.55f;r=2+rand.nextFloat()*6;
        }
    }
    class RushView extends View {
        final Paint p=new Paint(3);
        final Random random=new Random();
        final ArrayList<Thing> objects=new ArrayList<>();
        final ArrayList<Particle> particles=new ArrayList<>();
        final SharedPreferences prefs;
        final ToneGenerator tones;
        int W,H,score=0,best,coins=0,runCoins=0,mission=0,missionProgress=0,skin=0,unlocked=0,combo=0;
        float time=0,elapsed=0,spawn=0,shipX=0,targetX=0,invincible=0,shield=0,speed=0,shake=0,flash=0,trail=0;
        boolean started=false,over=false,paused=false,dragging=false;
        int cyan=Color.rgb(63,244,255),magenta=Color.rgb(255,54,178),gold=Color.rgb(255,219,83);
        int[] colors={0xff47efff,0xffff4db6,0xffffd653,0xff9a74ff,0xff61ff8f,0xffff815c};
        final String[] missions={"COLLECT 12 ENERGY","SURVIVE 35 SEC","SCORE 1500","COLLECT 20 ENERGY","SURVIVE 60 SEC","SCORE 3500"};
        RushView(Context c){
            super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);
            prefs=getSharedPreferences("rush_save",0);
            best=prefs.getInt("best",0);coins=prefs.getInt("coins",0);mission=prefs.getInt("mission",0);
            skin=prefs.getInt("skin",0);unlocked=prefs.getInt("unlocked",0);
            ToneGenerator t;try{t=new ToneGenerator(AudioManager.STREAM_MUSIC,65);}catch(Exception e){t=null;}tones=t;
        }
        void sound(int t,int dur){if(tones!=null)try{tones.startTone(t,dur);}catch(Exception e){}}
        void buzz(int ms){try{Vibrator v=(Vibrator)getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if(v!=null){if(Build.VERSION.SDK_INT>=26)v.vibrate(VibrationEffect.createOneShot(ms,80));else v.vibrate(ms);}}catch(Exception e){}}
        float laneX(float lane){return W*.5f+lane*(W*.225f);}
        float unit(){return Math.max(1,W/400f);}
        void reset(){
            objects.clear();particles.clear();score=0;runCoins=0;combo=0;time=0;elapsed=0;spawn=0;
            shipX=targetX=laneX(0);shield=0;invincible=1;speed=350*unit();flash=0;shake=0;
            over=false;paused=false;started=true;missionProgress=0;
        }
        void pop(float x,float y,int color,int count){
            for(int i=0;i<count;i++){if(particles.size()<190)particles.add(new Particle(x,y,color,random));}
        }
        void finish(){
            over=true;started=false;paused=false;shake=16*unit();flash=.7f;
            sound(ToneGenerator.TONE_PROP_NACK,350);buzz(90);
            if(score>best)best=score;
            coins+=runCoins;
            prefs.edit().putInt("best",best).putInt("coins",coins).apply();
            pop(shipX,H*.79f,magenta,40);
        }
        void completeMission(){
            mission++;unlocked=Math.min(colors.length-1,unlocked+1);
            coins+=25;missionProgress=0;shield=6;
            flash=.85f;sound(ToneGenerator.TONE_PROP_ACK,210);
            prefs.edit().putInt("mission",mission).putInt("unlocked",unlocked).putInt("coins",coins).apply();
            pop(shipX,H*.77f,gold,35);
        }
        void collect(Thing o){
            o.collected=true;
            int cx=(int)laneX(o.lane),cy=(int)o.y;
            if(o.kind==1){runCoins+=1;combo++;score+=75+Math.min(combo,12)*8;pop(cx,cy,gold,14);sound(ToneGenerator.TONE_PROP_BEEP,65);}
            if(o.kind==2){shield=7;score+=125;pop(cx,cy,cyan,20);sound(ToneGenerator.TONE_PROP_ACK,130);}
            if(o.kind==3){score+=220;combo+=2;invincible=2.2f;pop(cx,cy,magenta,24);sound(ToneGenerator.TONE_PROP_ACK,150);}
            if(mission%3==0&&o.kind==1)missionProgress++;
            if(mission%3==2)missionProgress=score;
            if(missionProgress >= (mission%3==0?(mission==0?12:20): mission%3==1?(mission==1?35:60):(mission==2?1500:3500)))completeMission();
        }
        void spawnRow(){
            int danger=random.nextInt(3),safe=random.nextInt(3);
            int mode=random.nextInt(10);
            if(mode<3&&elapsed>12){
                for(int lane=-1;lane<=1;lane++)if(lane!=safe)objects.add(new Thing(lane,-85*unit(),0));
                objects.add(new Thing(safe,-175*unit(),random.nextInt(10)==0?2:1));
            } else {
                objects.add(new Thing(danger-1,-85*unit(),0));
                int other=(danger+1+random.nextInt(2))%3;
                objects.add(new Thing(other-1,-150*unit(),random.nextInt(13)==0?2:random.nextInt(18)==0?3:1));
            }
        }
        void update(float dt){
            if(!started||over||paused)return;
            elapsed+=dt;time+=dt;spawn-=dt;trail-=dt;
            speed=(360+Math.min(420,elapsed*7))*unit();
            if(invincible>0)invincible-=dt;if(shield>0)shield-=dt;if(shake>0)shake=Math.max(0,shake-dt*45*unit());
            if(flash>0)flash=Math.max(0,flash-dt*2);
            shipX+=(targetX-shipX)*Math.min(1,dt*13);
            score+=(int)Math.max(1,dt*125*(1+elapsed/90f));
            if(mission%3==1)missionProgress=(int)elapsed;
            if(mission%3==2)missionProgress=score;
            if(missionProgress >= (mission%3==1?(mission==1?35:60):mission%3==2?(mission==2?1500:3500):99999))completeMission();
            if(spawn<=0){spawnRow();spawn=Math.max(.39f,.83f-elapsed*.005f);}
            if(trail<=0){pop(shipX,H*.80f,colors[skin],1);trail=.07f;}
            for(int i=objects.size()-1;i>=0;i--){
                Thing o=objects.get(i);o.y+=speed*dt;o.phase+=dt*3;
                if(o.y>H+80*unit()){objects.remove(i);continue;}
                if(!o.collected&&Math.abs(o.y-H*.78f)<34*unit()&&Math.abs(laneX(o.lane)-shipX)<41*unit()){
                    if(o.kind==0){
                        if(shield>0||invincible>0){o.collected=true;pop(laneX(o.lane),o.y,cyan,18);score+=50;shield=0;invincible=1;shake=5*unit();}
                        else {finish();break;}
                    }else collect(o);
                }
            }
        }
        void text(Canvas c,String s,float x,float y,float size,int color,boolean bold,Paint.Align align){
            p.reset();p.setAntiAlias(true);p.setColor(color);p.setTypeface(bold?Typeface.create("sans-serif-black",Typeface.BOLD):Typeface.create("sans-serif",Typeface.NORMAL));
            p.setTextSize(size*unit());p.setTextAlign(align);c.drawText(s,x,y,p);
        }
        void line(Canvas c,float x1,float y1,float x2,float y2,int color,float sw){
            p.reset();p.setAntiAlias(true);p.setColor(color);p.setStrokeWidth(sw*unit());c.drawLine(x1,y1,x2,y2,p);
        }
        void circle(Canvas c,float x,float y,float radius,int color){
            p.reset();p.setAntiAlias(true);p.setColor(color);c.drawCircle(x,y,radius,p);
        }
        void glow(Canvas c,float x,float y,float radius,int color){
            p.reset();p.setAntiAlias(true);p.setColor(color);p.setShadowLayer(18*unit(),0,0,color);c.drawCircle(x,y,radius,p);p.clearShadowLayer();
        }
        void polygon(Canvas c,float[] pts,int color){
            Path path=new Path();path.moveTo(pts[0],pts[1]);for(int i=2;i<pts.length;i+=2)path.lineTo(pts[i],pts[i+1]);path.close();
            p.reset();p.setAntiAlias(true);p.setColor(color);c.drawPath(path,p);
        }
        void environment(Canvas c){
            c.drawColor(0xff05091b);
            p.reset();p.setShader(new LinearGradient(0,0,0,H,0xff111239,0xff05091b,Shader.TileMode.CLAMP));c.drawRect(0,0,W,H,p);p.setShader(null);
            float horiz=H*.28f;
            for(int i=0;i<25;i++){
                float sx=(float)(i*137%1000)/1000f*W;
                float sy=(float)((i*97)%700)/700f*H;
                circle(c,sx,sy,1.1f*unit(),0xff55608e);
            }
            for(int i=-4;i<=4;i++){
                float top=W*.5f+i*W*.055f,bottom=W*.5f+i*W*.225f;
                line(c,top,horiz,bottom,H,0xff203b67,1.25f);
            }
            for(int i=0;i<20;i++){
                float t=(i/20f+time*.38f)%1;
                float y=horiz+(H-horiz)*t*t;
                line(c,W*.5f-(W*.16f+W*.68f*t),y,W*.5f+(W*.16f+W*.68f*t),y,0xff223965,1.4f);
            }
            line(c,laneX(-1.5f),H*.29f,laneX(-1.5f),H,0xff00b9ff,2);
            line(c,laneX(1.5f),H*.29f,laneX(1.5f),H,0xffe43cff,2);
            for(int i=0;i<2;i++){
                float x=W*(.3875f+i*.225f);
                for(int y=0;y<H;y+=90*unit()){
                    float yy=(y+time*240*unit())%H;
                    line(c,x,yy,x,Math.min(H,yy+34*unit()),0x7754a7eb,2);
                }
            }
        }
        void ship(Canvas c){
            float x=shipX,y=H*.79f,u=unit();
            if(invincible>0&&((int)(time*12)%3==0))return;
            glow(c,x,y+22*u,16*u,0x9947efff);
            polygon(c,new float[]{x,y-37*u,x-26*u,y+27*u,x,y+16*u,x+26*u,y+27*u},colors[skin]);
            polygon(c,new float[]{x,y-23*u,x-10*u,y+17*u,x+10*u,y+17*u},0xff09122d);
            polygon(c,new float[]{x-14*u,y+21*u,x-9*u,y+48*u,x,y+20*u},0xffd76aff);
            polygon(c,new float[]{x+14*u,y+21*u,x+9*u,y+48*u,x,y+20*u},0xff19f5fa);
            if(shield>0){p.reset();p.setAntiAlias(true);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3*u);p.setColor(cyan);p.setShadowLayer(12*u,0,0,cyan);c.drawCircle(x,y,43*u,p);p.clearShadowLayer();p.setStyle(Paint.Style.FILL);}
        }
        void objects(Canvas c){
            float u=unit();
            for(Thing o:objects){
                if(o.collected)continue;
                float x=laneX(o.lane),y=o.y;
                if(o.kind==0){
                    p.reset();p.setColor(0xfffa246a);p.setShadowLayer(11*u,0,0,0xfffb1e66);
                    c.drawRoundRect(x-33*u,y-27*u,x+33*u,y+27*u,9*u,9*u,p);p.clearShadowLayer();
                    line(c,x-21*u,y-16*u,x+21*u,y+16*u,0xffffabc8,4);
                    line(c,x+21*u,y-16*u,x-21*u,y+16*u,0xffffabc8,4);
                }else{
                    int color=o.kind==1?gold:o.kind==2?cyan:magenta;
                    float pulse=1+(float)Math.sin(o.phase)*.15f;
                    glow(c,x,y,14*u*pulse,color);
                    if(o.kind==1){text(c,"✦",x,y+8*u,25,0xff27160e,true,Paint.Align.CENTER);}
                    if(o.kind==2){text(c,"S",x,y+8*u,23,0xff071a2a,true,Paint.Align.CENTER);}
                    if(o.kind==3){text(c,"★",x,y+7*u,21,0xff1a0623,true,Paint.Align.CENTER);}
                }
            }
        }
        void particles(Canvas c,float dt){
            for(int i=particles.size()-1;i>=0;i--){
                Particle a=particles.get(i);a.life-=dt;
                if(a.life<=0){particles.remove(i);continue;}
                a.x+=a.vx*dt;a.y+=a.vy*dt;a.vy+=150*dt;
                p.reset();p.setAntiAlias(true);p.setColor(a.col);p.setAlpha((int)(255*a.life/a.maxLife));
                c.drawCircle(a.x,a.y,a.r*unit(),p);
            }
        }
        void hud(Canvas c){
            float u=unit();
            text(c,"NEON",24*u,52*u,24,cyan,true,Paint.Align.LEFT);
            text(c,"RUSH",97*u,52*u,24,magenta,true,Paint.Align.LEFT);
            text(c,String.valueOf(score),W/2f,103*u,39,Color.WHITE,true,Paint.Align.CENTER);
            text(c,"BEST "+best,24*u,83*u,13,0xff97a8d8,true,Paint.Align.LEFT);
            text(c,"✦ "+(coins+runCoins),W-24*u,83*u,16,gold,true,Paint.Align.RIGHT);
            text(c,paused?"▶":"Ⅱ",W-29*u,49*u,28,Color.WHITE,true,Paint.Align.CENTER);
            if(combo>2)text(c,"COMBO x"+Math.min(combo,99),W/2f,145*u,17,magenta,true,Paint.Align.CENTER);
            if(shield>0)text(c,"SHIELD "+(int)Math.ceil(shield)+"s",W/2f,179*u,15,cyan,true,Paint.Align.CENTER);
            String objective=missions[Math.min(mission,missions.length-1)];
            text(c,"MISSION "+(mission+1)+" · "+objective,W/2f,H-53*u,12,0xffb2c4f5,true,Paint.Align.CENTER);
            text(c,"REWARD +25 ✦  ·  NEW SHIP COLOR",W/2f,H-34*u,10,gold,true,Paint.Align.CENTER);
        }
        void overlay(Canvas c,String heading,String sub,String button){
            p.reset();p.setColor(0xdd05091c);c.drawRect(0,0,W,H,p);
            float u=unit(),y=H*.32f;
            text(c,heading,W/2f,y,39,cyan,true,Paint.Align.CENTER);
            text(c,sub,W/2f,y+40*u,14,Color.WHITE,false,Paint.Align.CENTER);
            if(over){
                text(c,"SCORE  "+score,W/2f,y+95*u,27,Color.WHITE,true,Paint.Align.CENTER);
                text(c,"BEST  "+best+"     ✦ "+runCoins,W/2f,y+127*u,17,gold,true,Paint.Align.CENTER);
            }else if(!started){
                text(c,"DODGE  •  COLLECT  •  SURVIVE",W/2f,y+100*u,14,0xffc0c9ee,true,Paint.Align.CENTER);
                text(c,"SWIPE OR DRAG TO STEER",W/2f,y+128*u,14,0xffc0c9ee,false,Paint.Align.CENTER);
            }
            float by=y+178*u;
            p.reset();p.setColor(cyan);p.setShadowLayer(16*u,0,0,cyan);
            c.drawRoundRect(W*.15f,by,W*.85f,by+59*u,25*u,25*u,p);p.clearShadowLayer();
            text(c,button,W/2f,by+39*u,22,0xff08102c,true,Paint.Align.CENTER);
            text(c,"SHIP COLOR  "+(skin+1)+"/"+(unlocked+1)+"   ·   TAP BOTTOM TO CHANGE",W/2f,H-95*u,11,0xffa9badb,true,Paint.Align.CENTER);
        }
        long last=0;
        @Override protected void onDraw(Canvas c){
            super.onDraw(c); W=getWidth();H=getHeight();if(W<10||H<10)return;
            long now=System.nanoTime();float dt=last==0?0:Math.min(.04f,(now-last)/1_000_000_000f);last=now;
            if(!paused&&started&&!over)update(dt);
            c.save();if(shake>0)c.translate((random.nextFloat()-.5f)*shake,(random.nextFloat()-.5f)*shake);
            environment(c);objects(c);ship(c);particles(c,dt);c.restore();hud(c);
            if(flash>0){p.reset();p.setColor(Color.argb((int)(flash*75),255,255,255));c.drawRect(0,0,W,H,p);}
            if(paused)overlay(c,"PAUSED","TAKE A BREATH","RESUME");
            else if(over)overlay(c,"GAME OVER","ONE MORE RUN?","PLAY AGAIN");
            else if(!started)overlay(c,"NEON RUSH","ENDLESS ESCAPE","PLAY NOW");
            postInvalidateDelayed(16);
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent e){
            float x=e.getX(),y=e.getY();int action=e.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                dragging=true;
                if(y>H*.86f&&(!started||over||paused)){skin=(skin+1)%(unlocked+1);prefs.edit().putInt("skin",skin).apply();invalidate();return true;}
                if(paused){paused=false;return true;}
                if(over||!started){reset();return true;}
                if(y<H*.14f&&x>W*.78f){paused=true;return true;}
                targetX=Math.max(laneX(-1),Math.min(laneX(1),x));return true;
            }
            if(action==MotionEvent.ACTION_MOVE&&dragging&&started&&!paused){targetX=Math.max(laneX(-1),Math.min(laneX(1),x));return true;}
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)dragging=false;
            return true;
        }
    }
}
