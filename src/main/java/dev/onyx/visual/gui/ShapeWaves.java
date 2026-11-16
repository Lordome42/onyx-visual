package dev.onyx.visual.gui;


/** Mixed-shape grid with cursor-driven damped wave propagation behind CsClickGui. */
final class ShapeWaves {
    private static final int BACKGROUND=0xF8120F17;
    private static final float STEP=1f/60f, WAVE_SPEED=.42f, FRICTION=.94f, DECAY=.972f;
    private int cols,rows;
    private float cell,gridX,gridY,screenWidth,screenHeight;
    private float[] heights,previous,next,charges,tones,introJitter;
    private float introTime,simulationBacklog,lastMouseX=Float.NaN,lastMouseY=Float.NaN;
    private boolean active;

    void draw(UiCanvas ui,float width,float height,int mx,int my,float dt){
        draw(ui,width,height,mx,my,dt,true,BACKGROUND);
    }

    /** @param animated false = only the flat backdrop (setting "Фон меню" off). */
    void draw(UiCanvas ui,float width,float height,int mx,int my,float dt,boolean animated,int background){
        if(width<=0||height<=0)return;
        if(!animated){ui.rect(0,0,width,height,background);return;}
        configure(width,height);
        dt=Math.max(0,Math.min(.05f,dt));
        introTime=Math.min(1.6f,introTime+dt);
        if(mx>=0&&mx<width&&my>=0&&my<height){
            if(!Float.isFinite(lastMouseX)||Math.hypot(mx-lastMouseX,my-lastMouseY)>.35){
                float distance=Float.isFinite(lastMouseX)?(float)Math.hypot(mx-lastMouseX,my-lastMouseY):0;
                float velocity=distance/Math.max(dt,.008f);
                splash(mx,my,.4f*Math.min(1,.22f+velocity*.0006f));
                lastMouseX=mx;lastMouseY=my;
            }
        }else{lastMouseX=lastMouseY=Float.NaN;}
        simulationBacklog=Math.min(simulationBacklog+dt,STEP*3);
        while(active&&simulationBacklog>=STEP){
            simulationBacklog-=STEP;
            boolean moving=false;
            for(int row=0;row<rows;row++)for(int col=0;col<cols;col++){
                int id=row*cols+col;
                int left=row*cols+Math.max(0,col-1),right=row*cols+Math.min(cols-1,col+1);
                int up=Math.max(0,row-1)*cols+col,down=Math.min(rows-1,row+1)*cols+col;
                float h=heights[id];
                float laplace=heights[left]+heights[right]+heights[up]+heights[down]-4*h;
                float value=(h+(h-previous[id])*FRICTION+WAVE_SPEED*laplace)*DECAY;
                next[id]=Math.abs(value)<.0002f?0:value;
                charges[id]=clamp(next[id],0,1);
                if(Math.abs(next[id])>.01f)moving=true;
            }
            float[] old=previous;previous=heights;heights=next;next=old;
            if(!moving){
                java.util.Arrays.fill(heights,0);java.util.Arrays.fill(previous,0);
                java.util.Arrays.fill(charges,0);active=false;simulationBacklog=0;
            }
        }
        boolean animating=active||introTime<1.6f;
        int maxW=ui.lite()?640:1280;
        int tw=Math.max(32,Math.min(maxW,Math.round(width*ui.pixelScale()*.67f))),th=Math.max(18,Math.round(tw*height/width));
        sinceRaster+=dt;
        boolean fresh=buffer==null||bufW!=tw||bufH!=th||lastBackground!=background;
        if(fresh){buffer=new int[tw*th];bufW=tw;bufH=th;lastBackground=background;}
        if(wasAnimating&&!animating)dirtyOnce=true;
        wasAnimating=animating;
        if(fresh||dirtyOnce||(animating&&sinceRaster>=.033f)){
            dirtyOnce=false;sinceRaster=0;
            java.util.Arrays.fill(buffer,background);
            rasterize(width,height,tw,th);
            changed=true;
        }
        if(ui.blitPixels(buffer,tw,th,changed,0,0,width,height))changed=false;
        else ui.rect(0,0,width,height,background);
    }

    private int[] buffer;
    private int bufW,bufH,lastBackground;
    private boolean changed=true,dirtyOnce=true;
    private float sinceRaster;
    private boolean wasAnimating;

    /** Software-renders the cell grid into a small ARGB buffer; one texture blit replaces thousands of fills. */
    private void rasterize(float width,float height,int tw,int th){
        float introProgress=introTime/1.6f;
        float kx=tw/width,ky=th/height;
        for(int row=0;row<rows;row++)for(int col=0;col<cols;col++){
            int id=row*cols+col;
            float x=gridX+(col+.5f)*cell,y=gridY+(row+.5f)*cell;
            float ux=x/width*2-1,uy=y/height*2-1;
            float edge=(float)Math.pow(Math.pow(Math.abs(ux),2.5)+Math.pow(Math.abs(uy),2.5),1/2.5);
            float fade=1-smoothstep(.45f,1.0f,edge);
            float radial=(float)Math.sqrt(ux*ux+uy*uy)*.62f;
            float reveal=smoothstep(radial+introJitter[id]-.1f,radial+introJitter[id]+.12f,introProgress*1.55f);
            float charge=charges[id];
            float alpha=(.10f+tones[id]*.20f)*fade*reveal+charge*.48f;
            if(alpha<.015f)continue;
            int band=Math.min(2,(int)(tones[id]*3));
            int shape=2-(band+(int)(Math.min(charge,.999f)*3))%3;
            float size=cell*.375f*(1+charge*.22f)*(.55f+.45f*reveal);
            int rgb=mix(0x929292,0xFFFFFF,smoothstep(.15f,.85f,charge));
            int color=((int)(clamp(alpha,0,.88f)*255)<<24)|rgb;
            if(charge>.55f)blob(tw,th,x*kx,y*ky,size*1.65f*kx,size*1.65f*ky,1,((int)(charge*18)<<24)|0xFFFFFF);
            blob(tw,th,x*kx,y*ky,size*kx,size*ky,shape,color);
        }
    }

    /** shape: 0 square, 1 ellipse, 2 triangle (apex up); half sizes rx, ry in texels. */
    private void blob(int tw,int th,float cx,float cy,float rx,float ry,int shape,int color){
        int x0=Math.max(0,(int)Math.floor(cx-rx)),x1=Math.min(tw-1,(int)Math.ceil(cx+rx));
        int y0=Math.max(0,(int)Math.floor(cy-ry)),y1=Math.min(th-1,(int)Math.ceil(cy+ry));
        int sa=color>>>24,sr=(color>>16)&255,sg=(color>>8)&255,sb=color&255;
        for(int j=y0;j<=y1;j++){
            float py=(j+.5f-cy)/ry;
            for(int i=x0;i<=x1;i++){
                float px=(i+.5f-cx)/rx;
                float cover;
                if(shape==0)cover=Math.min(1,Math.min((1-Math.abs(px))*rx+.5f,(1-Math.abs(py))*ry+.5f));
                else if(shape==1)cover=clamp((1-(float)Math.sqrt(px*px+py*py))*Math.min(rx,ry)+.5f,0,1);
                else{
                    float t=(py+1)*.5f; // 0 at the apex, 1 at the base
                    float half=t;       // half-width relative to rx
                    cover=Math.min(1,Math.min((half-Math.abs(px))*rx+.5f,Math.min((1-Math.abs(py))*ry+.5f,1)));
                }
                if(cover<=0)continue;
                int a=Math.round(sa*cover);
                if(a<=0)continue;
                int idx=j*tw+i,d=buffer[idx];
                int da=d>>>24;
                int oa=a+da*(255-a)/255;
                if(oa<=0)continue;
                int dr=(d>>16)&255,dg=(d>>8)&255,db=d&255;
                int wd=da*(255-a)/255;
                int or=(sr*a+dr*wd)/oa,og=(sg*a+dg*wd)/oa,ob=(sb*a+db*wd)/oa;
                buffer[idx]=(oa<<24)|(or<<16)|(og<<8)|ob;
            }
        }
    }

    private void configure(float width,float height){
        if(width==screenWidth&&height==screenHeight)return;
        screenWidth=width;screenHeight=height;
        cell=Math.max(13f,(float)Math.sqrt(width*height/1700f));
        cols=Math.max(1,Math.round(width/cell));cell=width/cols;
        rows=Math.max(1,(int)(height/cell));
        gridX=0;gridY=(height-rows*cell)*.5f;
        int count=cols*rows;
        heights=new float[count];previous=new float[count];next=new float[count];
        charges=new float[count];tones=new float[count];introJitter=new float[count];
        for(int row=0;row<rows;row++)for(int col=0;col<cols;col++){
            int id=row*cols+col;
            float noise=smoothNoise(col/32f,row/32f);
            tones[id]=clamp((noise-.54f)*2.8f+.5f,0,1);
            introJitter[id]=(hash(col,row)-.5f)*.16f;
        }
        introTime=0;simulationBacklog=0;active=false;lastMouseX=lastMouseY=Float.NaN;
    }

    private void splash(float mx,float my,float strength){
        float centerCol=(mx-gridX)/cell-.5f,centerRow=(my-gridY)/cell-.5f;
        float sigma=Math.max(.5f,40/cell*.5f),reach=sigma*2.5f;
        for(int row=Math.max(0,(int)(centerRow-reach));row<=Math.min(rows-1,(int)(centerRow+reach));row++)
            for(int col=Math.max(0,(int)(centerCol-reach));col<=Math.min(cols-1,(int)(centerCol+reach));col++){
                float dx=col-centerCol,dy=row-centerRow;
                float bump=strength*(float)Math.exp(-(dx*dx+dy*dy)/(2*sigma*sigma));
                int id=row*cols+col;
                heights[id]=Math.min(1.2f,heights[id]+bump);
                active=true;
            }
    }
    private static float smoothNoise(float x,float y){
        int ix=(int)Math.floor(x),iy=(int)Math.floor(y);
        float fx=x-ix,fy=y-iy;
        fx=fx*fx*(3-2*fx);fy=fy*fy*(3-2*fy);
        float a=lerp(hash(ix,iy),hash(ix+1,iy),fx);
        float b=lerp(hash(ix,iy+1),hash(ix+1,iy+1),fx);
        return lerp(a,b,fy);
    }
    private static float hash(int x,int y){
        int h=x*0x9E3779B9+y*0x85EBCA6B+0x6D2B79F5;
        h^=h>>>16;h*=0x7FEB352D;h^=h>>>15;
        return (h&0x7FFFFFFF)/(float)Integer.MAX_VALUE;
    }
    private static int mix(int from,int to,float t){
        t=clamp(t,0,1);
        int r=Math.round(((from>>16)&255)*(1-t)+((to>>16)&255)*t);
        int g=Math.round(((from>>8)&255)*(1-t)+((to>>8)&255)*t);
        int b=Math.round((from&255)*(1-t)+(to&255)*t);
        return (r<<16)|(g<<8)|b;
    }
    private static float smoothstep(float a,float b,float x){float v=clamp((x-a)/(b-a),0,1);return v*v*(3-2*v);}
    private static float lerp(float a,float b,float t){return a+(b-a)*t;}
    private static float clamp(float value,float min,float max){return Math.max(min,Math.min(max,value));}
}
