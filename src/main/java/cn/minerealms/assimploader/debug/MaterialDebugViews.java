package cn.minerealms.assimploader.debug;

import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.config.AssimpLoaderConfig.MaterialView;
import cn.minerealms.assimploader.material.LabPbrPacker;
import cn.minerealms.assimploader.model.AssimpMaterial;

/** Bounded, pure conversion of packed LabPBR channels for display as ordinary albedo. */
public final class MaterialDebugViews {
    private MaterialDebugViews() {}
    public static MaterialView current() {
        var config=AssimpLoaderConfig.CLIENT.debugView;
        return AssimpLoaderConfig.getOrDefault(config.enabled::get,false)
                ? AssimpLoaderConfig.getOrDefault(config.material::get,MaterialView.DEFAULT) : MaterialView.DEFAULT;
    }
    public static boolean active(){return current()!=MaterialView.DEFAULT;}
    public static float[] tint(AssimpMaterial material) {
        return active() || material==null ? new float[]{1,1,1,material==null?1:material.getRenderColor()[3]} : material.getRenderColor();
    }
    public static LabPbrPacker.Image image(MaterialView view, LabPbrPacker.Image albedo, LabPbrPacker.PackedTextures maps) {
        var source=switch(view) {
            case ALBEDO, DEFAULT -> albedo;
            case NORMAL, AO -> maps==null?null:maps.normal();
            case ROUGHNESS, METALLIC, EMISSION -> maps==null?null:maps.specular();
            case UNTEXTURED -> null;
        };
        int width=source==null?1:Math.min(1024,source.width());
        int height=source==null?1:Math.min(1024,source.height());
        int[] pixels=new int[width*height];
        for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
            int sample=source==null?0:source.pixel((int)((long)x*source.width()/width),(int)((long)y*source.height()/height));
            int r=sample&255,g=sample>>>8&255,b=sample>>>16&255;
            int value=255;
            switch(view) {
                case ALBEDO, DEFAULT -> {pixels[y*width+x]=source==null?0xffffffff:sample;continue;}
                case NORMAL -> {
                    double nx=source==null?0:r/127.5-1,ny=source==null?0:-(g/127.5-1);
                    int z=(int)Math.round((Math.sqrt(Math.max(0,1-nx*nx-ny*ny))+1)*127.5);
                    pixels[y*width+x]=0xff000000 | z<<16 | (int)Math.round((ny+1)*127.5)<<8 | (int)Math.round((nx+1)*127.5);
                    continue;
                }
                case AO -> value=source==null?255:b;
                case ROUGHNESS -> value=source==null?255:255-r;
                case METALLIC -> value=source!=null && g>=230?255:0;
                case EMISSION -> value=source==null?0:sample>>>24&255;
                case UNTEXTURED -> {}
            }
            pixels[y*width+x]=0xff000000 | value<<16 | value<<8 | value;
        }
        return new LabPbrPacker.Image(width,height,pixels);
    }
}
